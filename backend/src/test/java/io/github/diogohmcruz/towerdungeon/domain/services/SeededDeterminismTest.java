package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * A seed makes a whole Campaign repeatable: the same seed and the same commands give the same Event
 * Log and the same state in any launch, and the Tower does not care how many Units were raised.
 */
class SeededDeterminismTest {
  private static final String PLAYER = "player";

  /**
   * Runs the replay in a fresh JVM, so that nothing a launch decides can be shared. {@code
   * hashCodeMode} picks how that JVM makes identity hash codes (HotSpot's experimental {@code
   * -XX:hashCode}): the default makes them in a repeatable order, which would let a hashed map of
   * enums look stable between two launches, so the launches below each use another way.
   */
  private static String hashInNewJvm(long seed, int hashCodeMode)
      throws IOException, InterruptedException {
    var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var classpath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    var process =
        new ProcessBuilder(
                java,
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:hashCode=" + hashCodeMode,
                "-cp",
                classpath,
                DeterministicReplay.class.getName(),
                Long.toString(seed))
            .redirectErrorStream(true)
            .start();
    var output = new String(process.getInputStream().readAllBytes());
    assertThat(process.waitFor(120, TimeUnit.SECONDS)).as(output).isTrue();
    assertThat(process.exitValue()).as(output).isZero();
    return output
        .lines()
        .filter(line -> line.startsWith("HASH="))
        .findFirst()
        .map(line -> line.substring("HASH=".length()))
        .orElseThrow(() -> new AssertionError("No hash in the output: " + output));
  }

  @Test
  void theSameSeedAndScriptGiveTheSameEventLogHashInTwoSeparateJvmLaunches() throws Exception {
    var first = hashInNewJvm(7, 0);
    var second = hashInNewJvm(7, 3);
    var third = hashInNewJvm(7, 4);

    assertThat(first).isEqualTo(second).isEqualTo(third).hasSize(64);
    assertThat(first).isEqualTo(DeterministicReplay.run(7));
  }

  @Test
  void aDifferentSeedGivesADifferentHash() throws Exception {
    assertThat(hashInNewJvm(8, 0)).isNotEqualTo(hashInNewJvm(7, 0));
  }

  private GameService service(long seed) {
    var config = new GameProperties();
    var store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    var service =
        new GameService(
            config,
            new ObjectMapper(),
            SeedSource.fixed(seed),
            ArchitecturePlan.generate(
                config.getArchitecture().getSeed(),
                config.getTower().getMaxFloor(),
                config.getBoss().getInterval(),
                config.getEnemies().getSurgeInterval()),
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            mock(AutosaveCoordinator.class));
    service.attach(PLAYER, "ws").block();
    return service;
  }

  private static List<String> garrison(GameService service) {
    var floor = service.playerState(PLAYER).getTower().getCurrentTowerFloor();
    return floor.getEnemies().stream().map(SeededDeterminismTest::describe).toList();
  }

  private static String describe(Enemy enemy) {
    return enemy.getId()
        + " "
        + enemy.getName()
        + " "
        + enemy.getStats()
        + " "
        + enemy.getParts()
        + " "
        + enemy.getMaxHealth();
  }

  /** Sets out with whoever is in Reserves and takes the first lifecycle tick, onto Floor 1. */
  private static List<String> floorOneGarrison(GameService service) {
    service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0));
    service.gameLoop();
    assertThat(service.playerState(PLAYER).getTower().getCurrentFloor()).isEqualTo(1);
    return garrison(service);
  }

  @Test
  void recruitingAnExtraUnitLeavesTheNextFloorsGarrisonUnchanged() {
    var plain = service(7);
    var recruited = service(7);
    recruited.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 2));

    var expected = floorOneGarrison(plain);
    var actual = floorOneGarrison(recruited);

    assertThat(actual).isNotEmpty().isEqualTo(expected);
    // The extra Warriors really did go up with the party and draw from the other two streams.
    assertThat(recruited.playerState(PLAYER).getTowerPartySize())
        .isGreaterThan(plain.playerState(PLAYER).getTowerPartySize());
  }

  @Test
  void differentSeedsBuildDifferentGarrisons() {
    var garrisons =
        java.util.stream.LongStream.range(1, 6).mapToObj(seed -> floorOneGarrison(service(seed)));

    assertThat(garrisons.distinct().count()).isGreaterThan(1);
  }

  @Test
  void aSandboxCampaignTakesItsSeedAndAHumanOneDrawsItFromTheServerSecret() {
    // Only newSandboxCampaign takes a seed; no command carries one. Two human Campaigns on one
    // server do not share a seed.
    var human = new SeedSource("a server secret");
    var sandbox = service(7).newSandboxCampaign(42);

    assertThat(sandbox.toSnapshot().random().seed()).isEqualTo(42);

    var first = human.forHumanCampaign().snapshot().seed();
    var second = human.forHumanCampaign().snapshot().seed();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void aSandboxOpenedOnTheSameSeedReplaysTheSameRun() {
    var config = new GameProperties();
    config.getAgent().getSandbox().setEnabled(true);
    var store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    var service =
        new GameService(
            config,
            new ObjectMapper(),
            SeedSource.fixed(1),
            mock(ArchitecturePlan.class),
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            mock(AutosaveCoordinator.class));
    var registry = new SandboxRegistry(service, config);

    var first = registry.open("grant", null, null, 99L);
    var second = registry.open("grant", null, null, 99L);
    var fresh = registry.open("grant", null, null);

    assertThat(first.seed()).isEqualTo(99);
    assertThat(second.seed()).isEqualTo(99);
    assertThat(fresh.seed()).isNotNull();
    var a = service.playerState(first.sandboxId());
    var b = service.playerState(second.sandboxId());
    assertThat(a.toSnapshot()).isEqualTo(b.toSnapshot());
  }
}

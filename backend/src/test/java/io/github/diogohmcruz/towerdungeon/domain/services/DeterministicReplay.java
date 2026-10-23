package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.items.LeftoverGenerator;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.observation.EventLog;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plays one scripted Campaign from a seed and reduces what happened to a hash. Run in a JVM of its
 * own by {@code SeededDeterminismTest}: a hash that survives two launches cannot depend on anything
 * a launch decides, such as the identity hash codes that order a hashed map of enums.
 */
public final class DeterministicReplay {
  private static final String PLAYER = "player";
  private static final int TICKS = 600;

  private DeterministicReplay() {}

  /** Prints the hash for the seed given as the only argument. */
  public static void main(String[] args) throws Exception {
    System.out.println("HASH=" + run(Long.parseLong(args[0])));
  }

  /**
   * The same seed and the same script always give the same hash: it covers the whole Event Log and
   * the saved state, which holds every Unit and Garrison name and id and where each random stream
   * stands.
   */
  public static String run(long seed) throws Exception {
    var config = new GameProperties();
    var store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    var mapper =
        JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    var service =
        new GameService(
            config,
            mapper,
            SeedSource.fixed(seed),
            plan,
            new PartyCombat(),
            store,
            new FloorBundle(config, new LeftoverGenerator(config)),
            mock(AutosaveCoordinator.class));
    service.attach(PLAYER, "ws").block();

    script(service);

    var state = service.playerState(PLAYER);
    var digest = MessageDigest.getInstance("SHA-256");
    for (var event : service.eventsSince(PLAYER, 0).orElseThrow()) {
      digest.update(describe(event).getBytes(StandardCharsets.UTF_8));
    }
    digest.update(mapper.writeValueAsBytes(state.toSnapshot()));
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void script(GameService service) {
    service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 2));
    service.execute(PLAYER, new Command.Recruit(UnitStats.ARCHER, 1));
    service.execute(PLAYER, new Command.SellFood());
    for (var tick = 0; tick < TICKS; tick++) {
      var state = service.playerState(PLAYER);
      if (!state.isExpeditionActive()) {
        service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 3));
        service.execute(PLAYER, new Command.Recruit(UnitStats.ARCHER, 1));
        service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0));
      } else if (tick % 25 == 0) {
        service.execute(PLAYER, new Command.SendReinforcements(Map.of()));
      }
      service.gameLoop();
      service.invasionTimer();
    }
  }

  /** One event as text. The data is printed as the log holds it: in key order, in every launch. */
  private static String describe(EventLog.Event event) {
    return event.id() + "|" + event.stateSeq() + "|" + event.type() + "|" + event.data() + "\n";
  }
}

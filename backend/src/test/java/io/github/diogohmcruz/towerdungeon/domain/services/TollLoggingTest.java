package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** An Expedition's Toll is written to the log as one parsable line when it ends. */
class TollLoggingTest {
  private static final String PLAYER = "player";

  private final GameProperties config = new GameProperties();
  private final SplittableRandom random = new SplittableRandom(5);
  private final ObjectMapper mapper = new ObjectMapper();
  private final Logger logger = (Logger) LoggerFactory.getLogger(GameService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Level before;
  private GameService service;
  private GameState state;
  private Tower tower;

  @BeforeEach
  void setUp() {
    before = logger.getLevel();
    logger.setLevel(Level.INFO);
    appender.start();
    logger.addAppender(appender);
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    var store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    service =
        new GameService(
            config,
            mapper,
            SeedSource.fixed(9),
            plan,
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            mock(AutosaveCoordinator.class));
    service.attach(PLAYER, "ws").block();
    state = service.playerState(PLAYER);
    tower =
        new Tower(
            100, config.getBoss().copy(), config.getEnemies().copy(), state.getRandom(), plan);
    state.setTower(tower);
    tower.startNewRun();
    state.setExpeditionActive(true);
    state.startRun();
  }

  @AfterEach
  void release() {
    logger.detachAppender(appender);
    logger.setLevel(before);
  }

  private void addParty() {
    var unit = state.recruit(UnitStats.WARRIOR, random);
    state.getUnitsOnTower().computeIfAbsent(UnitStats.WARRIOR, _ -> new ArrayList<>()).add(unit);
  }

  private List<JsonNode> tollLines() {
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(message -> message.startsWith("{\"event\":\"EXPEDITION_TOLL\""))
        .map(mapper::readTree)
        .toList();
  }

  private void assertOneLine(String outcome) {
    var lines = tollLines();
    assertThat(lines).hasSize(1);
    var line = lines.getFirst();
    assertThat(line.get("outcome").asString()).isEqualTo(outcome);
    for (var field :
        List.of(
            "foodMarks",
            "materialsMarks",
            "creditsMarks",
            "relicsMarks",
            "expeditionsMarks",
            "ascentMarks",
            "guardianMarks",
            "marks",
            "toll",
            "legacy")) {
      assertThat(line.has(field)).as(field).isTrue();
    }
    assertThat(line.get("expeditionsMarks").asDouble()).isPositive();
    assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.INFO)).isNotEmpty();
  }

  @Test
  void anExtractionWritesOneTollLine() {
    addParty();
    service.execute(PLAYER, new Command.Extract());
    assertOneLine("EXTRACTED");
  }

  @Test
  void aWipeWritesOneTollLine() {
    // No party on the Tower: the next lifecycle tick ends the run as a Wipe.
    service.gameLoop();
    assertOneLine("WIPED");
  }

  @Test
  void aVictoryWritesOneTollLine() {
    addParty();
    while (tower.getCurrentFloor() < tower.getMaxFloor()) {
      tower.moveToNextFloor();
    }
    tower.getCurrentTowerFloor().getEnemies().clear();
    service.gameLoop();
    assertOneLine("VICTORY");
  }

  @Test
  void theFlagTurnsTheLineOff() {
    // Each Campaign owns a copy of the configuration, so the flag is turned off on that copy.
    state.getConfig().getPrestige().setLogToll(false);
    addParty();
    service.execute(PLAYER, new Command.Extract());
    assertThat(tollLines()).isEmpty();
  }

  @Test
  void loggingDoesNotChangeTheGame() {
    addParty();
    service.execute(PLAYER, new Command.Extract());
    var withLog = state.getExpeditionsCompleted();
    assertThat(withLog).isEqualTo(1);
    assertThat(state.isExpeditionActive()).isFalse();
  }
}

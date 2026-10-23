package io.github.diogohmcruz.towerdungeon.domain.services;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.commands.Notice;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema.SchemaNewerException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Rotation;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import tools.jackson.databind.json.JsonMapper;

class PlayerSlotsTest {
  private static final String PLAYER = "0123456789abcdef0123456789abcdef";

  @TempDir Path dir;

  private final GameProperties config = new GameProperties();
  private final JsonMapper mapper = JsonMapper.builder().build();
  private FileCampaignStore store;
  private GameService service;

  @BeforeEach
  void setUp() {
    config.getPersistence().setDataDir(dir.toString());
    store = new FileCampaignStore(config.getPersistence());
    service =
        new GameService(
            config,
            mapper,
            new SplittableRandom(7),
            mock(ArchitecturePlan.class),
            new PartyCombat(new SplittableRandom(7)),
            store,
            mock(FloorBundle.class),
            new AutosaveCoordinator(
                store, mapper, config.getPersistence(), "test", System::nanoTime));
  }

  private void saveCampaign(int schema, byte[] payload) {
    store.save(PLAYER, 0, Rotation.KEEP, schema, "test", payload).block();
  }

  @Test
  void aNewKeyStartsAFreshCampaignWithTheFounders() {
    service.attach(PLAYER, "ws-1").block();

    var state = service.playerState(PLAYER);
    assertThat(state.getUnits().values().stream().mapToInt(List::size).sum()).isEqualTo(4);
    assertThat(service.renderState(PLAYER).orElseThrow()).contains("\"resumed\":false");
  }

  @Test
  void aReturningKeyResumesItsSavedCampaign() throws Exception {
    var saved = service.newCampaign();
    saved.setCredit(321.0);
    saveCampaign(CampaignSchema.CURRENT, mapper.writeValueAsBytes(saved.toSnapshot()));

    service.attach(PLAYER, "ws-1").block();

    assertThat(service.playerState(PLAYER).getCredit()).isEqualTo(321.0);
    assertThat(service.renderState(PLAYER).orElseThrow())
        .contains("\"resumed\":true")
        .contains("\"savedAt\":");
  }

  @Test
  void aRecordFromANewerSchemaIsRefusedAndLeftUntouched() {
    saveCampaign(CampaignSchema.CURRENT + 1, "{}".getBytes(UTF_8));

    assertThatThrownBy(() -> service.attach(PLAYER, "ws-1").block())
        .isInstanceOf(SchemaNewerException.class);

    assertThat(service.hasSlot(PLAYER)).isFalse();
    assertThat(store.load(PLAYER).block().payload()).isEqualTo("{}".getBytes(UTF_8));
  }

  @Test
  void accessNeverCreatesASlot() {
    var sold = service.execute(PLAYER, new Command.SellFood());
    var invaded = service.execute(PLAYER, new Command.StartExpedition(java.util.Map.of(), 0));
    assertThat(sold.reason()).isEqualTo(Reason.NO_CAMPAIGN);
    assertThat(invaded.reason()).isEqualTo(Reason.NO_CAMPAIGN);
    service.gameLoop();
    service.invasionTimer();

    assertThat(service.renderState(PLAYER)).isEmpty();
    assertThat(service.hasSlot(PLAYER)).isFalse();
  }

  @Test
  void aDetachedSlotKeepsItsStateButDoesNotTick() {
    service.attach(PLAYER, "ws-1").block();
    var state = service.playerState(PLAYER);
    var foodBefore = state.getVillage().getFood();
    service.detach(PLAYER, "ws-1");

    service.gameLoop();

    assertThat(service.hasSlot(PLAYER)).isTrue();
    assertThat(service.isConnected(PLAYER)).isFalse();
    assertThat(state.getVillage().getFood()).isEqualTo(foodBefore);
  }

  @Test
  void aDetachedSlotIsEvictedOnlyAfterTheLinger() {
    config.getPersistence().setLinger(Duration.ofSeconds(30));
    service.attach(PLAYER, "ws-1").block();
    service.detach(PLAYER, "ws-1");

    service.evictLingering(System.nanoTime() + Duration.ofSeconds(29).toNanos());
    assertThat(service.hasSlot(PLAYER)).isTrue();

    service.evictLingering(System.nanoTime() + Duration.ofSeconds(31).toNanos());
    assertThat(service.hasSlot(PLAYER)).isFalse();
  }

  @Test
  void anAttachedSlotIsNeverEvicted() {
    service.attach(PLAYER, "ws-1").block();

    service.evictLingering(System.nanoTime() + Duration.ofHours(1).toNanos());

    assertThat(service.hasSlot(PLAYER)).isTrue();
  }

  @Test
  void reattachingWithinTheLingerResumesTheSameLiveState() {
    service.attach(PLAYER, "ws-1").block();
    var state = service.playerState(PLAYER);
    service.detach(PLAYER, "ws-1");

    service.attach(PLAYER, "ws-2").block();
    service.evictLingering(System.nanoTime() + Duration.ofHours(1).toNanos());

    assertThat(service.playerState(PLAYER)).isSameAs(state);
    assertThat(service.isConnected(PLAYER)).isTrue();
  }

  @Test
  void theNewestConnectionWinsAndTheOldOneIsToldItWasReplaced() {
    var first = service.attach(PLAYER, "ws-1").block();
    var replaced = first.replaced().toFuture();
    assertThat(replaced).isNotDone();

    service.attach(PLAYER, "ws-2").block();

    assertThat(replaced).isDone();
  }

  @Test
  void theReplacedConnectionDetachingDoesNotDetachTheNewOne() {
    service.attach(PLAYER, "ws-1").block();
    service.attach(PLAYER, "ws-2").block();

    service.detach(PLAYER, "ws-1");

    assertThat(service.isConnected(PLAYER)).isTrue();
    service.detach(PLAYER, "ws-2");
    assertThat(service.isConnected(PLAYER)).isFalse();
  }

  @Test
  void aRefusedInvasionLeavesTheWholePartyUntouched() {
    service.attach(PLAYER, "ws-1").block();
    var state = service.playerState(PLAYER);
    var warriorsBefore = List.copyOf(state.getUnits().get(UnitStats.WARRIOR));
    var archersBefore = List.copyOf(state.getUnits().get(UnitStats.ARCHER));

    // The warriors are listed first and are available; the archers are not (only one exists).
    var request = new java.util.LinkedHashMap<UnitStats, Integer>();
    request.put(UnitStats.WARRIOR, 2);
    request.put(UnitStats.ARCHER, 5);

    var result = service.execute(PLAYER, new Command.StartExpedition(request, 0));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.INSUFFICIENT_UNITS);
    assertThat(result.notices()).extracting(Notice::code).containsExactly("INVALID_INVASION");

    assertThat(state.getUnits().get(UnitStats.WARRIOR)).containsExactlyElementsOf(warriorsBefore);
    assertThat(state.getUnits().get(UnitStats.ARCHER)).containsExactlyElementsOf(archersBefore);
    assertThat(state.hasUnitsOnTower()).isFalse();
    assertThat(state.isExpeditionActive()).isFalse();
  }
}

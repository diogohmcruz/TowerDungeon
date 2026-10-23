package io.github.diogohmcruz.towerdungeon.domain.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemStat;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import tools.jackson.databind.json.JsonMapper;

/** The projection must stay small however big the roster is, and must not leak hidden facts. */
class StateProjectionTest {
  /** Characters of JSON for a brief view of a busy mid-game Campaign. Roughly 600 tokens. */
  private static final int BRIEF_BUDGET_CHARS = 2_400;

  private final GameProperties config = new GameProperties();
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final ItemRating rating = new ItemRating(config.getItems().getWeights());

  /** A Campaign with {@code perType} units of every unlocked type, fully kitted and haunted. */
  private GameState campaign(int perType, boolean expedition) {
    var random = new SplittableRandom(11);
    var state = new GameState(config);
    state.setCredit(1234.5);
    for (var type : new UnitStats[] {UnitStats.WARRIOR, UnitStats.ARCHER, UnitStats.PORTER}) {
      for (var i = 0; i < perType; i++) {
        var unit = state.recruit(type, random);
        for (var enemy : EnemyStats.values()) {
          unit.addPhobia(enemy);
        }
        unit.getPhobias().getFirst().reveal();
        unit.equip(
            new Item(
                "sword" + i,
                "A sword",
                ItemTier.LEFTOVER,
                ItemCategory.WEAPON,
                Map.of(ItemStat.POWER, 1.0),
                null),
            rating);
        state.addUnits(type, java.util.List.of(unit));
      }
    }
    if (expedition) {
      var tower =
          new Tower(
              config.getTower().getMaxFloor(),
              config.getBoss().copy(),
              config.getEnemies().copy(),
              random,
              ArchitecturePlan.generate(1L, 100, 10, 5));
      state.setTower(tower);
      state.setUnitsOnTower(new HashMap<>(state.getUnits()));
      state.setUnits(new HashMap<>());
      tower.startNewRunAt(0);
      state.startRun();
    }
    state.setCarriedCredits(55.5);
    return state;
  }

  private String json(GameState state) throws Exception {
    return mapper.writeValueAsString(StateProjection.brief(state, 42));
  }

  @Test
  void aBusyCampaignFitsTheBudget() throws Exception {
    assertThat(json(campaign(200, true)).length()).isLessThan(BRIEF_BUDGET_CHARS);
    assertThat(json(campaign(200, false)).length()).isLessThan(BRIEF_BUDGET_CHARS);
  }

  @Test
  void theSizeDoesNotGrowWithTheNumberOfUnits() throws Exception {
    var small = json(campaign(3, true)).length();
    var large = json(campaign(300, true)).length();

    // Only the digits of counts and sums may differ.
    assertThat(large - small).isLessThan(120);
  }

  @Test
  void itIsFarSmallerThanTheFullFrame() throws Exception {
    var state = campaign(100, true);

    assertThat(json(state).length() * 20).isLessThan(mapper.writeValueAsString(state).length());
  }

  @Test
  void unitsAreSummarisedPerTypeWithoutNamesIdsPartsOrItemsListed() throws Exception {
    var state = campaign(10, false);

    var projection = StateProjection.brief(state, 7);
    var warriors = projection.reserves().get("WARRIOR");

    assertThat(projection.seq()).isEqualTo(7);
    assertThat(warriors.n()).isEqualTo(10);
    assertThat(warriors.itemsWorn()).isEqualTo(10);
    assertThat(warriors.knownPhobias()).isEqualTo(10);
    assertThat(warriors.health()).matches("\\d+/\\d+");
    var json = json(state);
    assertThat(json).doesNotContain("parts", "phobias", "gear", "slots", "\"name\"", "\"id\"");
    state
        .getUnits()
        .values()
        .forEach(units -> units.forEach(unit -> assertThat(json).doesNotContain(unit.getName())));
  }

  @Test
  void undiscoveredPhobiasAndRolledStrengthsStayHidden() throws Exception {
    var state = campaign(5, false);
    state
        .getUnits()
        .values()
        .forEach(
            units ->
                units.forEach(
                    unit -> {
                      unit.getPhobias().forEach(p -> assertThat(p.isKnown()).isIn(true, false));
                      unit.setHealthFactor(1.2345678);
                      unit.setDamageFactor(0.8765432);
                    }));

    var json = json(state);

    // Five phobias of each unit, one revealed: only the count of the revealed ones is shown, and
    // no enemy type is named while no expedition is out.
    for (var enemy : EnemyStats.values()) {
      assertThat(json).doesNotContain(enemy.name());
    }
    assertThat(json).doesNotContain("1.2345678", "0.8765432", "Factor", "Strength");
    assertThat(StateProjection.brief(state, 1).reserves().get("WARRIOR").knownPhobias())
        .isEqualTo(5);
  }

  @Test
  void vaultCreditsAndCarriedLootAreShownSeparately() {
    var projection = StateProjection.brief(campaign(1, true), 1);

    assertThat(projection.village().vault().credits()).isEqualTo(1234.5);
    assertThat(projection.expedition().loot().credits()).isEqualTo(55.5);
    assertThat(projection.expedition().spendableCredits()).isEqualTo(1290.0);
  }

  @Test
  void theExpeditionSectionIsAbsentWhileNoExpeditionIsOut() {
    assertThat(StateProjection.brief(campaign(1, false), 1).expedition()).isNull();
    assertThat(StateProjection.brief(campaign(1, true), 1).expedition()).isNotNull();
  }

  @Test
  void waveListingIsCappedButTheCountIsNot() {
    var state = campaign(1, true);
    for (var i = 0; i < 25; i++) {
      var wave =
          new io.github.diogohmcruz.towerdungeon.domain.models.Reinforcement(
              new HashMap<>(), 1.0, 1.0);
      state.getReinforcements().add(wave);
    }

    var expedition = StateProjection.brief(state, 1).expedition();

    assertThat(expedition.waveCount()).isEqualTo(25);
    assertThat(expedition.waves()).hasSize(StateProjection.MAX_LISTED_WAVES);
  }
}

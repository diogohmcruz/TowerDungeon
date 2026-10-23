package io.github.diogohmcruz.towerdungeon.agent.observe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
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
import io.github.diogohmcruz.towerdungeon.domain.services.ClockMode;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService.Meta;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The views stay small however big the Roster is, aggregate per unit type, and hold nothing hidden.
 * The key paths of a brief are golden: a field added to or dropped from the brief is a decision.
 */
class ViewsTest {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final Path GOLDEN = Path.of("src/test/resources/agent/brief-shape.txt");
  private static final Meta META = new Meta(42, false, ClockMode.MANUAL);

  private final GameProperties config = new GameProperties();
  private final ItemRating rating = new ItemRating(config.getItems().getWeights());

  /** A Campaign with {@code perType} units of ten unit types, kitted, haunted and wounded. */
  private GameState campaign(int perType, boolean expedition) {
    var random = new SplittableRandom(11);
    var state = new GameState(config);
    state.setCredit(1234.5);
    for (var type : UnitStats.values()) {
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
        state.addUnits(type, List.of(unit));
      }
    }
    if (expedition) {
      var tower =
          new Tower(
              config.getTower().getMaxFloor(),
              config.getBoss().copy(),
              config.getEnemies().copy(),
              new io.github.diogohmcruz.towerdungeon.domain.random.RandomSource(11),
              ArchitecturePlan.generate(1L, 100, 10, 5));
      state.setTower(tower);
      var party =
          new HashMap<UnitStats, List<io.github.diogohmcruz.towerdungeon.domain.models.Unit>>();
      for (var type : List.of(UnitStats.WARRIOR, UnitStats.ARCHER, UnitStats.PORTER)) {
        party.put(type, state.getUnits().remove(type));
      }
      state.setUnitsOnTower(party);
      tower.startNewRunAt(1);
      state.startRun();
      for (var wave = 0; wave < 15; wave++) {
        state
            .getReinforcements()
            .add(
                new io.github.diogohmcruz.towerdungeon.domain.models.Reinforcement(
                    new HashMap<>(), 10.0 + wave, 60.0));
      }
    }
    return state;
  }

  private int length(Map<String, Object> view) {
    return MAPPER.writeValueAsString(view).length();
  }

  @Test
  void eachViewStaysUnderItsCharacterBudgetOnABusyCampaign() {
    for (var expedition : List.of(true, false)) {
      var state = campaign(100, expedition);

      assertThat(length(StateViews.brief(state, META))).isLessThan(StateViews.BRIEF_BUDGET);
      assertThat(length(StateViews.full(state, META))).isLessThan(StateViews.FULL_BUDGET);
    }
  }

  @Test
  void theSizeDoesNotGrowWithTheNumberOfUnits() {
    var small = campaign(2, true);
    var large = campaign(300, true);

    assertThat(length(StateViews.brief(large, META)) - length(StateViews.brief(small, META)))
        .isLessThan(400);
    assertThat(length(StateViews.full(large, META)) - length(StateViews.full(small, META)))
        .isLessThan(400);
  }

  @Test
  void fullAddsAggregatedDetailAndNeverListsAUnit() {
    var state = campaign(8, false);
    var full = MAPPER.valueToTree(StateViews.full(state, META));

    var units = full.at("/detail/units");
    assertThat(units.get("count").asInt()).isEqualTo(8 * UnitStats.values().length);
    var known = 0;
    for (var count : units.get("known_phobias")) {
      known += count.asInt();
    }
    assertThat(known).isEqualTo(8 * UnitStats.values().length);
    assertThat(full.at("/detail/unlocked_units").size()).isGreaterThan(0);
    var text = full.toString();
    assertThat(text).doesNotContain("\"name\":\"", "\"id\"", "parts\":[", "phobias\":[", "gear");
    state
        .getUnits()
        .values()
        .forEach(units2 -> units2.forEach(u -> assertThat(text).doesNotContain(u.getName())));
  }

  @Test
  void aPhobiaCountsOnlyOnceItIsKnown() {
    var random = new SplittableRandom(5);
    var state = new GameState(config);
    for (var i = 0; i < 20; i++) {
      state.addUnits(UnitStats.WARRIOR, List.of(state.recruit(UnitStats.WARRIOR, random)));
    }
    var hidden =
        state.getUnits().get(UnitStats.WARRIOR).stream()
            .flatMap(u -> u.getPhobias().stream())
            .filter(p -> !p.isKnown())
            .toList();
    assertThat(hidden).as("the recruits are born with hidden phobias").isNotEmpty();

    var full = MAPPER.valueToTree(StateViews.full(state, META));

    assertThat(full.at("/detail/units/known_phobias").size()).isZero();
    hidden.forEach(p -> assertThat(full.toString()).doesNotContain(p.getFeared().name()));
  }

  @Test
  void rolledStrengthsDoNotAppear() {
    var state = campaign(3, false);
    state
        .getUnits()
        .values()
        .forEach(
            list ->
                list.forEach(
                    u -> {
                      u.setHealthFactor(1.2345678);
                      u.setDamageFactor(0.8765432);
                    }));

    var text = MAPPER.writeValueAsString(StateViews.full(state, META));

    assertThat(text).doesNotContain("1.2345678", "0.8765432", "Factor", "strength");
  }

  @Test
  void theBriefHasAGoldenShape() throws IOException {
    var brief = MAPPER.valueToTree(StateViews.brief(campaign(3, true), META));
    var paths = new TreeSet<String>();
    collect("", brief, paths);
    var shape = String.join("\n", paths) + "\n";
    if (Boolean.getBoolean("td.golden.update")) {
      Files.createDirectories(GOLDEN.getParent());
      Files.writeString(GOLDEN, shape);
    }

    assertThat(shape)
        .as("The brief changed shape. Review, then regenerate with -Dtd.golden.update=true")
        .isEqualTo(Files.readString(GOLDEN));
  }

  private static void collect(String prefix, JsonNode node, TreeSet<String> paths) {
    if (node.isObject()) {
      if (node.isEmpty()) {
        paths.add(prefix + "{}");
      }
      node.properties()
          .forEach(
              e -> {
                // Per-type rows share one shape: name them by their position, not by type.
                var key =
                    prefix.endsWith("reserves.")
                            || prefix.endsWith("party.")
                            || prefix.endsWith("types.")
                        ? "*"
                        : e.getKey();
                collect(prefix + key + ".", e.getValue(), paths);
              });
    } else if (node.isArray()) {
      if (node.isEmpty()) {
        paths.add(prefix + "[]");
      }
      node.forEach(item -> collect(prefix + "[].", item, paths));
    } else {
      paths.add(prefix.substring(0, prefix.length() - 1));
    }
  }

  @Test
  void actionsListWhatTheCampaignWouldAcceptWithReasonsForTheRest() {
    var state = new GameState(config);
    state.setCredit(10.0);

    var view = MAPPER.valueToTree(ActionViews.of(state, META));
    var actions = view.get("actions");

    JsonNode warrior = find(actions, "RECRUIT", "unit_type", "WARRIOR");
    // Credits must exceed the cost: 10 Credits buy three Warriors at 3, not a fourth.
    assertThat(warrior.get("max_quantity").asInt()).isEqualTo(3);
    JsonNode archer = find(actions, "RECRUIT", "unit_type", "ARCHER");
    assertThat(archer.get("max_quantity").asInt()).isEqualTo(1);
    JsonNode mage = find(actions, "RECRUIT", "unit_type", "MAGE");
    assertThat(mage.get("reason").asString()).isEqualTo("NOT_UNLOCKED");
    assertThat(mage.get("needs").asString()).isNotBlank();
    assertThat(find(actions, "EXTRACT", null, null).get("reason").asString())
        .isEqualTo("NO_EXPEDITION");
    assertThat(find(actions, "SEND_REINFORCEMENT_WAVE", null, null).get("reason").asString())
        .isEqualTo("NO_EXPEDITION");
  }

  private static JsonNode find(JsonNode actions, String action, String key, String value) {
    for (var candidate : actions) {
      if (candidate.get("action").asString().equals(action)
          && (key == null || value.equals(candidate.path(key).asString()))) {
        return candidate;
      }
    }
    throw new AssertionError("no action " + action + " " + value);
  }

  @Test
  void towerViewsShowOnlyFloorsTheCampaignHasReached() {
    var state = campaign(2, true);
    state.recordFloorReached(12);

    var tower = MAPPER.valueToTree(TowerViews.summary(state, META));
    assertThat(tower.get("highest_floor").asInt()).isEqualTo(12);
    assertThat(tower.get("guardian_floors_met").toString()).isEqualTo("[10]");
    assertThat(tower.get("surge_floors_met").toString()).isEqualTo("[5]");
    assertThat(TowerViews.floor(state, META, 13)).isEmpty();
    assertThat(TowerViews.floor(state, META, 0)).isEmpty();
    var ten = MAPPER.valueToTree(TowerViews.floor(state, META, 10).orElseThrow());
    assertThat(ten.get("guardian").asBoolean()).isTrue();
    assertThat(tower.toString()).doesNotContain("seed", "architecture", "blueprint", "plan");
  }

  @Test
  void enemyRulesNeedTheSandboxScope() {
    assertThat(RuleViews.section("enemies", config, false)).isNull();
    assertThat(RuleViews.section("enemies", config, true)).isNotNull();
    assertThat(RuleViews.section("nope", config, true)).isNull();
    assertThat(RuleViews.section("units", config, false).toString()).contains("WARRIOR");
    assertThat(Principal.dev().has(io.github.diogohmcruz.towerdungeon.agent.auth.Scope.SANDBOX))
        .isTrue();
  }
}

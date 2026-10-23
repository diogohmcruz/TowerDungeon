package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.Village;

/**
 * Fails the build when a field of the saved classes is neither written to the snapshot nor listed
 * here as transient with a reason. Add the new field to the snapshot (and the mapping below), or
 * explain why it is not persisted.
 */
class FieldAccountingTest {
  /** Field name to the snapshot component that carries it, as {@code Record.component}. */
  private static final Map<String, String> GAME_STATE_MAPPED =
      Map.ofEntries(
          Map.entry("credit", "CampaignSnapshot.credit"),
          Map.entry("units", "CampaignSnapshot.reserves"),
          Map.entry("unitsOnTower", "ExpeditionSnapshot.party"),
          Map.entry("tower", "CampaignSnapshot.tower"),
          Map.entry("expeditionActive", "GameSnapshot.expedition"),
          Map.entry("gameOutcome", "CampaignSnapshot.outcome"),
          Map.entry("village", "CampaignSnapshot.villageFood"),
          Map.entry("resources", "CampaignSnapshot.vault"),
          Map.entry("carriedLoot", "ExpeditionSnapshot.carriedLoot"),
          Map.entry("carriedCredits", "ExpeditionSnapshot.carriedCredits"),
          Map.entry("surplus", "ExpeditionSnapshot.surplus"),
          Map.entry("supplies", "ExpeditionSnapshot.supplies"),
          Map.entry("lastFoodReturned", "ExpeditionSnapshot.lastFoodReturned"),
          Map.entry("reinforcements", "ExpeditionSnapshot.waves"),
          Map.entry("purchasedUpgrades", "CampaignSnapshot.upgrades"),
          Map.entry("highestFloor", "CampaignSnapshot.highestFloor"),
          Map.entry("expeditionsCompleted", "CampaignSnapshot.expeditionsCompleted"),
          Map.entry("enemiesDefeated", "CampaignSnapshot.enemiesDefeated"),
          Map.entry("runStartFloor", "ExpeditionSnapshot.runStartFloor"),
          Map.entry("runEnemiesDefeated", "ExpeditionSnapshot.runEnemiesDefeated"),
          Map.entry("runUnitsLost", "ExpeditionSnapshot.runUnitsLost"),
          Map.entry("lastRunSummary", "CampaignSnapshot.lastExpeditionSummary"),
          Map.entry("achievedMilestones", "CampaignSnapshot.milestones"),
          Map.entry("openedShortcuts", "CampaignSnapshot.shortcutsOpened"),
          Map.entry("artifactsTaken", "CampaignSnapshot.artifactsTaken"),
          Map.entry("tally", "CampaignSnapshot.tally"),
          Map.entry("villageTier", "CampaignSnapshot.villageTier"),
          Map.entry("runFallen", "ExpeditionSnapshot.runFallen"),
          Map.entry("wallOfNames", "CampaignSnapshot.wallOfNames"));

  private static final Map<String, String> GAME_STATE_TRANSIENT =
      Map.of(
          "maxSupplies", "derived: carrying capacity is recomputed from upgrades and the party",
          "unlockedUnits", "derived: the starting types plus upgrade and milestone unlocks",
          "recoverySpend", "recomputed by the next lifecycle tick",
          "foodDistributionStrategy", "behaviour, not state; a default is installed on creation",
          "config", "game balance, supplied on restore",
          "ledger",
              "a log of what just happened, not a fact of the Campaign; starts empty on load");

  private static final Map<String, String> VILLAGE_MAPPED =
      Map.ofEntries(
          Map.entry("food", "CampaignSnapshot.villageFood"),
          Map.entry("villagersCount", "CampaignSnapshot.villagers"),
          Map.entry("breakers", "TradesSnapshot.breakers"),
          Map.entry("scribes", "TradesSnapshot.scribes"),
          Map.entry("settling", "TradesSnapshot.settlingTillers"),
          Map.entry("knowledge", "TradesSnapshot.knowledge"),
          Map.entry("quarryHeap", "TradesSnapshot.quarryHeap"),
          Map.entry("emergencyTilling", "TradesSnapshot.emergencyTilling"),
          Map.entry("villagersLost", "TradesSnapshot.villagersLost"),
          Map.entry("reservesLost", "TradesSnapshot.reservesLost"),
          Map.entry("tomes", "TradesSnapshot.tomes"),
          Map.entry("postLevels", "TradesSnapshot.postLevels"));

  private static final Map<String, String> VILLAGE_TRANSIENT =
      Map.of(
          "villagerFoodProduction", "game balance, copied from config on creation",
          "unitFoodConsumption", "game balance, copied from config on creation",
          "trades", "game balance, copied from config on creation");

  private static final Map<String, String> TOWER_MAPPED =
      Map.of(
          "currentFloor", "TowerSnapshot.currentFloor",
          "clearedBosses", "TowerSnapshot.clearedBosses",
          "floorMemory", "TowerSnapshot.floorGear");

  // floorMemory also carries the Risen, persisted as TowerSnapshot.risen.

  private static final Map<String, String> TOWER_TRANSIENT =
      Map.of(
          "maxFloor", "game balance, supplied from config on restore",
          "floors",
              "not persisted: each floor is rebuilt on arrival; the current garrison is in"
                  + " ExpeditionSnapshot.garrison",
          "bossConfig", "per-campaign copy of the config, supplied on restore",
          "enemiesConfig", "per-campaign copy of the config, supplied on restore",
          "random", "a source of randomness, supplied on restore",
          "architecturePlan", "derived from the architecture seed in config",
          "layoutVersion", "client redraw counter, recomputed on restore");

  private static final Map<String, String> UNIT_MAPPED =
      Map.ofEntries(
          Map.entry("id", "UnitSnapshot.id"),
          Map.entry("name", "UnitSnapshot.name"),
          Map.entry("stats", "UnitSnapshot.type"),
          Map.entry("parts", "UnitSnapshot.parts"),
          Map.entry("maxMana", "UnitSnapshot.maxMana"),
          Map.entry("currentMana", "UnitSnapshot.currentMana"),
          Map.entry("healthFactor", "UnitSnapshot.healthFactor"),
          Map.entry("damageFactor", "UnitSnapshot.damageFactor"),
          Map.entry("fixedStrengths", "UnitSnapshot.fixedStrengths"),
          Map.entry("sanity", "UnitSnapshot.sanity"),
          Map.entry("maxSanity", "UnitSnapshot.maxSanity"),
          Map.entry("phobias", "UnitSnapshot.phobias"),
          Map.entry("fearFloor", "UnitSnapshot.fearFloor"),
          Map.entry("gear", "UnitSnapshot.gear"),
          Map.entry("affinity", "UnitSnapshot.affinity"));

  private static final Map<String, String> UNIT_TRANSIENT =
      Map.of(
          "currentHealth", "derived: the sum of the present parts' health",
          "maxHealth", "derived: the sum of the present parts' maximum health",
          "bodyWeights", "game balance, supplied from config on restore",
          "sanityRules", "game balance, supplied from config on restore",
          "itemEffects", "game balance, supplied from config on restore");

  private static Set<String> fieldsOf(Class<?> type) {
    var names = new TreeSet<String>();
    for (var current = type; current != Object.class; current = current.getSuperclass()) {
      Arrays.stream(current.getDeclaredFields())
          .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
          .forEach(field -> names.add(field.getName()));
    }
    return names;
  }

  private static void assertAccounted(
      Class<?> type, Map<String, String> mapped, Map<String, String> transientFields) {
    var fields = fieldsOf(type);
    var accounted = new TreeSet<String>(mapped.keySet());
    accounted.addAll(transientFields.keySet());

    var unaccounted = new TreeSet<String>(fields);
    unaccounted.removeAll(accounted);
    assertThat(unaccounted)
        .as(
            "%s fields neither persisted nor on the TRANSIENT list with a reason. Map them in the"
                + " snapshot or list them as transient.",
            type.getSimpleName())
        .isEmpty();

    var stale = new TreeSet<String>(accounted);
    stale.removeAll(fields);
    assertThat(stale)
        .as("%s entries that no longer name a field; remove them", type.getSimpleName())
        .isEmpty();

    var both = new TreeSet<String>(mapped.keySet());
    both.retainAll(transientFields.keySet());
    assertThat(both).as("fields both mapped and transient").isEmpty();
    assertThat(transientFields.values()).allSatisfy(reason -> assertThat(reason).isNotBlank());
  }

  /** Every mapped target names a real record component, so a mapping cannot rot silently. */
  private static void assertTargetsExist(Map<String, String> mapped) {
    var records =
        Map.<String, Class<?>>of(
            "GameSnapshot", GameSnapshot.class,
            "CampaignSnapshot", CampaignSnapshot.class,
            "ExpeditionSnapshot", ExpeditionSnapshot.class,
            "TowerSnapshot", TowerSnapshot.class,
            "TradesSnapshot", TradesSnapshot.class,
            "TallySnapshot", TallySnapshot.class,
            "UnitSnapshot", UnitSnapshot.class);
    mapped
        .values()
        .forEach(
            target -> {
              var parts = target.split("\\.");
              var record = records.get(parts[0]);
              assertThat(record).as("record in %s", target).isNotNull();
              assertThat(Arrays.stream(record.getRecordComponents()).map(c -> c.getName()))
                  .as("component in %s", target)
                  .contains(parts[1]);
            });
  }

  @Test
  void everyGameStateFieldIsMappedOrTransient() {
    assertAccounted(GameState.class, GAME_STATE_MAPPED, GAME_STATE_TRANSIENT);
    assertTargetsExist(GAME_STATE_MAPPED);
  }

  @Test
  void everyVillageFieldIsMappedOrTransient() {
    assertAccounted(Village.class, VILLAGE_MAPPED, VILLAGE_TRANSIENT);
    assertTargetsExist(VILLAGE_MAPPED);
  }

  @Test
  void everyTowerFieldIsMappedOrTransient() {
    assertAccounted(Tower.class, TOWER_MAPPED, TOWER_TRANSIENT);
    assertTargetsExist(TOWER_MAPPED);
  }

  @Test
  void everyUnitFieldIsMappedOrTransient() {
    assertAccounted(Unit.class, UNIT_MAPPED, UNIT_TRANSIENT);
    assertTargetsExist(UNIT_MAPPED);
  }
}

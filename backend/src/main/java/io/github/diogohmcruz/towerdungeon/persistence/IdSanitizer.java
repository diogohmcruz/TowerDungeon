package io.github.diogohmcruz.towerdungeon.persistence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Drops the entries of a migrated record whose enum id no longer exists (a unit type, an upgrade, a
 * shortcut that a later build removed) instead of failing the whole load, and says so.
 */
final class IdSanitizer {
  private static final Set<String> UNIT_TYPES = PersistedIds.current(UnitStats.class);
  private static final Set<String> ENEMY_TYPES = PersistedIds.current(EnemyStats.class);
  private static final Set<String> BODY_PARTS = PersistedIds.current(BodyPart.class);
  private static final Set<String> RESOURCES = PersistedIds.current(ResourceType.class);
  private static final Set<String> UPGRADES = PersistedIds.current(UpgradeType.class);
  private static final Set<String> MILESTONES = PersistedIds.current(MilestoneType.class);
  private static final Set<String> SHORTCUTS = PersistedIds.current(ShortcutType.class);
  private static final Set<String> OUTCOMES = PersistedIds.current(GameOutcome.class);

  private final Set<String> dropped = new LinkedHashSet<>();

  private IdSanitizer() {}

  /** Edits the tree in place and returns one notice per kind of id dropped. */
  static List<String> sanitize(ObjectNode root) {
    var sanitizer = new IdSanitizer();
    sanitizer.campaign(root.get("campaign"));
    sanitizer.expedition(root.get("expedition"));
    return new ArrayList<>(sanitizer.dropped);
  }

  private void campaign(JsonNode node) {
    if (!(node instanceof ObjectNode campaign)) {
      return;
    }
    units(campaign.get("reserves"));
    keys(campaign.get("vault"), RESOURCES, "ResourceType");
    keys(campaign.get("upgrades"), UPGRADES, "UpgradeType");
    values(campaign.get("milestones"), MILESTONES, "MilestoneType");
    values(campaign.get("shortcutsOpened"), SHORTCUTS, "ShortcutType");
    JsonNode outcome = campaign.get("outcome");
    if (outcome != null && outcome.isString() && !OUTCOMES.contains(outcome.asString())) {
      dropped.add("GameOutcome." + outcome.asString());
      campaign.put("outcome", GameOutcome.PLAYING.name());
    }
  }

  private void expedition(JsonNode node) {
    if (!(node instanceof ObjectNode expedition)) {
      return;
    }
    units(expedition.get("party"));
    keys(expedition.get("carriedLoot"), RESOURCES, "ResourceType");
    dropEntries(expedition.get("garrison"), "type", ENEMY_TYPES, "EnemyStats");
    if (expedition.get("waves") instanceof ArrayNode waves) {
      waves.forEach(wave -> units(wave.get("units")));
    }
  }

  private void units(JsonNode node) {
    dropEntries(node, "type", UNIT_TYPES, "UnitStats");
    if (node instanceof ArrayNode units) {
      units.forEach(unit -> dropEntries(unit.get("parts"), "kind", BODY_PARTS, "BodyPart"));
    }
  }

  /** Removes the objects of an array whose {@code field} is not a known id. */
  private void dropEntries(JsonNode node, String field, Set<String> known, String enumName) {
    if (!(node instanceof ArrayNode array)) {
      return;
    }
    for (int i = array.size() - 1; i >= 0; i--) {
      JsonNode id = array.get(i).get(field);
      if (id != null && id.isString() && !known.contains(id.asString())) {
        dropped.add(enumName + "." + id.asString());
        array.remove(i);
      }
    }
  }

  /** Removes the entries of an array of ids that are not known. */
  private void values(JsonNode node, Set<String> known, String enumName) {
    if (!(node instanceof ArrayNode array)) {
      return;
    }
    for (int i = array.size() - 1; i >= 0; i--) {
      JsonNode id = array.get(i);
      if (id.isString() && !known.contains(id.asString())) {
        dropped.add(enumName + "." + id.asString());
        array.remove(i);
      }
    }
  }

  /** Removes the entries of a map whose key is not a known id. */
  private void keys(JsonNode node, Set<String> known, String enumName) {
    if (!(node instanceof ObjectNode map)) {
      return;
    }
    Iterator<String> names = new ArrayList<>(map.propertyNames()).iterator();
    while (names.hasNext()) {
      String name = names.next();
      if (!known.contains(name)) {
        dropped.add(enumName + "." + name);
        map.remove(name);
      }
    }
  }
}

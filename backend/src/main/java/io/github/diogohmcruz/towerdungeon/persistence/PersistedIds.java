package io.github.diogohmcruz.towerdungeon.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionChoice;

/**
 * The enums whose constant names are written into Campaign files, so renaming or removing one
 * strands saved records. {@code persistence/released-ids.txt} freezes every id that has shipped,
 * one {@code EnumName.CONSTANT} per line; a freeze test fails when one disappears without a
 * migration declaring it retired.
 */
public final class PersistedIds {
  static final String RELEASED_RESOURCE = "/persistence/released-ids.txt";

  private static final List<Class<? extends Enum<?>>> ENUMS =
      List.of(
          UnitStats.class,
          EnemyStats.class,
          BodyPart.class,
          ResourceType.class,
          UpgradeType.class,
          MilestoneType.class,
          ShortcutType.class,
          GameOutcome.class,
          InheritanceType.class,
          SuccessionChoice.class);

  private PersistedIds() {}

  /** The constant names currently defined for a persisted enum. */
  public static Set<String> current(Class<? extends Enum<?>> type) {
    Set<String> names = new LinkedHashSet<>();
    Arrays.stream(type.getEnumConstants()).forEach(c -> names.add(c.name()));
    return names;
  }

  /** Every {@code EnumName.CONSTANT} the code defines today. */
  public static Set<String> allCurrent() {
    Set<String> ids = new LinkedHashSet<>();
    for (var type : ENUMS) {
      current(type).forEach(id -> ids.add(type.getSimpleName() + "." + id));
    }
    return ids;
  }

  /** Every id frozen as released. */
  public static Set<String> released() {
    try (var in = PersistedIds.class.getResourceAsStream(RELEASED_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + RELEASED_RESOURCE);
      }
      Set<String> ids = new LinkedHashSet<>();
      for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        String id = line.strip();
        if (!id.isEmpty() && !id.startsWith("#")) {
          ids.add(id);
        }
      }
      return ids;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The constant names for each persisted enum, keyed by simple name. */
  static Map<String, Set<String>> currentByEnum() {
    Map<String, Set<String>> byEnum = new LinkedHashMap<>();
    ENUMS.forEach(type -> byEnum.put(type.getSimpleName(), current(type)));
    return byEnum;
  }
}

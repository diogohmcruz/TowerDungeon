package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.RequiredArgsConstructor;

/** Makes Leftover items for a floor, drawing every number from the injected random source. */
@Component
@RequiredArgsConstructor
public class LeftoverGenerator {
  private final RandomGenerator random;
  private final GameProperties properties;

  /** One Leftover: uniform category, one or two stats of its table, scaled by the floor. */
  public Item generate(int floor) {
    var config = properties.getItems().getLeftover();
    var categories = ItemCategory.values();
    var category = categories[random.nextInt(categories.length)];

    var table = new ArrayList<>(category.statTable());
    var count =
        Math.min(table.size(), random.nextInt(config.getMinStats(), config.getMaxStats() + 1));
    Collections.shuffle(table, random);

    var scale = 1 + config.getFloorScaling() * floor;
    var stats = new EnumMap<ItemStat, Double>(ItemStat.class);
    for (var stat : table.subList(0, count)) {
      var roll = random.nextDouble(config.getRollMin(), config.getRollMax());
      stats.put(stat, baseOf(config.getBase(), stat) * scale * roll);
    }
    return new Item(
        new UUID(random.nextLong(), random.nextLong()).toString(),
        "Leftover " + category.name().charAt(0) + category.name().substring(1).toLowerCase(),
        ItemTier.LEFTOVER,
        category,
        stats,
        null);
  }

  private static double baseOf(GameProperties.Items.Leftover.Base base, ItemStat stat) {
    return switch (stat) {
      case POWER -> base.getPower();
      case ARMOUR -> base.getArmour();
      case ACCURACY -> base.getAccuracy();
      case EVASION -> base.getEvasion();
      case SANITY -> base.getSanity();
    };
  }
}

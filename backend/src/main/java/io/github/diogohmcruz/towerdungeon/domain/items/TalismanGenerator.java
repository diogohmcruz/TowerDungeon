package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.RequiredArgsConstructor;

/** Makes Talismans and decides which tier a find is, drawing from the injected random source. */
@Component
@RequiredArgsConstructor
public class TalismanGenerator {
  private static final ItemEffect[] TALISMAN_EFFECTS =
      Arrays.stream(ItemEffect.values())
          .filter(effect -> effect.tier() == ItemTier.TALISMAN)
          .toArray(ItemEffect[]::new);

  private final RandomGenerator random;
  private final GameProperties properties;

  /** One Talisman with an effect drawn uniformly from the eight starters. */
  public Item generate() {
    var effect = TALISMAN_EFFECTS[random.nextInt(TALISMAN_EFFECTS.length)];
    return new Item(
        new UUID(random.nextLong(), random.nextLong()).toString(),
        displayName(effect) + " Talisman",
        ItemTier.TALISMAN,
        ItemCategory.TRINKET,
        Map.of(),
        effect.id());
  }

  /** Which tier of find a drop is, by the configured drop rates: Leftover or Talisman. */
  public ItemTier rollTier() {
    var rates = properties.getItems().getDropRates();
    var total = rates.getLeftover() + rates.getTalisman();
    return random.nextDouble() * total < rates.getTalisman()
        ? ItemTier.TALISMAN
        : ItemTier.LEFTOVER;
  }

  private static String displayName(ItemEffect effect) {
    var name = effect.name();
    return name.charAt(0) + name.substring(1).toLowerCase();
  }
}

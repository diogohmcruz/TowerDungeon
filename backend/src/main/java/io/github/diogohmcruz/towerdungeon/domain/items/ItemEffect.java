package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed set of special effects an item can carry: eight Talisman effects and four Artifact
 * effects. The {@link #id()} is what an {@link Item} stores as its effect id; ids are stable across
 * saves and never change. Values live in {@code game.items.effects}.
 */
public enum ItemEffect {
  STEADFAST("steadfast", ItemTier.TALISMAN),
  KEEN("keen", ItemTier.TALISMAN),
  WARY("wary", ItemTier.TALISMAN),
  EMBER("ember", ItemTier.TALISMAN),
  GLOOM("gloom", ItemTier.TALISMAN),
  SALTED("salted", ItemTier.TALISMAN),
  THORN("thorn", ItemTier.TALISMAN),
  LANTERN("lantern", ItemTier.TALISMAN),
  SARGANS_MASK("sargans-mask", ItemTier.ARTIFACT),
  LILICENS_MANA_VESSEL("lilicens-mana-vessel", ItemTier.ARTIFACT),
  DRAGON_SLAYERS_BLADE("dragon-slayers-blade", ItemTier.ARTIFACT),
  YUVAS_SALT_SATCHEL("yuvas-salt-satchel", ItemTier.ARTIFACT);

  private final String id;
  private final ItemTier tier;

  ItemEffect(String id, ItemTier tier) {
    this.id = id;
    this.tier = tier;
  }

  public String id() {
    return id;
  }

  /** The tier of item that carries this effect. */
  public ItemTier tier() {
    return tier;
  }

  public static Optional<ItemEffect> of(String id) {
    return Arrays.stream(values()).filter(effect -> effect.id.equals(id)).findFirst();
  }
}

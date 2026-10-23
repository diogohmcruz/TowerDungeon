package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.RequiredArgsConstructor;

/**
 * The four starter Artifacts, each at its configured home floor. An Artifact is unique: the
 * campaign holds one each, so a given id always names the same item.
 */
@Component
@RequiredArgsConstructor
public class HomeFloorArtifacts implements ArtifactProvider {
  private static final Map<ItemEffect, String> NAMES =
      Map.of(
          ItemEffect.SARGANS_MASK, "Sargan's Mask",
          ItemEffect.LILICENS_MANA_VESSEL, "Lilicen's Mana Vessel",
          ItemEffect.DRAGON_SLAYERS_BLADE, "the Dragon-Slayer's Blade",
          ItemEffect.YUVAS_SALT_SATCHEL, "Yuva's Salt Satchel");

  private final GameProperties properties;

  /** Every Artifact, in a fixed order. */
  public List<Item> all() {
    return NAMES.keySet().stream().sorted().map(HomeFloorArtifacts::artifact).toList();
  }

  @Override
  public List<Item> artifactsFor(int floor, Set<String> takenThisGeneration) {
    var homes = properties.getItems().getArtifacts();
    return all().stream()
        .filter(item -> homes.homeFloorOf(ItemEffect.of(item.effectId()).orElseThrow()) == floor)
        .filter(item -> !takenThisGeneration.contains(item.id()))
        .toList();
  }

  private static Item artifact(ItemEffect effect) {
    return new Item(
        effect.id(),
        NAMES.get(effect),
        ItemTier.ARTIFACT,
        ItemCategory.TRINKET,
        Map.of(),
        effect.id());
  }
}

package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemStat;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The unit card reads body, mind and gear straight from the unit's JSON. */
class UnitJsonTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void aUnitSerialisesItsBodyMindAndWornItemsForTheCard() {
    var unit = new Unit(UnitStats.WARRIOR);
    unit.addPhobia(EnemyStats.values()[0]);
    var helm =
        new Item(
            "h1",
            "Rusty Helm",
            ItemTier.LEFTOVER,
            ItemCategory.HELM,
            Map.of(ItemStat.ARMOUR, 1.5),
            null);
    unit.equip(helm, new ItemRating(new GameProperties().getItems().getWeights()));

    JsonNode json = mapper.valueToTree(unit);

    assertThat(json.get("parts")).hasSize(6);
    assertThat(json.get("parts").get(0).get("kind").asText()).isEqualTo("TORSO");
    assertThat(json.get("parts").get(0).get("lost").asBoolean()).isFalse();
    assertThat(json.has("sanity")).isTrue();
    assertThat(json.has("maxSanity")).isTrue();
    assertThat(json.get("phobias").get(0).get("known").asBoolean()).isFalse();
    var worn = json.get("gear").get("slots").get("HEAD");
    assertThat(worn.get("name").asText()).isEqualTo("Rusty Helm");
    assertThat(worn.get("tier").asText()).isEqualTo("LEFTOVER");
    assertThat(worn.get("stats").get("ARMOUR").asDouble()).isEqualTo(1.5);
  }
}

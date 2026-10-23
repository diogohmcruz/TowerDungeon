package io.github.diogohmcruz.towerdungeon.api.dtos;

import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * Setting out. {@code salt} is the Salt from the Loadout to salt Supplies with; null is none, which
 * is what one click on the button sends. {@code saltPack} is the Salt of a Salt Pack and {@code
 * saltBearer} the Unit type that carries it; null is no Pack.
 */
public record InvadeActionDTO(
    Map<UnitStats, Integer> units,
    Integer startFloor,
    Integer salt,
    Integer saltPack,
    UnitStats saltBearer) {
  public InvadeActionDTO(Map<UnitStats, Integer> units, Integer startFloor, Integer salt) {
    this(units, startFloor, salt, null, null);
  }

  public InvadeActionDTO(Map<UnitStats, Integer> units, Integer startFloor) {
    this(units, startFloor, null, null, null);
  }
}

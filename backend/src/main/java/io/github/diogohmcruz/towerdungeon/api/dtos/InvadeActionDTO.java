package io.github.diogohmcruz.towerdungeon.api.dtos;

import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * Setting out. {@code salt} is the Salt from the Loadout to salt Supplies with; null is none, which
 * is what one click on the button sends. {@code saltPack} is the Salt of a Salt Pack and {@code
 * saltBearer} the Unit type that carries it; null is no Pack. {@code panacea} carries a Panacea;
 * null is none. {@code overreach} sets the Expedition to Overreach, which needs the Inheritance;
 * null is off.
 */
public record InvadeActionDTO(
    Map<UnitStats, Integer> units,
    Integer startFloor,
    Integer salt,
    Integer saltPack,
    UnitStats saltBearer,
    Boolean panacea,
    Boolean overreach) {
  public InvadeActionDTO(
      Map<UnitStats, Integer> units,
      Integer startFloor,
      Integer salt,
      Integer saltPack,
      UnitStats saltBearer,
      Boolean panacea) {
    this(units, startFloor, salt, saltPack, saltBearer, panacea, null);
  }

  public InvadeActionDTO(
      Map<UnitStats, Integer> units,
      Integer startFloor,
      Integer salt,
      Integer saltPack,
      UnitStats saltBearer) {
    this(units, startFloor, salt, saltPack, saltBearer, null, null);
  }

  public InvadeActionDTO(Map<UnitStats, Integer> units, Integer startFloor, Integer salt) {
    this(units, startFloor, salt, null, null, null, null);
  }

  public InvadeActionDTO(Map<UnitStats, Integer> units, Integer startFloor) {
    this(units, startFloor, null, null, null, null, null);
  }
}

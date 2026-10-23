package io.github.diogohmcruz.towerdungeon.api.dtos;

import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * Setting out. {@code salt} is the Salt from the Loadout to salt Supplies with; null is none, which
 * is what one click on the button sends.
 */
public record InvadeActionDTO(Map<UnitStats, Integer> units, Integer startFloor, Integer salt) {
  public InvadeActionDTO(Map<UnitStats, Integer> units, Integer startFloor) {
    this(units, startFloor, null);
  }
}

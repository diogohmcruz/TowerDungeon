package io.github.diogohmcruz.towerdungeon.domain.exceptions;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InvalidInvasion extends RuntimeException {
  public InvalidInvasion(UnitStats unitStats, Integer quantityReceived, Integer quantityInReserves) {
    super(
        String.format(
            "Not enough %s in reserves to invade: %s requested, %s available",
            unitStats.name(), quantityReceived, quantityInReserves));
  }
}

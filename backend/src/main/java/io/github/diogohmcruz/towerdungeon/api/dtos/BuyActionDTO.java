package io.github.diogohmcruz.towerdungeon.api.dtos;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

public record BuyActionDTO(UnitStats unitStats, Integer quantity) {}

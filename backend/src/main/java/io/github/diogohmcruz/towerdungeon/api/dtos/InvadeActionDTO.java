package io.github.diogohmcruz.towerdungeon.api.dtos;

import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

public record InvadeActionDTO(Map<UnitStats, Integer> units) {}

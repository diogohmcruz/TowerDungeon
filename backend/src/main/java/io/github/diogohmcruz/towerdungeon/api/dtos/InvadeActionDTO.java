package io.github.diogohmcruz.towerdungeon.api.dtos;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import java.util.Map;

public record InvadeActionDTO(Map<UnitStats, Integer> units) {}

package io.github.diogohmcruz.towerdungeon.api.dtos;

import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * A mid-run reinforcement wave: which home units to send up after the active party. An empty (or
 * null) map sends everyone currently at home.
 */
public record ReinforceActionDTO(Map<UnitStats, Integer> units) {}

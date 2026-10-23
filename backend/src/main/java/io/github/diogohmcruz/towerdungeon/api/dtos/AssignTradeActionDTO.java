package io.github.diogohmcruz.towerdungeon.api.dtos;

import io.github.diogohmcruz.towerdungeon.domain.models.Trade;

/** Moves one Villager: {@code delta} of +1 takes a Tiller to the Trade, -1 sends one back. */
public record AssignTradeActionDTO(Trade trade, Integer delta) {}

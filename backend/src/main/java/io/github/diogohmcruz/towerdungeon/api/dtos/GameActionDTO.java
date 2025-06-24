package io.github.diogohmcruz.towerdungeon.api.dtos;

public record GameActionDTO<T>(GameAction gameAction, T payload) {}

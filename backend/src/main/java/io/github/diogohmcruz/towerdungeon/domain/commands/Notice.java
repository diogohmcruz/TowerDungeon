package io.github.diogohmcruz.towerdungeon.domain.commands;

/** A message for the player, with a stable code the client can switch on. */
public record Notice(String code, String message) {}

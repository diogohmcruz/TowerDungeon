package io.github.diogohmcruz.towerdungeon.domain.prestige;

/**
 * One Inheritance the player holds: its id and the Legacy actually paid for it. A refund gives back
 * {@code paid}, so a price retuned later neither robs nor pays the player.
 */
public record RollEntry(String id, int paid) {}

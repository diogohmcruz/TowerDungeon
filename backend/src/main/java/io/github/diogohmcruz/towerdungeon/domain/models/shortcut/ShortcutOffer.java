package io.github.diogohmcruz.towerdungeon.domain.models.shortcut;

/**
 * Player-facing view of a tower shortcut: the floor it deposits the party on, why it appeared (its
 * manga-flavored lore), how it is unlocked and whether it is currently open. Streamed inside the
 * game state so the frontend can list shortcuts and offer them as expedition start points.
 */
public record ShortcutOffer(
    String id, String name, String description, String trigger, int floor, boolean unlocked) {}

package io.github.diogohmcruz.towerdungeon.domain.models.shortcut;

/**
 * Player-facing view of a tower shortcut: the floor it deposits the party on, why it appeared (its
 * manga-flavored lore), how it is unlocked and whether it is currently open. Streamed inside the
 * game state so the frontend can list shortcuts and offer them as expedition start points. {@code
 * maxParty} is the largest Party that may take it; 0 for no limit.
 */
public record ShortcutOffer(
    String id,
    String name,
    String description,
    String trigger,
    int floor,
    boolean unlocked,
    int maxParty) {

  /** A Shortcut any Party may take. */
  public ShortcutOffer(
      String id, String name, String description, String trigger, int floor, boolean unlocked) {
    this(id, name, description, trigger, floor, unlocked, 0);
  }
}

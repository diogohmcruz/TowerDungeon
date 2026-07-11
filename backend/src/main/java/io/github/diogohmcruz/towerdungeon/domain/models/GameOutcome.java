package io.github.diogohmcruz.towerdungeon.domain.models;

/**
 * The campaign-level state of a player's game, distinct from a single expedition's outcome. A run
 * can be extracted or wiped many times while the game is still {@link #PLAYING}; the game only ends
 * when the party conquers the tower's summit ({@link #VICTORY}) or the village collapses with no
 * means left to field another soul ({@link #DEFEAT}).
 */
public enum GameOutcome {
  PLAYING,
  VICTORY,
  DEFEAT
}

package io.github.diogohmcruz.towerdungeon.domain.models.milestone;

/**
 * Player-facing view of a story milestone: its unlock condition and whether it has been achieved.
 * Streamed inside the game state so the frontend can show campaign progress. {@code companion} is
 * true when the milestone brings in a named companion rather than only strengthening the party.
 */
public record MilestoneOffer(
    String id,
    String name,
    String description,
    String trigger,
    boolean achieved,
    boolean companion) {}

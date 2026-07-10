package io.github.diogohmcruz.towerdungeon.domain.models.milestone;

/**
 * Player-facing view of a story milestone: its unlock condition and whether it has been achieved.
 * Streamed inside the game state so the frontend can show campaign progress.
 */
public record MilestoneOffer(
    String id, String name, String description, String trigger, boolean achieved) {}

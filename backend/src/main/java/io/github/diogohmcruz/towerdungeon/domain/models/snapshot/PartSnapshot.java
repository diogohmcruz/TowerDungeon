package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;

/** One body part's health pool; a lost arm or leg stays listed, flagged {@code lost}. */
public record PartSnapshot(BodyPart kind, double maxHealth, double currentHealth, boolean lost) {}

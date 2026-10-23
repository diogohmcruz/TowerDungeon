package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

/**
 * The persisted record of one Campaign: the always-saved {@code campaign} facts and the in-flight
 * {@code expedition} (null when no expedition is active). A {@code meta} section is reserved for
 * Prestige and is not written yet.
 */
public record GameSnapshot(CampaignSnapshot campaign, ExpeditionSnapshot expedition) {}

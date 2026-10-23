package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderStatus;

/**
 * The persisted facts of a Trial. The Terms' seed is not here: it rides in the {@link
 * RandomSnapshot}, and no Trial file ever leaves the server.
 */
public record TrialSnapshot(
    String raceId,
    int attempt,
    int finishFloor,
    long limitTicks,
    long ticksSinceStart,
    ContenderStatus status) {}

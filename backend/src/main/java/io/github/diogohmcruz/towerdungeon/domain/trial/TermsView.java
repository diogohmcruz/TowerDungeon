package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.time.Instant;

/**
 * What a player may read of a race before entering it: the Terms without their seed, hash or plan,
 * and the Campaign Key's own standing in it. {@code refusal} is the reason entry would be refused
 * now, if any; FULL cannot be known here and is only known at the door.
 *
 * @param attemptsUsed Trials this Key has started in the race
 * @param attemptsLeft Trials it may still start
 * @param attemptOpen whether its current attempt is still being run, so entry resumes it
 * @param refusal a {@link TrialDesk.Refusal} name, or null when entry is open
 */
public record TermsView(
    String raceId,
    TermsKind kind,
    int finishFloor,
    Instant opensAt,
    Instant closesAt,
    long limitSeconds,
    int maxAttempts,
    int maxConcurrent,
    int attemptsUsed,
    int attemptsLeft,
    boolean attemptOpen,
    String refusal) {}

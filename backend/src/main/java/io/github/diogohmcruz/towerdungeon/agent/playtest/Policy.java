package io.github.diogohmcruz.towerdungeon.agent.playtest;

import java.util.random.RandomGenerator;

/**
 * A scripted player. It sees what a player sees ({@link Sight}) and answers with one {@link Move}.
 * The runner carries the move out through the same action tools an agent calls, so a bot cannot do
 * what an agent cannot. A policy holds no state of its own: what it needs, the sight carries, so
 * the same seed and policy always make the same run.
 */
public interface Policy {
  /** A short token naming the policy in every telemetry row. */
  String name();

  Move next(Sight sight, RandomGenerator dice);
}

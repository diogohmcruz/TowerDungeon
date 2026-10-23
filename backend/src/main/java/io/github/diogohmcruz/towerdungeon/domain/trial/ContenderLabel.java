package io.github.diogohmcruz.towerdungeon.domain.trial;

/**
 * How a Contender says it plays. The label is declared at entry and never detected: a script on the
 * plain socket cannot be told from a hand, so an AGENT is ordered in a table of its own and never
 * among the hands.
 */
public enum ContenderLabel {
  HUMAN,
  AGENT
}

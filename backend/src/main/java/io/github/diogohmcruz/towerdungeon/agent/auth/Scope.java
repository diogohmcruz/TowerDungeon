package io.github.diogohmcruz.towerdungeon.agent.auth;

/** What a grant may do. Only the scopes the read surface needs exist so far. */
public enum Scope {
  /** Read the state, actions, Tower, rules, Ledger and events; wait. */
  OBSERVE("td:observe"),
  /** Open, advance and close Sandbox Campaigns. */
  SANDBOX("td:sandbox");

  private final String label;

  Scope(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}

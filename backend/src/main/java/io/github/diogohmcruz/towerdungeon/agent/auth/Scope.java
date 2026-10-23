package io.github.diogohmcruz.towerdungeon.agent.auth;

/** What a grant may do. */
public enum Scope {
  /** Read the state, actions, Tower, rules, Ledger and events; wait. */
  OBSERVE("td:observe"),
  /** Open, advance and close Sandbox Campaigns. */
  SANDBOX("td:sandbox"),
  /** Recruit, hire Villagers, sell and buy Upgrades: what happens in the Village. */
  ACT_VILLAGE("td:act:village"),
  /** Set out, send a Reinforcement Wave and Extract: what happens on the Tower. */
  ACT_EXPEDITION("td:act:expedition");

  private final String label;

  Scope(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}

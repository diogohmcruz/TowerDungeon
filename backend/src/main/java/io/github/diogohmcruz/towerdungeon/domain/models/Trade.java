package io.github.diogohmcruz.towerdungeon.domain.models;

/** The work a Villager does. Every Villager works one Trade; Tillers in the Fields are the rest. */
public enum Trade {
  TILLER("Tiller", "Fields"),
  BREAKER("Breaker", "Quarry"),
  SCRIBE("Scribe", "Scribe's Study"),
  SALTER("Salter", "Salt Pans"),
  SPOREWARDEN("Sporewarden", "Spore Cellar");

  private final String noun;
  private final String workplace;

  Trade(String noun, String workplace) {
    this.noun = noun;
    this.workplace = workplace;
  }

  /** Singular name of one who works the Trade: "Breaker". */
  public String noun() {
    return noun;
  }

  public String plural() {
    return noun + "s";
  }

  public String workplace() {
    return workplace;
  }
}

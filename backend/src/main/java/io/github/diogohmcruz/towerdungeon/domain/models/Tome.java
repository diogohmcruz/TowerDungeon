package io.github.diogohmcruz.towerdungeon.domain.models;

/**
 * A Village-side Repeatable Upgrade bought with Knowledge. Every Tome exists because someone came
 * back.
 */
public enum Tome {
  TILLERS_RECORD("Tillers' Record", "Fields output"),
  SEAM_CHARTS("Seam Charts", "Breaker output");

  private final String title;
  private final String effect;

  Tome(String title, String effect) {
    this.title = title;
    this.effect = effect;
  }

  public String title() {
    return title;
  }

  /** What each level raises: "Fields output". */
  public String effect() {
    return effect;
  }
}

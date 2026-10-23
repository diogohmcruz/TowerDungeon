package io.github.diogohmcruz.towerdungeon.domain.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A unit's fear of one enemy type. It bites from the start, but stays hidden from the player until
 * the unit first stands on a floor where that type is present: in the serialised form the type name
 * is {@code null} until then.
 */
@EqualsAndHashCode
@ToString
public class Phobia {
  private final EnemyStats enemyType;
  private boolean known;

  public Phobia(EnemyStats enemyType) {
    this(enemyType, false);
  }

  public Phobia(EnemyStats enemyType, boolean known) {
    this.enemyType = enemyType;
    this.known = known;
  }

  /** The feared enemy type, whether or not the player may know it yet. */
  @JsonIgnore
  public EnemyStats getFeared() {
    return enemyType;
  }

  /** The feared type's name, only once revealed. */
  @JsonProperty("enemyType")
  public String getRevealedType() {
    return known ? enemyType.name() : null;
  }

  @JsonProperty("known")
  public boolean isKnown() {
    return known;
  }

  /** Reveals the phobia; a revealed phobia stays revealed. */
  public void reveal() {
    this.known = true;
  }
}

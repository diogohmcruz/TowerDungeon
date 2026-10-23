package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * The free Inheritances, earned when the Lifetime Ledger crosses a threshold and kept for good. The
 * constant name is the id written into the player's file. Thresholds and sizes are config ({@code
 * game.prestige.epitaphs}), not here.
 */
public enum Epitaph {
  FIRST_FOOTING("First Footing"),
  NAMES_ON_THE_WALL("Names on the Wall"),
  TEN_SEALS_BROKEN("Ten Seals Broken");

  private final String displayName;

  Epitaph(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }

  /** Whether the Lifetime Ledger has crossed this Epitaph's threshold. */
  public boolean isEarned(LifetimeLedger lifetime, GameProperties.Prestige.Epitaphs config) {
    return switch (this) {
      case FIRST_FOOTING ->
          lifetime.highestFloor() >= config.getFirstFooting().getLifetimeHighestFloor();
      case NAMES_ON_THE_WALL ->
          lifetime.unitsLost() >= config.getNamesOnTheWall().getLifetimeUnitsLost();
      case TEN_SEALS_BROKEN ->
          lifetime.guardiansSlain() >= config.getTenSealsBroken().getLifetimeGuardiansSlain();
    };
  }

  /** The Epitaph with this id; empty for an id the registry does not know. */
  public static Optional<Epitaph> find(String id) {
    for (var epitaph : values()) {
      if (epitaph.name().equals(id)) {
        return Optional.of(epitaph);
      }
    }
    return Optional.empty();
  }

  /**
   * The ids held once the Ledger is read: every id already held (a ratchet: never lost, even when a
   * threshold is raised later) and every Epitaph the Ledger now earns, in declaration order.
   */
  public static List<String> granted(
      List<String> held, LifetimeLedger lifetime, GameProperties.Prestige.Epitaphs config) {
    var result = new ArrayList<>(held);
    for (var epitaph : values()) {
      if (!result.contains(epitaph.name()) && epitaph.isEarned(lifetime, config)) {
        result.add(epitaph.name());
      }
    }
    return result;
  }
}

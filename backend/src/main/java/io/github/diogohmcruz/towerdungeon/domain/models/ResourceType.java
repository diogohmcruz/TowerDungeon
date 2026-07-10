package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.EnumMap;
import java.util.Map;

/**
 * Resources gathered from the tower during a run. {@link #MATERIALS} drop on every floor and fund
 * units/gear; {@link #RELICS} are rare, only drop on deeper floors, and fund major/permanent
 * upgrades.
 */
public enum ResourceType {
  MATERIALS,
  RELICS;

  public static Map<ResourceType, Double> emptyWallet() {
    var wallet = new EnumMap<ResourceType, Double>(ResourceType.class);
    for (ResourceType type : values()) {
      wallet.put(type, 0d);
    }
    return wallet;
  }
}

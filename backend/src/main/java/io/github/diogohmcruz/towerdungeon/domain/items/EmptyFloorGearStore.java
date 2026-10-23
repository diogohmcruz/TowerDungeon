package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

/** A store with nothing in it, until gear can be left on floors. */
@Component
public class EmptyFloorGearStore implements FloorGearStore {
  @Override
  public List<Item> takeAll(int floor) {
    return List.of();
  }

  @Override
  public void putBack(int floor, Collection<Item> items) {}
}

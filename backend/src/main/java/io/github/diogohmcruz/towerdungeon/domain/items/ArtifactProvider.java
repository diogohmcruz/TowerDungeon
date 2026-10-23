package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.List;
import java.util.Set;

/**
 * Says which Artifacts a floor's Bundle should add. The Bundle asks once per floor in a generation,
 * passing the ids of the Artifacts already taken that generation.
 */
public interface ArtifactProvider {
  /**
   * The Artifacts whose home floor is {@code floor} and that were not taken this generation.
   *
   * @param takenThisGeneration the ids of the Artifacts already taken in this generation
   */
  List<Item> artifactsFor(int floor, Set<String> takenThisGeneration);
}

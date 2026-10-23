package io.github.diogohmcruz.towerdungeon.persistence;

import java.util.Set;
import java.util.function.Consumer;

import tools.jackson.databind.node.ObjectNode;

/**
 * One step of the schema chain: turns a record of schema {@link #from()} into one of schema {@code
 * from() + 1}, editing the JSON tree in place. The tree is the whole payload ({@code campaign},
 * {@code expedition}, {@code meta}), so a step can move fields between sections.
 */
public interface CampaignMigration {
  int from();

  void migrate(ObjectNode root);

  /**
   * Ids (as {@code EnumName.CONSTANT}, see {@code released-ids.txt}) that this step removes or
   * renames. Declaring them is what lets the freeze test pass for an id that no longer exists.
   */
  default Set<String> retiredIds() {
    return Set.of();
  }

  static CampaignMigration of(int from, Consumer<ObjectNode> step, String... retiredIds) {
    Set<String> retired = Set.of(retiredIds);
    return new CampaignMigration() {
      @Override
      public int from() {
        return from;
      }

      @Override
      public void migrate(ObjectNode root) {
        step.accept(root);
      }

      @Override
      public Set<String> retiredIds() {
        return retired;
      }
    };
  }
}

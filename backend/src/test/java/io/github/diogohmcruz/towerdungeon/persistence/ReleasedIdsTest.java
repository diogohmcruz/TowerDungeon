package io.github.diogohmcruz.towerdungeon.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The freeze: ids written into Campaign files may not disappear or be renamed unless a migration
 * says it handled them. Fails the build otherwise.
 */
class ReleasedIdsTest {
  @Test
  void everyReleasedIdStillExistsOrIsRetiredByAMigration() {
    Set<String> retired = new LinkedHashSet<>();
    CampaignMigrations.ALL.forEach(step -> retired.addAll(step.retiredIds()));
    Set<String> current = PersistedIds.allCurrent();

    Set<String> missing = new LinkedHashSet<>(PersistedIds.released());
    missing.removeAll(current);
    missing.removeAll(retired);

    assertThat(missing)
        .as(
            "Released ids were removed or renamed. Add a CampaignMigration that maps them and list"
                + " them in retiredIds(), instead of editing released-ids.txt")
        .isEmpty();
  }

  @Test
  void theFreezeFileIsWellFormed() {
    assertThat(PersistedIds.released())
        .isNotEmpty()
        .allMatch(id -> id.matches("[A-Za-z]+\\.[A-Z_]+"));
  }
}

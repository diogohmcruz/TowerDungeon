package io.github.diogohmcruz.towerdungeon.balance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The curve still lets every Generation's plan reach its Floor and stops it at a wall (#118), see
 * {@link PacingAcceptance}. The fast sample runs in the default build; the full sweep runs with
 * {@code -Dpacing.acceptance=full}.
 */
class PacingAcceptanceTest {

  @Test
  void everyGenerationsPlanReachesItsFloorAndMeetsAWall() {
    var failures = PacingAcceptance.check("fast");
    assertThat(failures).as(PacingAcceptance.describe(failures)).isEmpty();
  }

  @Test
  @EnabledIfSystemProperty(named = "pacing.acceptance", matches = "full")
  void fullSweep() {
    var failures = PacingAcceptance.check("full");
    assertThat(failures).as(PacingAcceptance.describe(failures)).isEmpty();
  }
}

package io.github.diogohmcruz.towerdungeon.persistence;

import java.util.List;

/**
 * The released schema versions and the steps between them. Bump {@link #CURRENT} and add a step
 * whenever a persisted shape changes in a way old files cannot read as they are; keep a golden
 * fixture for the schema that was left behind.
 */
public final class CampaignMigrations {
  /** The schema this build writes. */
  public static final int CURRENT = CampaignSchema.CURRENT;

  /** Steps in order; step {@code n} migrates schema {@code n} to {@code n + 1}. */
  public static final List<CampaignMigration> ALL = List.of();

  private CampaignMigrations() {}
}

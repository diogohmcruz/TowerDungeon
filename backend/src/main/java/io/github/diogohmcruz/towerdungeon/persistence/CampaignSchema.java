package io.github.diogohmcruz.towerdungeon.persistence;

/** The record schema this build writes and can read. */
public final class CampaignSchema {
  public static final int CURRENT = 1;

  private CampaignSchema() {}

  /** Thrown when a saved record was written by a newer build; the file must be left untouched. */
  public static class SchemaNewerException extends RuntimeException {
    public SchemaNewerException(int found) {
      super("Campaign schema " + found + " is newer than the supported schema " + CURRENT);
    }
  }
}

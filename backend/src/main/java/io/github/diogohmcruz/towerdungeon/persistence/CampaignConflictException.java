package io.github.diogohmcruz.towerdungeon.persistence;

/** A save was made against a sequence number that is no longer current. */
public class CampaignConflictException extends RuntimeException {
  private final long expectedSeq;
  private final long actualSeq;

  public CampaignConflictException(long expectedSeq, long actualSeq) {
    super("Expected sequence " + expectedSeq + " but the Campaign is at " + actualSeq);
    this.expectedSeq = expectedSeq;
    this.actualSeq = actualSeq;
  }

  public long getExpectedSeq() {
    return expectedSeq;
  }

  public long getActualSeq() {
    return actualSeq;
  }
}

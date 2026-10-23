package io.github.diogohmcruz.towerdungeon.persistence;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * The container shared by the files on disk and the exported {@code .tdc}: one header line ({@code
 * magic}, {@code schema}, {@code seq}, {@code savedAt}, {@code gameVersion}, {@code bytes}, {@code
 * sha256}) followed by the payload. The hash covers the exact payload bytes. The Campaign Key is
 * never part of it.
 */
public record CampaignFile(
    byte[] payload, int schema, String gameVersion, long seq, Instant savedAt) {
  public static final String MAGIC = "TDC1";
  static final Pattern GAME_VERSION = Pattern.compile("[A-Za-z0-9._+-]{1,64}");
  private static final int MAX_HEADER = 512;

  public static byte[] encode(
      int schema, long seq, Instant savedAt, String gameVersion, byte[] payload) {
    String header =
        "%s schema=%d seq=%d savedAt=%s gameVersion=%s bytes=%d sha256=%s\n"
            .formatted(
                MAGIC,
                schema,
                seq,
                savedAt,
                gameVersion,
                payload.length,
                HexFormat.of().formatHex(sha256(payload)));
    byte[] head = header.getBytes(US_ASCII);
    byte[] out = Arrays.copyOf(head, head.length + payload.length);
    System.arraycopy(payload, 0, out, head.length, payload.length);
    return out;
  }

  /**
   * Verifies magic, byte count and SHA-256 and parses the header.
   *
   * @throws CorruptFileException when any of them does not hold
   */
  public static CampaignFile decode(byte[] content) {
    int newline = -1;
    for (int i = 0; i < content.length && i < MAX_HEADER; i++) {
      if (content[i] == '\n') {
        newline = i;
        break;
      }
    }
    if (newline < 0) {
      throw new CorruptFileException("no header line");
    }
    String[] parts = new String(content, 0, newline, US_ASCII).split(" ", -1);
    if (parts.length != 7 || !parts[0].equals(MAGIC)) {
      throw new CorruptFileException("bad header");
    }
    try {
      int schema = Integer.parseInt(field(parts[1], "schema"));
      long seq = Long.parseLong(field(parts[2], "seq"));
      Instant savedAt = Instant.parse(field(parts[3], "savedAt"));
      String gameVersion = field(parts[4], "gameVersion");
      int bytes = Integer.parseInt(field(parts[5], "bytes"));
      String sha = field(parts[6], "sha256");
      if (!GAME_VERSION.matcher(gameVersion).matches()) {
        throw new CorruptFileException("bad game version");
      }
      byte[] payload = Arrays.copyOfRange(content, newline + 1, content.length);
      if (payload.length != bytes) {
        throw new CorruptFileException("byte count mismatch");
      }
      if (!HexFormat.of().formatHex(sha256(payload)).equals(sha)) {
        throw new CorruptFileException("checksum mismatch");
      }
      return new CampaignFile(payload, schema, gameVersion, seq, savedAt);
    } catch (CorruptFileException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new CorruptFileException("unparseable header");
    }
  }

  private static String field(String part, String name) {
    String prefix = name + "=";
    if (!part.startsWith(prefix)) {
      throw new CorruptFileException("missing " + name);
    }
    return part.substring(prefix.length());
  }

  private static byte[] sha256(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The bytes are not a readable Campaign file. */
  public static class CorruptFileException extends RuntimeException {
    public CorruptFileException(String message) {
      super(message);
    }
  }
}

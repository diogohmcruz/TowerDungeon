package io.github.diogohmcruz.towerdungeon.agent.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a bearer token, as lower-case hex. A token is only ever compared by its hash, in
 * constant time, so neither the configuration nor memory beyond the request holds the raw value.
 */
public final class TokenHasher {
  private TokenHasher() {}

  public static String sha256Hex(String token) {
    return HexFormat.of().formatHex(digest(token));
  }

  /** Whether the token hashes to {@code expectedHex}; constant time in the hash. */
  public static boolean matches(String token, String expectedHex) {
    if (token == null || expectedHex == null || expectedHex.isBlank()) {
      return false;
    }
    byte[] expected;
    try {
      expected = HexFormat.of().parseHex(expectedHex.strip().toLowerCase());
    } catch (IllegalArgumentException e) {
      return false;
    }
    return MessageDigest.isEqual(digest(token), expected);
  }

  private static byte[] digest(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }
}

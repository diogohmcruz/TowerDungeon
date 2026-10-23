package io.github.diogohmcruz.towerdungeon.persistence;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The anonymous Campaign Key a browser mints (16 random bytes, base64url, 22 characters) and the
 * player id the server derives from it. The server never keeps or logs the key itself.
 */
public final class CampaignKey {
  private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{22}$");

  private CampaignKey() {}

  public static boolean isValid(String key) {
    return key != null && FORMAT.matcher(key).matches();
  }

  /**
   * The player id of a key: the lowercase hex of the first 16 bytes of its SHA-256. Empty when the
   * key is not in the expected format, which is checked before anything touches the filesystem.
   */
  public static Optional<String> playerId(String key) {
    if (!isValid(key)) {
      return Optional.empty();
    }
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(US_ASCII));
      return Optional.of(HexFormat.of().formatHex(digest, 0, 16));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every JRE", e);
    }
  }
}

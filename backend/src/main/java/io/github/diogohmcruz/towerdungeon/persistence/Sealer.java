package io.github.diogohmcruz.towerdungeon.persistence;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seals the spoiler-sensitive parts of a Campaign file (the Tower's secret, the Blueprint) so an
 * exported file does not reveal what the player has not yet met. AES-256-GCM from the JDK, keyed by
 * {@code game.persistence.seal-key}, which must stay the same across deploys or old files cannot be
 * opened. This is a spoiler guard, not security: the source and the seed formula are public.
 *
 * <p>A sealed block is {@code base64url(version || iv || ciphertext+tag)}.
 */
public final class Sealer {
  /** Used outside production when no key is configured. Never use it for a real deployment. */
  static final String DEV_KEY = "tower-dungeon-development-seal-key-not-for-production";

  private static final Logger log = LoggerFactory.getLogger(Sealer.class);
  private static final byte VERSION = 1;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final byte[] AAD = "towerdungeon.seal.v1".getBytes(StandardCharsets.US_ASCII);

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  /** A sealed block that cannot be opened: tampered with, or sealed under another key. */
  public static class UnsealException extends RuntimeException {
    public UnsealException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public Sealer(String sealKey) {
    if (sealKey == null || sealKey.isBlank()) {
      throw new IllegalArgumentException("The seal key is blank");
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(sealKey.getBytes(StandardCharsets.UTF_8));
      this.key = new SecretKeySpec(digest, "AES");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * The Sealer for the configured key.
   *
   * @param production when true a missing key stops the application from starting; otherwise a
   *     fixed development key is used and a warning is logged
   * @throws IllegalStateException if {@code production} and no key is set
   */
  public static Sealer forConfig(String configuredKey, boolean production) {
    if (configuredKey != null && !configuredKey.isBlank()) {
      return new Sealer(configuredKey);
    }
    if (production) {
      throw new IllegalStateException(
          "game.persistence.seal-key must be set in production: it has to stay the same across"
              + " deploys, or exported Campaign files can no longer be opened");
    }
    log.warn(
        "game.persistence.seal-key is not set; using the fixed development key. Set it before"
            + " deploying.");
    return new Sealer(DEV_KEY);
  }

  public String seal(byte[] plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(AAD);
      byte[] encrypted = cipher.doFinal(plaintext);
      byte[] block = new byte[1 + IV_BYTES + encrypted.length];
      block[0] = VERSION;
      System.arraycopy(iv, 0, block, 1, IV_BYTES);
      System.arraycopy(encrypted, 0, block, 1 + IV_BYTES, encrypted.length);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(block);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Sealing failed", e);
    }
  }

  /**
   * @throws UnsealException when the block is malformed, was altered, or was sealed with another
   *     key
   */
  public byte[] unseal(String sealed) {
    try {
      byte[] block = Base64.getUrlDecoder().decode(sealed);
      if (block.length < 1 + IV_BYTES + TAG_BITS / 8 || block[0] != VERSION) {
        throw new UnsealException("Not a sealed block", null);
      }
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, block, 1, IV_BYTES));
      cipher.updateAAD(AAD);
      return cipher.doFinal(block, 1 + IV_BYTES, block.length - 1 - IV_BYTES);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new UnsealException("The sealed block cannot be opened with this key", e);
    }
  }
}

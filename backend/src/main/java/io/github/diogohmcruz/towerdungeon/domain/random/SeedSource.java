package io.github.diogohmcruz.towerdungeon.domain.random;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where a Campaign's seed comes from. A human Campaign's seed is drawn here from a server secret
 * and is never accepted from a client, because a known seed would reveal the Tower. This is the
 * only place in the game that draws from an operating-system source of entropy; everything after it
 * is a pure function of the seed.
 *
 * <p>The secret is {@code game.seed-secret}; when unset, a random one is made at start-up (the
 * seeds already issued are saved with their Campaigns, so losing it loses nothing).
 */
@Component
public class SeedSource {
  private static final String HMAC = "HmacSHA256";

  private final SecureRandom entropy = new SecureRandom();
  private final byte[] secret;
  private final Long fixed;

  @Autowired
  public SeedSource(@Value("${game.seed-secret:}") String configuredSecret) {
    this.secret =
        configuredSecret.isBlank()
            ? randomSecret()
            : configuredSecret.getBytes(StandardCharsets.UTF_8);
    this.fixed = null;
  }

  private SeedSource(long fixed) {
    this.secret = new byte[] {0};
    this.fixed = fixed;
  }

  /** A source that always hands out {@code seed}: for tests and replays, never for the server. */
  public static SeedSource fixed(long seed) {
    return new SeedSource(seed);
  }

  /** The random source of a new human Campaign, seeded from the server secret. */
  public RandomSource forHumanCampaign() {
    return new RandomSource(fixed != null ? fixed : drawSeed());
  }

  private long drawSeed() {
    var nonce = new byte[16];
    entropy.nextBytes(nonce);
    try {
      var mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret, HMAC));
      return ByteBuffer.wrap(mac.doFinal(nonce)).getLong();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("The seed could not be drawn.", e);
    }
  }

  private byte[] randomSecret() {
    var bytes = new byte[32];
    entropy.nextBytes(bytes);
    return bytes;
  }
}

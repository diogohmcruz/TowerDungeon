package io.github.diogohmcruz.towerdungeon.persistence;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.persistence.Sealer.UnsealException;

class SealerTest {
  private static final byte[] SECRET = "{\"mutations\":[\"veil\"],\"seed\":42}".getBytes(UTF_8);

  @Test
  void sealingRoundTrips() {
    var sealer = new Sealer("key-one");

    String sealed = sealer.seal(SECRET);

    assertThat(sealed).doesNotContain("veil");
    assertThat(sealer.unseal(sealed)).isEqualTo(SECRET);
  }

  @Test
  void anEmptyBlockRoundTrips() {
    var sealer = new Sealer("key-one");

    assertThat(sealer.unseal(sealer.seal(new byte[0]))).isEmpty();
  }

  @Test
  void sealingTwiceGivesDifferentBlocks() {
    var sealer = new Sealer("key-one");

    assertThat(sealer.seal(SECRET)).isNotEqualTo(sealer.seal(SECRET));
  }

  @Test
  void aKeyWithTheSameValueOpensTheBlockAfterARestart() {
    String sealed = new Sealer("key-one").seal(SECRET);

    assertThat(new Sealer("key-one").unseal(sealed)).isEqualTo(SECRET);
  }

  @Test
  void aWrongKeyFailsCleanly() {
    String sealed = new Sealer("key-one").seal(SECRET);

    assertThatThrownBy(() -> new Sealer("key-two").unseal(sealed))
        .isInstanceOf(UnsealException.class);
  }

  @Test
  void aTamperedBlockFailsCleanly() {
    var sealer = new Sealer("key-one");
    String sealed = sealer.seal(SECRET);
    // A character in the middle: the last one can hold padding bits that change nothing.
    int at = sealed.length() / 2;
    char changed = sealed.charAt(at) == 'A' ? 'B' : 'A';
    String tampered = sealed.substring(0, at) + changed + sealed.substring(at + 1);

    assertThatThrownBy(() -> sealer.unseal(tampered)).isInstanceOf(UnsealException.class);
  }

  @Test
  void garbageFailsCleanly() {
    var sealer = new Sealer("key-one");

    assertThatThrownBy(() -> sealer.unseal("not a sealed block!"))
        .isInstanceOf(UnsealException.class);
    assertThatThrownBy(() -> sealer.unseal("")).isInstanceOf(UnsealException.class);
    assertThatThrownBy(() -> sealer.unseal("AAAA")).isInstanceOf(UnsealException.class);
  }

  @Test
  void aBlankKeyIsRefused() {
    assertThatThrownBy(() -> new Sealer(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void productionRefusesToStartWithoutAKey() {
    assertThatThrownBy(() -> Sealer.forConfig(null, true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("game.persistence.seal-key");
    assertThatThrownBy(() -> Sealer.forConfig("  ", true))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void productionUsesTheConfiguredKey() {
    String sealed = Sealer.forConfig("prod-key", true).seal(SECRET);

    assertThat(new Sealer("prod-key").unseal(sealed)).isEqualTo(SECRET);
  }

  @Test
  void developmentFallsBackToAFixedKey() {
    String sealed = Sealer.forConfig(null, false).seal(SECRET);

    assertThat(Sealer.forConfig("", false).unseal(sealed)).isEqualTo(SECRET);
    assertThat(new Sealer(Sealer.DEV_KEY).unseal(sealed)).isEqualTo(SECRET);
  }
}

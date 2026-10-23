package io.github.diogohmcruz.towerdungeon.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CampaignKeyTest {
  private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAA";

  @Test
  void aWellFormedKeyHashesToAThirtyTwoCharLowercaseHexPlayerId() {
    var id = CampaignKey.playerId(KEY).orElseThrow();

    assertThat(id).matches("[0-9a-f]{32}");
    assertThat(id).isEqualTo(CampaignKey.playerId(KEY).orElseThrow());
    assertThat(id).isNotEqualTo(CampaignKey.playerId("BBBBBBBBBBBBBBBBBBBBBB").orElseThrow());
  }

  @Test
  void theIdIsTheFirstHalfOfTheSha256() throws Exception {
    var digest =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(KEY.getBytes(java.nio.charset.StandardCharsets.US_ASCII));

    assertThat(CampaignKey.playerId(KEY).orElseThrow())
        .isEqualTo(java.util.HexFormat.of().formatHex(digest).substring(0, 32));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "short",
        "AAAAAAAAAAAAAAAAAAAAA", // 21
        "AAAAAAAAAAAAAAAAAAAAAAA", // 23
        "AAAAAAAAAAAAAAAAAAAA+A", // not base64url
        "AAAAAAAAAAAAAAAAAAAA/A",
        "AAAAAAAAAAAAAAAAAAAA==",
        "../../etc/passwd/xxxxx",
        "AAAAAAAAAAAAAAAAAAAAA\n"
      })
  void anythingElseIsRejected(String key) {
    assertThat(CampaignKey.isValid(key)).isFalse();
    assertThat(CampaignKey.playerId(key)).isEmpty();
  }

  @Test
  void base64urlCharactersAreAccepted() {
    assertThat(CampaignKey.isValid("abcXYZ019_-abcXYZ019_-")).isTrue();
  }
}

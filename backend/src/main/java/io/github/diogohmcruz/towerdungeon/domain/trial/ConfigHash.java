package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A fingerprint of the balance: every tuning value that can change how a Trial plays out. Two
 * servers with the same hash play the same game, so a result is valid only for the hash it was
 * played on. Where Campaigns are kept, who may connect, the agent surface and the Trials themselves
 * change no outcome and are left out.
 */
public final class ConfigHash {
  private static final Set<String> NOT_BALANCE =
      Set.of("persistence", "security", "agent", "trial");

  private static final ObjectMapper CANONICAL =
      JsonMapper.builder()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  private ConfigHash() {}

  /** The first 16 hex digits of the SHA-256 of the balance in a canonical JSON form. */
  public static String of(GameProperties config) {
    var tree = (ObjectNode) CANONICAL.valueToTree(config);
    tree.remove(NOT_BALANCE);
    try {
      var digest =
          MessageDigest.getInstance("SHA-256")
              .digest(CANONICAL.writeValueAsString(tree).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every JRE", e);
    }
  }
}

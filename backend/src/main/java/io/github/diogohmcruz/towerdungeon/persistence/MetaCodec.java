package io.github.diogohmcruz.towerdungeon.persistence;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.domain.prestige.PlayerMeta;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns the {@code meta} section of a record into a {@link PlayerMeta} and back. The Tower's secret
 * is sealed inside the file, so a player cannot read the Tower out of their own export.
 *
 * <p>Reading is forgiving: {@code meta} is kept across redesigns, so a section that is empty, from
 * an older shape or damaged reads as what can be salvaged, and a missing or unopenable secret is
 * replaced by a new one (the Tower of that Player is then not the one they saw before).
 */
@Component
public class MetaCodec {
  private static final Logger log = LoggerFactory.getLogger(MetaCodec.class);

  private final ObjectMapper mapper;
  private final ObjectMapper reader;
  private final Sealer sealer;

  @Autowired
  public MetaCodec(ObjectMapper mapper, Sealer sealer) {
    this.mapper = mapper;
    this.reader =
        mapper.rebuild().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    this.sealer = sealer;
  }

  /** A codec under the development seal key, for tests. */
  public static MetaCodec development(ObjectMapper mapper) {
    return new MetaCodec(mapper, Sealer.development());
  }

  /** The {@code meta} section to store: the secret sealed, everything else as it is. */
  public ObjectNode toNode(PlayerMeta meta) {
    ObjectNode node = mapper.valueToTree(meta);
    node.put("towerSecret", sealer.seal(meta.towerSecret().getBytes(StandardCharsets.UTF_8)));
    return node;
  }

  /**
   * The meta a stored section holds. A null or empty section gives the meta of a first Campaign
   * with no secret yet ({@link PlayerMeta#hasSecret()} is false), and so does a secret that is
   * missing or cannot be opened: the caller mints a new one.
   */
  public PlayerMeta fromNode(JsonNode node) {
    if (!(node instanceof ObjectNode section) || section.isEmpty()) {
      return PlayerMeta.first("");
    }
    String secret = "";
    try {
      var sealed = section.path("towerSecret").asString("");
      if (!sealed.isEmpty()) {
        secret = new String(sealer.unseal(sealed), StandardCharsets.UTF_8);
      }
    } catch (Sealer.UnsealException e) {
      log.warn("The Tower's secret could not be opened; a new one will be made.");
    }
    var copy = section.deepCopy();
    copy.remove("towerSecret");
    PlayerMeta read;
    try {
      read = reader.treeToValue(copy, PlayerMeta.class);
    } catch (RuntimeException e) {
      log.warn(
          "The meta section could not be read as it is; keeping what can be kept: {}",
          e.toString());
      read = salvage(copy);
    }
    return new PlayerMeta(
        read.generation(),
        read.legacy(),
        read.roll(),
        read.residue(),
        read.lifetime(),
        read.chronicle(),
        read.slainGuardians(),
        read.epitaphs(),
        read.openBreach(),
        secret,
        read.raceRecords());
  }

  /** What survives a section that no longer fits the shape: the numbers that still parse. */
  private PlayerMeta salvage(ObjectNode section) {
    return new PlayerMeta(
        section.path("generation").asInt(1),
        section.path("legacy").asInt(0),
        null,
        section.path("residue").asDouble(0),
        null,
        null,
        null,
        null,
        0,
        null);
  }
}

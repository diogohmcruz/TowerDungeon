package io.github.diogohmcruz.towerdungeon.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns a {@link GameSnapshot} (plus the {@code meta} section reserved for Prestige) into the
 * payload JSON a record carries, and back: reading any released schema through the {@link
 * CampaignMigration} chain, dropping ids that no longer exist, and validating the result by
 * restoring it.
 *
 * <p>When the {@code campaign} cannot be brought to the current schema or fails validation, it is
 * cleared but {@code meta} is kept, with a notice.
 */
@Component
public class CampaignCodec {
  public static final String CAMPAIGN_LOST = "Campaign lost to a redesign. Legacy kept.";

  private static final Logger log = LoggerFactory.getLogger(CampaignCodec.class);

  private final GameProperties config;
  private final ObjectMapper mapper;
  private final ObjectMapper reader;
  private final int current;
  private final Map<Integer, CampaignMigration> steps;

  @Autowired
  public CampaignCodec(GameProperties config, ObjectMapper mapper) {
    this(config, mapper, CampaignSchema.CURRENT, CampaignMigrations.ALL);
  }

  CampaignCodec(
      GameProperties config, ObjectMapper mapper, int current, List<CampaignMigration> steps) {
    this.config = config;
    this.mapper = mapper;
    // A field added since the record was written reads as its default, so a purely additive change
    // needs no migration.
    this.reader =
        mapper.rebuild().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    this.current = current;
    this.steps = steps.stream().collect(Collectors.toMap(CampaignMigration::from, s -> s));
  }

  /** The schema this codec writes. */
  public int current() {
    return current;
  }

  /** Receives a copy of the record as it was before each migration step. */
  @FunctionalInterface
  public interface PreMigrationSink {
    PreMigrationSink NONE = (schema, payload) -> {};

    void keep(int schema, byte[] payload);
  }

  /**
   * @param snapshot the Campaign, or null when it could not be recovered (see {@link #campaignLost}
   *     and {@code notices})
   * @param meta the {@code meta} section, kept whatever happened to the campaign
   */
  public record Decoded(
      GameSnapshot snapshot, ObjectNode meta, List<String> notices, boolean campaignLost) {}

  public byte[] encode(GameSnapshot snapshot, ObjectNode meta) {
    ObjectNode root = mapper.valueToTree(snapshot);
    root.set("meta", meta == null ? mapper.createObjectNode() : meta);
    return mapper.writeValueAsBytes(root);
  }

  /**
   * Reads a payload written under {@code schema}.
   *
   * @param beforeStep called with the record as it stands before each migration step
   * @throws CampaignSchema.SchemaNewerException when {@code schema} is newer than {@link
   *     #current()}
   */
  public Decoded decode(int schema, byte[] payload, PreMigrationSink beforeStep) {
    if (schema > current) {
      throw new CampaignSchema.SchemaNewerException(schema);
    }
    List<String> notices = new ArrayList<>();
    ObjectNode root;
    try {
      JsonNode parsed = mapper.readTree(payload);
      if (!(parsed instanceof ObjectNode object)) {
        throw new IllegalArgumentException("payload is not a JSON object");
      }
      root = object;
    } catch (RuntimeException e) {
      log.warn("Unreadable payload at schema {}: {}", schema, e.toString());
      return lost(mapper.createObjectNode(), notices);
    }

    ObjectNode meta = metaOf(root);
    for (int version = schema; version < current; version++) {
      CampaignMigration step = steps.get(version);
      beforeStep.keep(version, mapper.writeValueAsBytes(root));
      try {
        if (step == null) {
          throw new IllegalStateException("no migration from schema " + version);
        }
        step.migrate(root);
        meta = metaOf(root);
      } catch (RuntimeException e) {
        log.warn("Migration from schema {} failed: {}", version, e.toString());
        return lost(meta, notices);
      }
    }

    List<String> dropped = IdSanitizer.sanitize(root);
    if (!dropped.isEmpty()) {
      notices.add("Dropped ids that no longer exist: " + String.join(", ", dropped) + ".");
    }

    try {
      root.remove("meta");
      GameSnapshot snapshot = reader.treeToValue(root, GameSnapshot.class);
      if (snapshot == null || snapshot.campaign() == null) {
        throw new IllegalArgumentException("no campaign");
      }
      GameState.restore(snapshot, config);
      return new Decoded(snapshot, meta, List.copyOf(notices), false);
    } catch (RuntimeException e) {
      log.warn("Migrated record failed validation: {}", e.toString());
      return lost(meta, notices);
    }
  }

  private Decoded lost(ObjectNode meta, List<String> notices) {
    List<String> all = new ArrayList<>(notices);
    all.add(CAMPAIGN_LOST);
    return new Decoded(null, meta, List.copyOf(all), true);
  }

  private ObjectNode metaOf(ObjectNode root) {
    return root.get("meta") instanceof ObjectNode meta
        ? meta.deepCopy()
        : mapper.createObjectNode();
  }
}

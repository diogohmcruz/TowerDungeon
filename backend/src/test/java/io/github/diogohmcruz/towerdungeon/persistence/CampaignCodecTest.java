package io.github.diogohmcruz.towerdungeon.persistence;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignCodec.Decoded;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema.SchemaNewerException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class CampaignCodecTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final GameProperties config = new GameProperties();

  private static byte[] golden(int schema) throws IOException {
    try (InputStream in =
        CampaignCodecTest.class.getResourceAsStream(
            "/persistence/golden/schema-" + schema + ".json")) {
      assertThat(in).as("golden fixture for schema %d", schema).isNotNull();
      return in.readAllBytes();
    }
  }

  private CampaignCodec codec() {
    return new CampaignCodec(config, mapper);
  }

  private CampaignCodec codec(int current, CampaignMigration... steps) {
    return new CampaignCodec(config, mapper, current, List.of(steps));
  }

  private ObjectNode goldenTree() throws IOException {
    return (ObjectNode) mapper.readTree(golden(1));
  }

  private byte[] bytes(JsonNode node) {
    return mapper.writeValueAsBytes(node);
  }

  @ParameterizedTest
  @ValueSource(ints = {1})
  void everyReleasedSchemaLoadsThroughTheMigrationChain(int schema) throws IOException {
    assertThat(schema).isLessThanOrEqualTo(CampaignMigrations.CURRENT);

    Decoded decoded = codec().decode(schema, golden(schema), CampaignCodec.PreMigrationSink.NONE);

    assertThat(decoded.campaignLost()).isFalse();
    assertThat(decoded.notices()).isEmpty();
    assertThat(decoded.snapshot().campaign().credit()).isEqualTo(250d);
    assertThat(decoded.snapshot().campaign().reserves()).hasSize(3);
    assertThat(decoded.snapshot().campaign().upgrades()).containsKey(UpgradeType.UNLOCK_HEALER);
    assertThat(decoded.meta().path("chronicle").asString()).isEqualTo("kept");
  }

  @Test
  void aReleasedSchemaFixtureIsPresentForEverySchemaUpToCurrent() throws IOException {
    for (int schema = 1; schema <= CampaignMigrations.CURRENT; schema++) {
      assertThat(golden(schema)).isNotEmpty();
    }
  }

  @Test
  void encodeThenDecodeGivesTheSameCampaignAndMeta() throws IOException {
    var first = codec().decode(1, golden(1), CampaignCodec.PreMigrationSink.NONE);

    byte[] payload = codec().encode(first.snapshot(), first.meta());
    var second = codec().decode(1, payload, CampaignCodec.PreMigrationSink.NONE);

    assertThat(second.snapshot()).isEqualTo(first.snapshot());
    assertThat(second.meta()).isEqualTo(first.meta());
    assertThat(mapper.readTree(payload).path("meta").path("chronicle").asString())
        .isEqualTo("kept");
  }

  @Test
  void aRecordOfANewerSchemaIsRefused() throws IOException {
    assertThatThrownBy(
            () ->
                codec()
                    .decode(
                        CampaignMigrations.CURRENT + 1,
                        golden(1),
                        CampaignCodec.PreMigrationSink.NONE))
        .isInstanceOf(SchemaNewerException.class);
  }

  @Test
  void stepsAreChainedAndACopyIsKeptBeforeEachOne() throws IOException {
    var rename =
        CampaignMigration.of(
            1,
            root -> {
              var campaign = (ObjectNode) root.get("campaign");
              campaign.set("credit", campaign.remove("gold"));
            });
    var addMeta = CampaignMigration.of(2, root -> ((ObjectNode) root.get("meta")).put("v3", true));
    var old = goldenTree();
    var campaign = (ObjectNode) old.get("campaign");
    campaign.set("gold", campaign.remove("credit"));
    byte[] original = bytes(old);
    List<Integer> keptSchemas = new ArrayList<>();
    List<byte[]> keptBytes = new ArrayList<>();

    Decoded decoded =
        codec(3, rename, addMeta)
            .decode(
                1,
                original,
                (schema, payload) -> {
                  keptSchemas.add(schema);
                  keptBytes.add(payload);
                });

    assertThat(decoded.campaignLost()).isFalse();
    assertThat(decoded.snapshot().campaign().credit()).isEqualTo(250d);
    assertThat(decoded.meta().path("v3").asBoolean()).isTrue();
    assertThat(keptSchemas).containsExactly(1, 2);
    assertThat(mapper.readTree(keptBytes.get(0))).isEqualTo(old);
    assertThat(mapper.readTree(keptBytes.get(1)).path("campaign").has("credit")).isTrue();
    assertThat(mapper.readTree(keptBytes.get(1)).path("meta").has("v3")).isFalse();
  }

  @Test
  void aStepThatFailsClearsTheCampaignButKeepsMetaWithANotice() throws IOException {
    var good = CampaignMigration.of(1, root -> ((ObjectNode) root.get("meta")).put("migrated", 1));
    var corrupt =
        CampaignMigration.of(
            2,
            root -> {
              ((ObjectNode) root.get("meta")).put("half", "written");
              throw new IllegalStateException("redesigned beyond repair");
            });
    List<Integer> kept = new ArrayList<>();

    Decoded decoded =
        codec(3, good, corrupt).decode(1, golden(1), (schema, payload) -> kept.add(schema));

    assertThat(decoded.campaignLost()).isTrue();
    assertThat(decoded.snapshot()).isNull();
    assertThat(decoded.notices()).contains("Campaign lost to a redesign. Legacy kept.");
    assertThat(decoded.meta().path("chronicle").asString()).isEqualTo("kept");
    assertThat(decoded.meta().path("migrated").asInt()).isEqualTo(1);
    assertThat(decoded.meta().has("half")).isFalse();
    assertThat(kept).containsExactly(1, 2);
  }

  @Test
  void aMissingStepCountsAsAMigrationThatCannotRun() throws IOException {
    Decoded decoded = codec(2).decode(1, golden(1), CampaignCodec.PreMigrationSink.NONE);

    assertThat(decoded.campaignLost()).isTrue();
    assertThat(decoded.meta().path("chronicle").asString()).isEqualTo("kept");
  }

  @Test
  void aCampaignThatFailsValidationIsLostButMetaIsKept() throws IOException {
    var tree = goldenTree();
    ((ObjectNode) tree.get("campaign")).put("highestFloor", "not a number");

    Decoded decoded = codec().decode(1, bytes(tree), CampaignCodec.PreMigrationSink.NONE);

    assertThat(decoded.campaignLost()).isTrue();
    assertThat(decoded.meta().path("chronicle").asString()).isEqualTo("kept");
  }

  @Test
  void aPayloadThatIsNotJsonIsLostWithEmptyMeta() {
    Decoded decoded =
        codec().decode(1, "not json at all".getBytes(UTF_8), CampaignCodec.PreMigrationSink.NONE);

    assertThat(decoded.campaignLost()).isTrue();
    assertThat(decoded.meta()).isEmpty();
  }

  @Test
  void unknownIdsAreDroppedWithAVisibleNotice() throws IOException {
    var tree = goldenTree();
    var campaign = (ObjectNode) tree.get("campaign");
    ((ObjectNode) campaign.get("upgrades")).put("UNLOCK_GOBLIN", 1);
    ((ObjectNode) campaign.get("vault")).put("GOLD", 3.0);
    ((ArrayNode) campaign.get("milestones")).add("GONE_MILESTONE");
    ((ArrayNode) campaign.get("shortcutsOpened")).add("GONE_SHORTCUT");
    var goblin = ((ArrayNode) campaign.get("reserves")).get(0).deepCopy();
    ((ObjectNode) goblin).put("type", "GOBLIN");
    ((ArrayNode) campaign.get("reserves")).add(goblin);
    ((ObjectNode) ((ArrayNode) campaign.get("reserves")).get(0).get("parts").get(0))
        .put("kind", "TAIL");
    int reservesBefore = campaign.get("reserves").size();

    Decoded decoded = codec().decode(1, bytes(tree), CampaignCodec.PreMigrationSink.NONE);

    assertThat(decoded.campaignLost()).isFalse();
    assertThat(decoded.snapshot().campaign().reserves()).hasSize(reservesBefore - 1);
    assertThat(decoded.snapshot().campaign().reserves().getFirst().parts()).hasSize(5);
    assertThat(decoded.snapshot().campaign().reserves())
        .noneMatch(unit -> unit.type() == null)
        .anyMatch(unit -> unit.type() == UnitStats.WARRIOR);
    assertThat(decoded.notices()).hasSize(1);
    assertThat(decoded.notices().getFirst())
        .contains(
            "UpgradeType.UNLOCK_GOBLIN",
            "ResourceType.GOLD",
            "MilestoneType.GONE_MILESTONE",
            "ShortcutType.GONE_SHORTCUT",
            "UnitStats.GOBLIN",
            "BodyPart.TAIL");
  }
}

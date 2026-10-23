package io.github.diogohmcruz.towerdungeon.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.prestige.ChronicleEntry;
import io.github.diogohmcruz.towerdungeon.domain.prestige.FallenName;
import io.github.diogohmcruz.towerdungeon.domain.prestige.PlayerMeta;
import tools.jackson.databind.json.JsonMapper;

class MetaCodecTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final MetaCodec codec = MetaCodec.development(mapper);

  @Test
  void aMetaRoundTripsAndTheSecretIsSealedInTheFile() {
    var meta =
        new PlayerMeta(
            3,
            7,
            List.of(),
            4.5,
            null,
            List.of(
                new ChronicleEntry(
                    2,
                    GameOutcome.DEFEAT,
                    12.5,
                    1,
                    18,
                    1,
                    4,
                    List.of(new FallenName("Calix", "ARCHER")))),
            "00112233445566778899aabbccddeeff");

    var node = codec.toNode(meta);

    assertThat(node.toString()).doesNotContain("00112233445566778899aabbccddeeff");
    assertThat(node.path("generation").asInt()).isEqualTo(3);
    assertThat(codec.fromNode(node)).isEqualTo(meta);
  }

  @Test
  void anEmptyOrMissingSectionGivesAFirstMetaWithNoSecretYet() {
    var fresh = codec.fromNode(mapper.createObjectNode());

    assertThat(fresh.generation()).isEqualTo(1);
    assertThat(fresh.hasSecret()).isFalse();
    assertThat(codec.fromNode(null)).isEqualTo(fresh);
  }

  @Test
  void aSectionOfAnOlderShapeKeepsWhatCanBeKept() {
    var node = mapper.createObjectNode();
    node.put("generation", 4);
    node.put("legacy", 9);
    node.put("chronicle", "kept");

    var meta = codec.fromNode(node);

    assertThat(meta.generation()).isEqualTo(4);
    assertThat(meta.legacy()).isEqualTo(9);
    assertThat(meta.chronicle()).isEmpty();
    assertThat(meta.hasSecret()).isFalse();
  }

  @Test
  void aSecretThatCannotBeOpenedIsDroppedAndTheRestIsKept() {
    var other = new MetaCodec(mapper, new Sealer("another-seal-key"));
    var sealedElsewhere = other.toNode(PlayerMeta.first("aaaa"));
    sealedElsewhere.put("legacy", 5);

    var meta = codec.fromNode(sealedElsewhere);

    assertThat(meta.legacy()).isEqualTo(5);
    assertThat(meta.hasSecret()).isFalse();
  }
}

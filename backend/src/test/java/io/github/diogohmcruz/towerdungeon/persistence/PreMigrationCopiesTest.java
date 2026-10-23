package io.github.diogohmcruz.towerdungeon.persistence;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Rotation;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Source;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PreMigrationCopiesTest {
  private static final String PLAYER = "ab0123456789abcdef0123456789abcd";

  @TempDir Path dataDir;
  private FileCampaignStore store;

  @BeforeEach
  void setUp() {
    store = new FileCampaignStore(dataDir, () -> {});
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  private static GameVersion version() {
    return new GameVersion(
        new org.springframework.beans.factory.support.DefaultListableBeanFactory()
            .getBeanProvider(org.springframework.boot.info.BuildProperties.class));
  }

  private Path playerDir() {
    return dataDir.resolve("players").resolve("ab").resolve(PLAYER);
  }

  private void save(long expectedSeq, Rotation rotation, String payload) {
    store.save(PLAYER, expectedSeq, rotation, 1, "0.1.0", payload.getBytes(UTF_8)).block();
  }

  @Test
  void aCopyIsKeptAsPreMigrationVn() throws IOException {
    store.savePreMigration(PLAYER, 3, "0.1.0", "before".getBytes(UTF_8)).block();

    Path copy = playerDir().resolve("pre-migration-v3.tdc");
    assertThat(copy).exists();
    var file = CampaignFile.decode(Files.readAllBytes(copy));
    assertThat(file.schema()).isEqualTo(3);
    assertThat(new String(file.payload(), UTF_8)).isEqualTo("before");
  }

  @Test
  void anExistingCopyOfTheSameSchemaIsNotOverwritten() {
    store.savePreMigration(PLAYER, 1, "0.1.0", "first".getBytes(UTF_8)).block();
    store.savePreMigration(PLAYER, 1, "0.1.0", "second".getBytes(UTF_8)).block();

    var loaded = store.load(PLAYER).block();
    assertThat(new String(loaded.payload(), UTF_8)).isEqualTo("first");
  }

  @Test
  void copiesSurviveSavesAndRotation() {
    store.savePreMigration(PLAYER, 1, "0.1.0", "old".getBytes(UTF_8)).block();
    for (int i = 0; i < 8; i++) {
      save(i, Rotation.ROTATE, "save " + i);
    }

    assertThat(playerDir().resolve("pre-migration-v1.tdc")).exists();
  }

  @Test
  void loadFallsBackToPreMigrationCopiesNewestSchemaFirstAfterTheBackups() throws IOException {
    store.savePreMigration(PLAYER, 1, "0.1.0", "v1".getBytes(UTF_8)).block();
    store.savePreMigration(PLAYER, 2, "0.1.0", "v2".getBytes(UTF_8)).block();
    save(0, Rotation.KEEP, "head");
    Files.write(playerDir().resolve("campaign.tdc"), "garbage".getBytes(UTF_8));

    var loaded = store.load(PLAYER).block();

    assertThat(new String(loaded.payload(), UTF_8)).isEqualTo("v2");
    assertThat(loaded.source()).isEqualTo(Source.preMigration(2));
    assertThat(loaded.source().isPreMigration()).isTrue();
    assertThat(loaded.quarantined()).containsExactly(Source.HEAD);
  }

  @Test
  void aBackupIsPreferredToAPreMigrationCopy() throws IOException {
    store.savePreMigration(PLAYER, 1, "0.1.0", "v1".getBytes(UTF_8)).block();
    save(0, Rotation.KEEP, "one");
    save(1, Rotation.ROTATE, "two");
    Files.write(playerDir().resolve("campaign.tdc"), "garbage".getBytes(UTF_8));

    var loaded = store.load(PLAYER).block();

    assertThat(new String(loaded.payload(), UTF_8)).isEqualTo("one");
    assertThat(loaded.source()).isEqualTo(new Source(1));
  }

  @Test
  void aCorruptCopyIsQuarantinedNotDeleted() throws IOException {
    store.savePreMigration(PLAYER, 1, "0.1.0", "v1".getBytes(UTF_8)).block();
    Files.write(playerDir().resolve("pre-migration-v1.tdc"), "garbage".getBytes(UTF_8));

    assertThat(store.load(PLAYER).block()).isNull();
    try (var files = Files.list(playerDir().resolve("corrupt"))) {
      assertThat(files.map(p -> p.getFileName().toString()).toList())
          .singleElement()
          .asString()
          .contains("pre-migration-v1");
    }
  }

  @Test
  void theLoaderMigratesWritesACopyFirstAndKeepsMetaWhenTheCampaignIsLost() throws IOException {
    var mapper = JsonMapper.builder().build();
    var failing =
        CampaignMigration.of(
            1,
            root -> {
              throw new IllegalStateException("cannot");
            });
    var codec = new CampaignCodec(new GameProperties(), mapper, 2, List.of(failing));
    var loader = new CampaignLoader(new MigratingCampaignStore(store, codec), codec);
    ObjectNode record = mapper.createObjectNode();
    record.putObject("campaign").put("credit", 1);
    record.putObject("meta").put("legacy", "kept");
    store.save(PLAYER, 0, Rotation.KEEP, 1, "0.1.0", mapper.writeValueAsBytes(record)).block();

    var loaded = loader.load(PLAYER).block();

    assertThat(loaded.stored()).isTrue();
    assertThat(loaded.snapshot()).isNull();
    // The legacy meta survives in the pre-migration copy.
    var copy = CampaignFile.decode(Files.readAllBytes(playerDir().resolve("pre-migration-v1.tdc")));
    assertThat(new String(copy.payload(), UTF_8)).contains("\"legacy\":\"kept\"");
    assertThat(loaded.notices()).contains("Campaign lost to a redesign. Legacy kept.");
    assertThat(loaded.seq()).isEqualTo(1);
    assertThat(playerDir().resolve("pre-migration-v1.tdc")).exists();
  }

  @Test
  void theLoaderReportsAPlayerWithNothingStored() {
    var codec = new CampaignCodec(new GameProperties(), JsonMapper.builder().build());
    var loader = new CampaignLoader(new MigratingCampaignStore(store, codec), codec);

    var loaded = loader.load(PLAYER).block();

    assertThat(loaded.stored()).isFalse();
    assertThat(loaded.snapshot()).isNull();
    assertThat(loaded.seq()).isEqualTo(CampaignStore.INITIAL_SEQ);
  }

  @Test
  void theLoaderPassesEachMigrationStepABeforeCopy() {
    var mapper = JsonMapper.builder().build();
    List<String> order = new ArrayList<>();
    var step =
        CampaignMigration.of(
            1,
            root -> {
              order.add(
                  "migrate; copy exists = "
                      + Files.exists(playerDir().resolve("pre-migration-v1.tdc")));
              ((ObjectNode) root.get("campaign")).put("credit", 9);
            });
    var codec = new CampaignCodec(new GameProperties(), mapper, 2, List.of(step));
    var loader = new CampaignLoader(new MigratingCampaignStore(store, codec), codec);
    store
        .save(
            PLAYER,
            0,
            Rotation.KEEP,
            1,
            "0.1.0",
            "{\"campaign\":{\"credit\":1},\"meta\":{}}".getBytes(UTF_8))
        .block();

    loader.load(PLAYER).block();

    assertThat(order).containsExactly("migrate; copy exists = true");
  }
}

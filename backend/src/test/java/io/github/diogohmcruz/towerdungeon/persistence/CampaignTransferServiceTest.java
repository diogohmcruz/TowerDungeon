package io.github.diogohmcruz.towerdungeon.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.ToLongFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignTransferService.ExpeditionInProgressException;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class CampaignTransferServiceTest {
  private static final String PLAYER = "ab0123456789abcdef0123456789abcd";

  @TempDir Path dataDir;
  private final GameProperties config = new GameProperties();
  private FileCampaignStore store;
  private CampaignCodec codec;
  private CampaignTransferService service;
  private boolean idle = true;
  private GameSnapshot installed;

  private final LiveCampaigns live =
      new LiveCampaigns() {
        @Override
        public Optional<GameSnapshot> snapshot(String playerId) {
          return Optional.ofNullable(installed);
        }

        @Override
        public boolean replaceIfIdle(
            String playerId, GameSnapshot snapshot, ToLongFunction<OptionalLong> persist) {
          if (!idle) {
            return false;
          }
          persist.applyAsLong(OptionalLong.empty());
          installed = snapshot;
          return true;
        }
      };

  @BeforeEach
  void setUp() {
    store = new FileCampaignStore(dataDir, () -> {});
    codec = new CampaignCodec(config, JsonMapper.builder().build());
    var version =
        new GameVersion(new DefaultListableBeanFactory().getBeanProvider(BuildProperties.class));
    service =
        new CampaignTransferService(new CampaignLoader(store, codec), store, codec, live, version);
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  private byte[] fileOf(double credit) {
    var state = new GameState(config);
    state.setCredit(credit);
    return CampaignFile.encode(
        1,
        1,
        Instant.parse("2026-01-01T00:00:00Z"),
        "0.1.0",
        codec.encode(state.toSnapshot(), null));
  }

  private Path playerDir() {
    return dataDir.resolve("players").resolve("ab").resolve(PLAYER);
  }

  @Test
  void anActiveOrHeldExpeditionRefusesTheImportAndWritesNothing() {
    idle = false;

    StepVerifier.create(service.importFile(PLAYER, fileOf(5)))
        .expectError(ExpeditionInProgressException.class)
        .verify();

    assertThat(playerDir()).doesNotExist();
    assertThat(installed).isNull();
  }

  @Test
  void aSecondImportCanBeUndoneThroughBackupOne() throws Exception {
    service.importFile(PLAYER, fileOf(10)).block();
    service.importFile(PLAYER, fileOf(20)).block();

    var backup = CampaignFile.decode(Files.readAllBytes(playerDir().resolve("backup/1.tdc")));
    var previous =
        codec.decode(backup.schema(), backup.payload(), CampaignCodec.PreMigrationSink.NONE);
    assertThat(previous.snapshot().campaign().credit()).isEqualTo(10);

    // Importing the previous Campaign again brings it back.
    service.importFile(PLAYER, fileOf(10)).block();
    assertThat(installed.campaign().credit()).isEqualTo(10);
  }

  @Test
  void theProductionProfileStopsStartupWithoutASealKey() {
    var persistence = new PersistenceConfig();
    var production = new MockEnvironment();
    production.setActiveProfiles("prod");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> persistence.sealer(config, production))
        .isInstanceOf(IllegalStateException.class);
    assertThat(persistence.sealer(config, new MockEnvironment())).isNotNull();

    config.getPersistence().setSealKey("a stable key");
    assertThat(persistence.sealer(config, production)).isNotNull();
  }
}

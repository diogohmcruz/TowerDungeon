package io.github.diogohmcruz.towerdungeon.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Rotation;

class FileCampaignStoreWriterThreadTest {
  @TempDir Path dir;

  @Test
  void aPayloadSupplierRunsOnTheWriterThreadNotTheCaller() {
    var ranOn = new AtomicReference<String>();
    try (var store = new FileCampaignStore(dir, () -> {})) {
      var seq =
          store
              .save(
                  "ab0123456789abcdef0123456789abcd",
                  CampaignStore.INITIAL_SEQ,
                  Rotation.KEEP,
                  1,
                  "test",
                  () -> {
                    ranOn.set(Thread.currentThread().getName());
                    return new byte[] {1};
                  })
              .block();

      assertThat(seq).isEqualTo(1L);
      assertThat(ranOn.get()).startsWith("campaign-writer");
    }
  }
}

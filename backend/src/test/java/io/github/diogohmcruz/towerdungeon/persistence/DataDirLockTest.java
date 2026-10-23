package io.github.diogohmcruz.towerdungeon.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataDirLockTest {
  @TempDir Path dir;

  @Test
  void aSecondInstanceOnTheSameDataDirectoryIsRefused() throws Exception {
    try (var first = new DataDirLock(dir)) {
      assertThat(Files.exists(dir.resolve(".lock"))).isTrue();

      assertThatThrownBy(() -> new DataDirLock(dir))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Another instance");
    }
  }

  @Test
  void theLockIsFreeAgainOnceReleased() throws Exception {
    new DataDirLock(dir).close();

    try (var again = new DataDirLock(dir)) {
      assertThat(again).isNotNull();
    }
  }
}

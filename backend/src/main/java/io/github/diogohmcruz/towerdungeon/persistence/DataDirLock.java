package io.github.diogohmcruz.towerdungeon.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Holds {@code <data-dir>/.lock} for the life of the instance so a second instance on the same data
 * directory refuses to start instead of racing the first one's writes.
 */
public class DataDirLock implements AutoCloseable {
  private final FileChannel channel;
  private final FileLock lock;

  /** Takes the lock, or fails with {@link IllegalStateException} when another instance holds it. */
  public DataDirLock(Path dataDir) {
    var file = dataDir.resolve(".lock");
    try {
      Files.createDirectories(dataDir);
      channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      FileLock acquired;
      try {
        acquired = channel.tryLock();
      } catch (OverlappingFileLockException e) {
        acquired = null;
      }
      if (acquired == null) {
        channel.close();
        throw new IllegalStateException(
            "Another instance is using the data directory " + dataDir.toAbsolutePath());
      }
      lock = acquired;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot lock the data directory " + dataDir, e);
    }
  }

  @Override
  public void close() throws IOException {
    lock.release();
    channel.close();
  }
}

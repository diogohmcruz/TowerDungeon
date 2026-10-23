package io.github.diogohmcruz.towerdungeon.persistence;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.LoadResult;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Rotation;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Source;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class FileCampaignStoreTest {
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

  private Path playerDir() {
    return dataDir.resolve("players").resolve("ab").resolve(PLAYER);
  }

  private Path head() {
    return playerDir().resolve("campaign.tdc");
  }

  private Path backup(int n) {
    return playerDir().resolve("backup").resolve(n + ".tdc");
  }

  private long save(long expectedSeq, Rotation rotation, String payload) {
    return store.save(PLAYER, expectedSeq, rotation, 1, "0.1.0", payload.getBytes(UTF_8)).block();
  }

  private static String text(LoadResult result) {
    return new String(result.payload(), UTF_8);
  }

  private void flipLastByte(Path file) throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    bytes[bytes.length - 1] ^= 1;
    Files.write(file, bytes);
  }

  private long countFiles(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return 0;
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.count();
    }
  }

  @Test
  void saveThenLoadReturnsTheSameRecordFromTheHead() {
    StepVerifier.create(store.save(PLAYER, 0, Rotation.KEEP, 7, "1.2.3", "hello".getBytes(UTF_8)))
        .expectNext(1L)
        .verifyComplete();

    StepVerifier.create(store.load(PLAYER))
        .assertNext(
            result -> {
              assertThat(text(result)).isEqualTo("hello");
              assertThat(result.schema()).isEqualTo(7);
              assertThat(result.gameVersion()).isEqualTo("1.2.3");
              assertThat(result.seq()).isEqualTo(1);
              assertThat(result.savedAt()).isNotNull();
              assertThat(result.source()).isEqualTo(Source.HEAD);
              assertThat(result.quarantined()).isEmpty();
            })
        .verifyComplete();
  }

  @Test
  void payloadMayBeBinaryAndContainNewlines() {
    byte[] payload = {0, 10, 13, 10, (byte) 255, 10};
    store.save(PLAYER, 0, Rotation.KEEP, 1, "v", payload).block();

    assertThat(store.load(PLAYER).block().payload()).isEqualTo(payload);
  }

  @Test
  void loadOfAnUnknownPlayerIsEmpty() {
    StepVerifier.create(store.load(PLAYER)).verifyComplete();
  }

  @Test
  void failureBeforeTheRenameLeavesThePreviousHeadIntact() throws IOException {
    save(0, Rotation.KEEP, "first");
    byte[] before = Files.readAllBytes(head());
    store.close();
    store =
        new FileCampaignStore(
            dataDir,
            () -> {
              throw new UncheckedIOException(new IOException("disk gone"));
            });

    StepVerifier.create(store.save(PLAYER, 1, Rotation.ROTATE, 1, "v", "second".getBytes(UTF_8)))
        .expectError(UncheckedIOException.class)
        .verify();

    assertThat(Files.readAllBytes(head())).isEqualTo(before);
    assertThat(Files.exists(backup(1))).isFalse();
    try (Stream<Path> files = Files.list(playerDir())) {
      assertThat(files.map(p -> p.getFileName().toString())).containsExactly("campaign.tdc");
    }
    LoadResult loaded = store.load(PLAYER).block();
    assertThat(text(loaded)).isEqualTo("first");
    assertThat(loaded.seq()).isEqualTo(1);
  }

  @Test
  void corruptHeadFallsBackToBackupOneAndIsQuarantined() throws IOException {
    save(0, Rotation.KEEP, "one");
    save(1, Rotation.ROTATE, "two");
    flipLastByte(head());

    LoadResult result = store.load(PLAYER).block();

    assertThat(text(result)).isEqualTo("one");
    assertThat(result.source()).isEqualTo(new Source(1));
    assertThat(result.quarantined()).containsExactly(Source.HEAD);
    assertThat(Files.exists(head())).isFalse();
    assertThat(countFiles(playerDir().resolve("corrupt"))).isEqualTo(1);
  }

  @Test
  void headAndBackupOneCorruptFallBackToBackupTwo() throws IOException {
    save(0, Rotation.KEEP, "a");
    save(1, Rotation.ROTATE, "b");
    save(2, Rotation.ROTATE, "c");
    save(3, Rotation.ROTATE, "d");
    flipLastByte(head());
    flipLastByte(backup(1));

    LoadResult result = store.load(PLAYER).block();

    assertThat(text(result)).isEqualTo("b");
    assertThat(result.source()).isEqualTo(new Source(2));
    assertThat(result.quarantined()).containsExactly(Source.HEAD, new Source(1));
  }

  @Test
  void truncatedAndGarbageFilesAreCorrupt() throws IOException {
    save(0, Rotation.KEEP, "payload");
    Files.write(head(), "TDC1 schema=1".getBytes(UTF_8));

    StepVerifier.create(store.load(PLAYER)).verifyComplete();
    assertThat(countFiles(playerDir().resolve("corrupt"))).isEqualTo(1);
  }

  @Test
  void everythingCorruptYieldsEmptyAndDeletesNothing() throws IOException {
    for (int i = 0; i < 4; i++) {
      save(i, Rotation.ROTATE, "v" + i);
    }
    flipLastByte(head());
    for (int n = 1; n <= 3; n++) {
      flipLastByte(backup(n));
    }

    StepVerifier.create(store.load(PLAYER)).verifyComplete();

    assertThat(countFiles(playerDir().resolve("corrupt"))).isEqualTo(3);
    assertThat(Files.exists(head())).isFalse();
  }

  @Test
  void corruptFolderKeepsAtMostThreeFiles() throws IOException {
    for (int round = 0; round < 5; round++) {
      save(0, Rotation.KEEP, "x");
      flipLastByte(head());
      store.load(PLAYER).block();
      Files.deleteIfExists(backup(1));
      assertThat(countFiles(playerDir().resolve("corrupt"))).isLessThanOrEqualTo(3);
    }
    assertThat(countFiles(playerDir().resolve("corrupt"))).isEqualTo(3);
  }

  @Test
  void rotateShiftsBackupsAndDropsTheOldestAfterFiveRotations() throws IOException {
    long seq = 0;
    for (int i = 1; i <= 7; i++) {
      seq = save(seq, Rotation.ROTATE, "v" + i);
    }

    assertThat(text(store.load(PLAYER).block())).isEqualTo("v7");
    for (int n = 1; n <= 5; n++) {
      String expected = "v" + (7 - n);
      assertThat(Files.readString(backup(n), UTF_8)).endsWith(expected);
    }
    assertThat(countFiles(playerDir().resolve("backup"))).isEqualTo(5);
  }

  @Test
  void keepChangesNoBackup() throws IOException {
    long seq = save(0, Rotation.KEEP, "v1");
    seq = save(seq, Rotation.ROTATE, "v2");
    byte[] backupBefore = Files.readAllBytes(backup(1));

    save(seq, Rotation.KEEP, "v3");

    assertThat(Files.readAllBytes(backup(1))).isEqualTo(backupBefore);
    assertThat(countFiles(playerDir().resolve("backup"))).isEqualTo(1);
    assertThat(text(store.load(PLAYER).block())).isEqualTo("v3");
  }

  @Test
  void wrongExpectedSeqIsAConflictAndChangesNothing() throws IOException {
    save(0, Rotation.KEEP, "v1");
    byte[] before = Files.readAllBytes(head());

    StepVerifier.create(store.save(PLAYER, 5, Rotation.ROTATE, 1, "v", "x".getBytes(UTF_8)))
        .expectErrorSatisfies(
            e ->
                assertThat(e)
                    .isInstanceOfSatisfying(
                        CampaignConflictException.class,
                        c -> {
                          assertThat(c.getExpectedSeq()).isEqualTo(5);
                          assertThat(c.getActualSeq()).isEqualTo(1);
                        }))
        .verify();
    StepVerifier.create(store.save(PLAYER, 0, Rotation.KEEP, 1, "v", "x".getBytes(UTF_8)))
        .expectError(CampaignConflictException.class)
        .verify();

    assertThat(Files.readAllBytes(head())).isEqualTo(before);
    assertThat(countFiles(playerDir())).isEqualTo(1);
  }

  @Test
  void aFirstSaveAgainstANonInitialSeqIsAConflict() {
    StepVerifier.create(store.save(PLAYER, 3, Rotation.KEEP, 1, "v", new byte[0]))
        .expectError(CampaignConflictException.class)
        .verify();
    assertThat(Files.exists(dataDir.resolve("players"))).isFalse();
  }

  @Test
  void aCampaignRecoveredFromABackupCanBeSavedAgain() throws IOException {
    save(0, Rotation.KEEP, "one");
    save(1, Rotation.ROTATE, "two");
    flipLastByte(head());
    LoadResult loaded = store.load(PLAYER).block();

    long seq = save(loaded.seq(), Rotation.KEEP, "three");

    assertThat(seq).isEqualTo(2);
    assertThat(text(store.load(PLAYER).block())).isEqualTo("three");
  }

  @Test
  void invalidPlayerIdsAreRejectedWithoutTouchingTheFileSystem() {
    List<String> invalid =
        List.of(
            "../../etc/passwd",
            "AB0123456789abcdef0123456789abcd",
            "ab0123456789abcdef0123456789abc",
            "ab0123456789abcdef0123456789abcde",
            "ab0123456789abcdef0123456789abc/",
            "zz0123456789abcdef0123456789abcd",
            "");
    for (String id : invalid) {
      StepVerifier.create(store.save(id, 0, Rotation.KEEP, 1, "v", new byte[0]))
          .expectError(IllegalArgumentException.class)
          .verify();
      StepVerifier.create(store.load(id)).expectError(IllegalArgumentException.class).verify();
    }
    StepVerifier.create(store.load(null)).expectError(IllegalArgumentException.class).verify();
    assertThat(dataDir).isEmptyDirectory();
  }

  @Test
  void quickSavesForOnePlayerAreAppliedInOrder() {
    Mono<Long> first = store.save(PLAYER, 0, Rotation.KEEP, 1, "v", "first".getBytes(UTF_8));
    Mono<Long> second = store.save(PLAYER, 1, Rotation.KEEP, 1, "v", "second".getBytes(UTF_8));
    Mono<Long> third = store.save(PLAYER, 2, Rotation.KEEP, 1, "v", "third".getBytes(UTF_8));

    StepVerifier.create(Mono.zip(first.doOnSubscribe(s -> {}), Mono.defer(() -> second), third))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(text(store.load(PLAYER).block())).isEqualTo("third");
  }

  @Test
  void savesSubscribedBackToBackNeverReorder() {
    Mono<Long> first = store.save(PLAYER, 0, Rotation.KEEP, 1, "v", "first".getBytes(UTF_8));
    Mono<Long> second = store.save(PLAYER, 1, Rotation.KEEP, 1, "v", "second".getBytes(UTF_8));
    first.subscribe();
    StepVerifier.create(second).expectNext(2L).verifyComplete();
    assertThat(text(store.load(PLAYER).block())).isEqualTo("second");
  }
}

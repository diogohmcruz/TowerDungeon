package io.github.diogohmcruz.towerdungeon.domain.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import tools.jackson.databind.json.JsonMapper;

class TermsRegistryTest {
  private final GameProperties config = new GameProperties();
  private final SeedSource seeds = new SeedSource("a server secret");

  private GameProperties.Trial.Race race(String id) {
    var race = new GameProperties.Trial.Race();
    race.setRaceId(id);
    config.getTrial().getRaces().add(race);
    return race;
  }

  @Test
  void aRaceIsBuiltFromConfigurationWithADerivedSeedAndTheRunningBalance() {
    race("season-1");

    var terms = new TermsRegistry(config, seeds).find("season-1").orElseThrow();

    assertThat(terms.kind()).isEqualTo(TermsKind.SEASON);
    assertThat(terms.finishFloor()).isEqualTo(10);
    assertThat(terms.maxAttempts()).isEqualTo(3);
    assertThat(terms.limit()).isEqualTo(Duration.ofMinutes(90));
    assertThat(terms.configHash()).isEqualTo(ConfigHash.of(config));
    assertThat(terms.seed()).isEqualTo(seeds.termsSeedOf("season-1"));
  }

  @Test
  void theSeedOfARaceIsStableAndDiffersPerRaceAndSecret() {
    assertThat(seeds.termsSeedOf("a"))
        .isEqualTo(new SeedSource("a server secret").termsSeedOf("a"));
    assertThat(seeds.termsSeedOf("a")).isNotEqualTo(seeds.termsSeedOf("b"));
    assertThat(seeds.termsSeedOf("a")).isNotEqualTo(new SeedSource("another").termsSeedOf("a"));
  }

  @Test
  void theSeedNeverShowsInTheTextOfTheTerms() {
    var race = race("season-1");
    race.setSeed(987654321012L);

    var terms = new TermsRegistry(config, seeds).find("season-1").orElseThrow();

    assertThat(terms.seed()).isEqualTo(987654321012L);
    assertThat(terms.toString()).doesNotContain("987654321012").doesNotContain("seed");
  }

  @Test
  void aStatedBalanceHashIsKeptAsWritten() {
    race("season-1").setConfigHash("0123456789abcdef");

    assertThat(new TermsRegistry(config, seeds).find("season-1").orElseThrow().configHash())
        .isEqualTo("0123456789abcdef");
  }

  @Test
  void termsThatCannotBeHonouredStopTheServerStarting() {
    race("../etc");
    assertThatThrownBy(() -> new TermsRegistry(config, seeds))
        .isInstanceOf(IllegalStateException.class);
    config.getTrial().getRaces().clear();

    race("off-the-tower").setFinishFloor(101);
    assertThatThrownBy(() -> new TermsRegistry(config, seeds)).hasMessageContaining("Finish Floor");
    config.getTrial().getRaces().clear();

    race("too-long").setLimit(Duration.ofMinutes(91));
    assertThatThrownBy(() -> new TermsRegistry(config, seeds)).hasMessageContaining("limit");
    config.getTrial().getRaces().clear();

    race("twice");
    race("twice");
    assertThatThrownBy(() -> new TermsRegistry(config, seeds)).hasMessageContaining("twice");
  }

  @Test
  void entryOpensAndClosesOnTheTerms() {
    var open = Instant.parse("2026-11-01T00:00:00Z");
    var close = Instant.parse("2026-11-08T00:00:00Z");
    var terms =
        new Terms("r", TermsKind.SEASON, 1, "h", 1, 10, open, close, Duration.ofMinutes(5), 3, 20);

    assertThat(terms.isOpenAt(open.minusSeconds(1))).isFalse();
    assertThat(terms.isOpenAt(open)).isTrue();
    assertThat(terms.isOpenAt(close)).isFalse();
  }

  @Test
  void theBalanceHashChangesWithTheBalanceAndNotWithWhereCampaignsAreKept() {
    var before = ConfigHash.of(config);

    config.getPersistence().setDataDir("/elsewhere");
    config.getSecurity().setRequireWss(true);
    race("season-1");
    assertThat(ConfigHash.of(config)).isEqualTo(before);
    assertThat(ConfigHash.of(config.copy())).isEqualTo(before);

    config.getEnemies().setBaseBudget(config.getEnemies().getBaseBudget() + 0.5);
    assertThat(ConfigHash.of(config)).isNotEqualTo(before);
    assertThat(before).matches("[0-9a-f]{16}");
  }

  @Test
  void theBalanceHashChangesWhenTheStartKitDoes() {
    var before = ConfigHash.of(config);

    config.getStart().setCredit(11.0);

    assertThat(ConfigHash.of(config)).isNotEqualTo(before);
  }

  @Test
  void theAttemptsRecordSurvivesARestartAndNeverGrantsAnExtra(@TempDir Path dir) {
    var mapper = JsonMapper.builder().build();
    var ledger = new AttemptLedger(dir, mapper);
    assertThat(ledger.start("r", "p")).isEqualTo(1);
    assertThat(ledger.start("r", "p")).isEqualTo(2);
    ledger.close("r", "p", 2, ContenderStatus.FINISHED, 77);
    ledger.close("r", "p", 1, ContenderStatus.TIMED_OUT, 5);

    var reopened = new AttemptLedger(dir, mapper);

    assertThat(reopened.standing("r", "p").started()).isEqualTo(2);
    assertThat(ledger.standing("r", "p").currentClosed()).isTrue();
    assertThat(reopened.standing("r", "other").started()).isZero();
    assertThat(reopened.standing("elsewhere", "p").started()).isZero();
  }
}

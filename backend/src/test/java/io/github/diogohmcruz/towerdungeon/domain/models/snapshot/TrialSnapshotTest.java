package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderStatus;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialRun;
import tools.jackson.databind.json.JsonMapper;

class TrialSnapshotTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final GameProperties config = new GameProperties();

  private GameState trial() {
    var state = new GameState(config, RandomSource.forTrial(99L, 5L));
    var run = new TrialRun("season-1", 2, 10, 5400);
    run.tick();
    run.tick();
    state.setTrial(run);
    return state;
  }

  @Test
  void aTrialSurvivesBeingSavedAndLoaded() throws Exception {
    var saved = trial();

    var json = mapper.writeValueAsString(saved.toSnapshot());
    var restored =
        GameState.restore(mapper.readValue(json, GameSnapshot.class), config, new RandomSource(0));

    var run = restored.getTrial();
    assertThat(run.getRaceId()).isEqualTo("season-1");
    assertThat(run.getAttempt()).isEqualTo(2);
    assertThat(run.getFinishFloor()).isEqualTo(10);
    assertThat(run.getLimitTicks()).isEqualTo(5400);
    assertThat(run.getTicksSinceStart()).isEqualTo(2);
    assertThat(run.getStatus()).isEqualTo(ContenderStatus.RACING);
    assertThat(restored.getRandom().isPinned()).isTrue();
    assertThat(restored.toSnapshot()).isEqualTo(saved.toSnapshot());
  }

  @Test
  void aClosedTrialStaysClosed() throws Exception {
    var saved = trial();
    saved.getTrial().finish();

    var json = mapper.writeValueAsString(saved.toSnapshot());
    var restored =
        GameState.restore(mapper.readValue(json, GameSnapshot.class), config, new RandomSource(0));

    assertThat(restored.isTrialClosed()).isTrue();
    assertThat(restored.getTrial().getStatus()).isEqualTo(ContenderStatus.FINISHED);
  }

  @Test
  void aSaveFromBeforeTrialsLoadsAsARealCampaign() throws Exception {
    var json = mapper.writeValueAsString(new GameState(config).toSnapshot());
    // Older saves have neither a trial nor a tower seed.
    var older = json.replace(",\"trial\":null", "").replace(",\"towerSeed\":null", "");
    assertThat(older).doesNotContain("trial").doesNotContain("towerSeed");

    var restored =
        GameState.restore(mapper.readValue(older, GameSnapshot.class), config, new RandomSource(0));

    assertThat(restored.getTrial()).isNull();
    assertThat(restored.getRandom().isPinned()).isFalse();
  }

  @Test
  void aRealCampaignsFrameCarriesNoTrial() throws Exception {
    assertThat(mapper.writeValueAsString(new GameState(config))).doesNotContain("\"trial\"");
  }
}

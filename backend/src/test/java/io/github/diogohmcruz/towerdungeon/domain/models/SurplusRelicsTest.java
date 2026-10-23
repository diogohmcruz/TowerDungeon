package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;

class SurplusRelicsTest {
  private final GameProperties config = new GameProperties();
  private final GameState state = new GameState(config);

  private double relics() {
    return state.getResources().getOrDefault(ResourceType.RELICS, 0d);
  }

  @Test
  void aLeftoverIsWorthOneRelicAndATalismanThree() {
    var value = config.getItems().getSurplusRelics();
    assertThat(value.of(ItemTier.LEFTOVER)).isEqualTo(1);
    assertThat(value.of(ItemTier.TALISMAN)).isEqualTo(3);
    assertThat(value.of(ItemTier.ARTIFACT)).isZero();
  }

  @Test
  void aSurplusTalismanBanksThreeRelics() {
    state.addSurplus(ItemTier.TALISMAN);
    state.addSurplus(ItemTier.LEFTOVER);

    state.bankLoot();

    assertThat(relics()).isEqualTo(4d);
  }

  @Test
  void extractionBanksEverySurplusItemAsRelics() {
    state.addSurplus(ItemTier.LEFTOVER);
    state.addSurplus(ItemTier.LEFTOVER);
    state.addSurplus(ItemTier.LEFTOVER);

    state.bankLoot();

    assertThat(relics()).isEqualTo(3d);
    assertThat(state.getSurplus()).isEmpty();
  }

  @Test
  void surplusRelicsAddToRelicsAlreadyBanked() {
    state.getResources().put(ResourceType.RELICS, 5d);
    state.addSurplus(ItemTier.LEFTOVER);

    state.bankLoot();

    assertThat(relics()).isEqualTo(6d);
  }

  @Test
  void aWipeConvertsNothing() {
    state.addSurplus(ItemTier.LEFTOVER);
    state.addSurplus(ItemTier.LEFTOVER);

    state.forfeitLoot();

    assertThat(relics()).isZero();
    assertThat(state.getSurplus()).isEmpty();
  }

  @Test
  void theRunSummaryCountsTheRelicsTheSurplusWillBank() {
    state.addSurplus(ItemTier.LEFTOVER);
    state.addSurplus(ItemTier.LEFTOVER);

    state.buildRunSummary(RunSummary.EXTRACTED);

    assertThat(state.getLastRunSummary().relicsGained()).isEqualTo(2d);
  }

  @Test
  void aWipedRunSummaryCountsNoSurplusRelics() {
    state.addSurplus(ItemTier.LEFTOVER);

    state.buildRunSummary(RunSummary.WIPED);

    assertThat(state.getLastRunSummary().relicsGained()).isZero();
  }
}

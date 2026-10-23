package io.github.diogohmcruz.towerdungeon.domain.prestige;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

/** The numbers behind Courses I and II come from config, and a reloaded Campaign keeps them. */
class RollEffectsTest {
  private static List<RollEntry> roll(InheritanceType... types) {
    return java.util.Arrays.stream(types).map(type -> new RollEntry(type.name(), 1)).toList();
  }

  @Test
  void theStartValuesAreConfigNotConstants() {
    var config = new GameProperties().copy();
    var numbers = config.getPrestige().getRoll();
    numbers.getSeedGrain().setFood(222);
    numbers.getSeedGrain().setCredits(33);
    numbers.getHardyStock().setVillagers(31);
    numbers.getHeavierPacks().setCapacity(40);
    var base = config.getSupply().getBaseMax();

    RollEffects.of(
            roll(
                InheritanceType.SEED_GRAIN,
                InheritanceType.HARDY_STOCK,
                InheritanceType.HEAVIER_PACKS),
            config)
        .shape(config);

    assertThat(config.getVillage().getStartingFood()).isEqualTo(222);
    assertThat(config.getStart().getCredit()).isEqualTo(33);
    assertThat(config.getVillage().getStartingVillagers()).isEqualTo(31);
    assertThat(config.getSupply().getBaseMax()).isEqualTo(base + 40);
  }

  @Test
  void aRollNeverLowersWhatTheConfigAlreadyGives() {
    var config = new GameProperties().copy();
    config.getVillage().setStartingFood(500);
    config.getVillage().setStartingVillagers(40);
    config.getStart().setCredit(90);

    RollEffects.of(roll(InheritanceType.SEED_GRAIN, InheritanceType.HARDY_STOCK), config)
        .shape(config);

    assertThat(config.getVillage().getStartingFood()).isEqualTo(500);
    assertThat(config.getVillage().getStartingVillagers()).isEqualTo(40);
    assertThat(config.getStart().getCredit()).isEqualTo(90);
  }

  @Test
  void anUnavailableOrUnknownEntryHasNoEffect() {
    var config = new GameProperties().copy();
    var effects =
        RollEffects.of(
            List.of(new RollEntry("READER_OF_OMENS", 4), new RollEntry("LONG_GONE", 3)), config);

    assertThat(effects.holds(InheritanceType.READER_OF_OMENS)).isFalse();
  }

  @Test
  void theDamageBonusIsReappliedToALoadedCampaign() {
    var config = new GameProperties().copy();
    config.getPrestige().getRoll().getWhetstoneHabit().setDamageBonus(0.2);
    var state = new GameState(config, new RandomSource(1));
    var restored = GameState.restore(state.toSnapshot(), config, new RandomSource(1), null);
    assertThat(restored.getDamageMultiplier()).isEqualTo(1.0);

    RollEffects.of(roll(InheritanceType.WHETSTONE_HABIT), config).carry(restored);

    assertThat(restored.getDamageMultiplier()).isEqualTo(1.2);
  }

  @Test
  void theHeirsHealthMultiplierIsConfig() {
    var config = new GameProperties().copy();
    config.getPrestige().getRoll().getHeir().setHealthMultiplier(2.0);
    config.getRecruit().setStrengthSpread(0);
    var state = new GameState(config, new RandomSource(1));

    RollEffects.of(roll(InheritanceType.THE_HEIR), config)
        .start(state, Map.of("HEIR", "HEIR_ARCHER"));

    var heir = state.getUnits().get(UnitStats.ARCHER).getFirst();
    assertThat(heir.getMaxHealth()).isEqualTo(UnitStats.ARCHER.getHealth() * 2.0);
  }

  @Test
  void aChoiceFromTheWrongQuestionIsIgnored() {
    var config = new GameProperties().copy();
    var state = new GameState(config, new RandomSource(1));

    RollEffects.of(roll(InheritanceType.THE_HEIR), config)
        .start(state, Map.of("HEIR", "DOWRY_RELICS"));

    assertThat(state.getUnits().get(UnitStats.WARRIOR)).hasSize(1);
  }
}

package io.github.diogohmcruz.towerdungeon.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;

class GamePropertiesBindingTest {
  private static GameProperties bound() throws IOException {
    var sources = new MutablePropertySources();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .forEach(sources::addLast);
    return new Binder(ConfigurationPropertySources.from(sources))
        .bind("game", Bindable.of(GameProperties.class))
        .get();
  }

  @Test
  void theShippedEnemyPartRangesMatchTheDefaults() throws IOException {
    var shipped = bound().getEnemies();
    var defaults = new GameProperties().getEnemies();

    for (var type : EnemyStats.values()) {
      assertThat(shipped.partsOf(type)).as(type.name()).isEqualTo(defaults.partsOf(type));
    }
    assertThat(shipped.partsOf(EnemyStats.DEAD_SOLDIERS).getMin()).isEqualTo(2);
    assertThat(shipped.partsOf(EnemyStats.CHIMERA).getMin()).isEqualTo(4);
  }

  @Test
  void enemiesDivideTheirHealthByTheSameWeightsAsUnits() throws IOException {
    var config = bound();

    assertThat(config.getEnemies().getWeights()).isSameAs(config.getBody().getWeights());
  }

  @Test
  void theShippedRollMatchesTheDefaults() throws IOException {
    var shipped = bound().getPrestige().getRoll();
    var defaults = new GameProperties().getPrestige().getRoll();

    assertThat(shipped.getGates()).isEqualTo(defaults.getGates()).containsExactly(0, 5, 12, 21);
    assertThat(shipped.getCosts()).isEqualTo(defaults.getCosts()).hasSize(17);
    assertThat(shipped.getSeedGrain()).isEqualTo(defaults.getSeedGrain());
    assertThat(shipped.getHardyStock()).isEqualTo(defaults.getHardyStock());
    assertThat(shipped.getWhetstoneHabit()).isEqualTo(defaults.getWhetstoneHabit());
    assertThat(shipped.getDowry()).isEqualTo(defaults.getDowry());
    assertThat(shipped.getHeavierPacks()).isEqualTo(defaults.getHeavierPacks());
    assertThat(shipped.getVillageCharter()).isEqualTo(defaults.getVillageCharter());
    assertThat(shipped.getHeir()).isEqualTo(defaults.getHeir());
  }

  @Test
  void theShippedCurvesOfFloorsSixToOneHundredMatchTheDefaults() throws IOException {
    var shipped = bound();
    var defaults = new GameProperties();

    assertThat(shipped.getEnemies().getBudgetCurve())
        .isEqualTo(defaults.getEnemies().getBudgetCurve())
        .containsKeys(50, 59, 60, 80, 100);
    assertThat(shipped.getEnemies().getTierMinCurve())
        .isEqualTo(defaults.getEnemies().getTierMinCurve());
    assertThat(shipped.getEnemies().getTierMaxCurve())
        .isEqualTo(defaults.getEnemies().getTierMaxCurve());
    assertThat(shipped.getEnemies().getSurgeMultiplierUpper())
        .isEqualTo(defaults.getEnemies().getSurgeMultiplierUpper());
    assertThat(shipped.getEnemies().getCurveFromFloor())
        .isEqualTo(defaults.getEnemies().getCurveFromFloor())
        .isEqualTo(6);
    assertThat(shipped.getBoss().getHealthCurve()).isEqualTo(defaults.getBoss().getHealthCurve());
    assertThat(shipped.getLoop().getSupplyDrainCurve())
        .isEqualTo(defaults.getLoop().getSupplyDrainCurve());
    assertThat(shipped.getSupply().getCarryMultiplier())
        .isEqualTo(defaults.getSupply().getCarryMultiplier());
  }
}

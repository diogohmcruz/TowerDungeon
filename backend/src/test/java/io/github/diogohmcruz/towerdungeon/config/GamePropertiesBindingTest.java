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
}

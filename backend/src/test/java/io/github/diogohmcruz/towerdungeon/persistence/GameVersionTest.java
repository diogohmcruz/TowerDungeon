package io.github.diogohmcruz.towerdungeon.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;

class GameVersionTest {
  private static GameVersion with(BuildProperties build) {
    var factory = new DefaultListableBeanFactory();
    if (build != null) {
      factory.registerSingleton("buildProperties", build);
    }
    return new GameVersion(factory.getBeanProvider(BuildProperties.class));
  }

  @Test
  void usesTheBuildVersion() {
    var properties = new Properties();
    properties.setProperty("version", "2.0.0");

    assertThat(with(new BuildProperties(properties)).current()).isEqualTo("2.0.0");
  }

  @Test
  void fallsBackWhenThereIsNoBuildInfo() {
    assertThat(with(null).current()).isEqualTo("dev");
  }
}

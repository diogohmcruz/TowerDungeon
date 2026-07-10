package io.github.diogohmcruz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

@EnableScheduling
@EnableConfigurationProperties(GameProperties.class)
@SpringBootApplication
public class TowerDungeonApplication {
  public static void main(String[] args) {
    SpringApplication.run(TowerDungeonApplication.class, args);
  }
}

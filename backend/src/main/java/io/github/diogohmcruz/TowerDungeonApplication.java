package io.github.diogohmcruz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TowerDungeonApplication {
  public static void main(String[] args) {
    SpringApplication.run(TowerDungeonApplication.class, args);
  }
}

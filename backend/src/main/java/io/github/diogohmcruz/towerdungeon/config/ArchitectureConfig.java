package io.github.diogohmcruz.towerdungeon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;

@Configuration
public class ArchitectureConfig {
  /** One plan for the whole Tower: in Generation 1 every Campaign climbs the same Floors. */
  @Bean
  public ArchitecturePlan architecturePlan(GameProperties config) {
    return ArchitecturePlan.generate(
        config.getArchitecture().getSeed(),
        config.getTower().getMaxFloor(),
        config.getBoss().getInterval(),
        config.getEnemies().getSurgeInterval());
  }
}

package io.github.diogohmcruz.towerdungeon.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

@Configuration
public class PersistenceConfig {
  @Bean
  public FileCampaignStore campaignStore(GameProperties config) {
    return new FileCampaignStore(config.getPersistence());
  }
}

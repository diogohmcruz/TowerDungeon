package io.github.diogohmcruz.towerdungeon.persistence;

import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

@Configuration
public class PersistenceConfig {
  @Bean
  public DataDirLock dataDirLock(GameProperties config) {
    return new DataDirLock(Path.of(config.getPersistence().getDataDir()));
  }

  /** Depends on the lock so no store exists for a data directory another instance holds. */
  @Bean
  public FileCampaignStore campaignStore(GameProperties config, DataDirLock lock) {
    return new FileCampaignStore(config.getPersistence());
  }
}

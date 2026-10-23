package io.github.diogohmcruz.towerdungeon.persistence;

import java.nio.file.Path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

@Configuration
public class PersistenceConfig {
  @Bean
  public DataDirLock dataDirLock(GameProperties config) {
    return new DataDirLock(Path.of(config.getPersistence().getDataDir()));
  }

  /** Depends on the lock so no store exists for a data directory another instance holds. */
  @Bean
  public FileCampaignStore fileCampaignStore(GameProperties config, DataDirLock lock) {
    return new FileCampaignStore(config.getPersistence());
  }

  /** What the game reads and writes through: the file store, with every load migrated. */
  @Bean
  @Primary
  public CampaignStore campaignStore(FileCampaignStore files, CampaignCodec codec) {
    return new MigratingCampaignStore(files, codec);
  }

  /** Fails startup when the seal key is unset in production ({@code prod} profile). */
  @Bean
  public Sealer sealer(GameProperties config, Environment environment) {
    return Sealer.forConfig(
        config.getPersistence().getSealKey(), environment.acceptsProfiles(Profiles.of("prod")));
  }
}

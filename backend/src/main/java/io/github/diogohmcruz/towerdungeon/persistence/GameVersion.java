package io.github.diogohmcruz.towerdungeon.persistence;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * The game version stamped on every saved record, read from the build info the Spring Boot Maven
 * plugin generates. Runs without build info (an IDE run before the first build) report {@code dev}.
 */
@Component
public class GameVersion {
  static final String UNKNOWN = "dev";

  private final String version;

  public GameVersion(ObjectProvider<BuildProperties> buildProperties) {
    var build = buildProperties.getIfAvailable();
    this.version = build == null || build.getVersion() == null ? UNKNOWN : build.getVersion();
  }

  public String current() {
    return version;
  }
}

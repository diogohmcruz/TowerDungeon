package io.github.diogohmcruz.towerdungeon.agent.mcp;

import java.nio.file.Path;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.auth.AuditLog;
import io.github.diogohmcruz.towerdungeon.agent.auth.RateLimiter;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** What both doors of the agent surface (HTTP and stdio) share. */
@AgentEnabled
@Configuration
@EnableConfigurationProperties(AgentHttpProperties.class)
public class AgentConfig {

  @Bean
  public RateLimiter agentRateLimiter() {
    return new RateLimiter();
  }

  /** The audit log: beside the Campaign files unless {@code game.agent.http.audit-file} says. */
  @Bean
  public AuditLog agentAuditLog(AgentHttpProperties http, GameProperties config) {
    var file =
        http.getAuditFile().isBlank()
            ? Path.of(config.getPersistence().getDataDir(), "audit", "agent-audit.jsonl")
            : Path.of(http.getAuditFile());
    return new AuditLog(file, Clock.systemUTC());
  }
}

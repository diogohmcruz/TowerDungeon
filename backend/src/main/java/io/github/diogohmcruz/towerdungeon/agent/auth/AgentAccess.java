package io.github.diogohmcruz.towerdungeon.agent.auth;

import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.mcp.AgentHttpProperties;

/**
 * Turns a bearer token into a {@link Principal}. For now the only token is the developer's, kept as
 * a SHA-256 hash in configuration; it is never stored raw, and compared in constant time.
 */
@AgentEnabled
@Component
public class AgentAccess {
  private final String devTokenHash;

  public AgentAccess(AgentHttpProperties properties) {
    this.devTokenHash = properties.getDevTokenHash();
  }

  /** The principal for a bearer token, or empty when the token is absent, empty or wrong. */
  public Optional<Principal> authenticate(String bearerToken) {
    if (bearerToken == null || bearerToken.isBlank()) {
      return Optional.empty();
    }
    return TokenHasher.matches(bearerToken, devTokenHash)
        ? Optional.of(Principal.dev())
        : Optional.empty();
  }
}

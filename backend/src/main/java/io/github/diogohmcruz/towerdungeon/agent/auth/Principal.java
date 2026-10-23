package io.github.diogohmcruz.towerdungeon.agent.auth;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who is calling, and what they may do. It is resolved once, before the MCP library sees a request
 * (by the HTTP filter, or fixed for stdio), and reaches the tools through the transport context, so
 * no tool depends on a library's way of carrying identity.
 *
 * @param grantId a short label of the grant, safe to log; never the token
 * @param scopes what the grant may do
 */
public record Principal(String grantId, Set<Scope> scopes) {
  /** The key under which the filter leaves the principal on the exchange and the context. */
  public static final String KEY = "td.principal";

  public Principal {
    scopes = Set.copyOf(scopes);
  }

  /** The developer token: read everything, drive Sandboxes and act in them. */
  public static Principal dev() {
    return new Principal("dev", EnumSet.allOf(Scope.class));
  }

  /** The caller on standard input: a local process the developer started themselves. */
  public static Principal stdio() {
    return new Principal("stdio", EnumSet.allOf(Scope.class));
  }

  public boolean has(Scope scope) {
    return scopes.contains(scope);
  }
}

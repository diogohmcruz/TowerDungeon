package io.github.diogohmcruz.towerdungeon.agent.tools;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.Scope;
import reactor.core.publisher.Mono;

/**
 * One tool, defined once as data. The MCP binding reads this and nothing else, so changing the
 * library never touches a tool. Annotations are always explicit (the protocol's defaults are
 * destructive and open-world), and a description is at most {@value #MAX_DESCRIPTION} characters.
 *
 * @param name flat, snake_case, prefixed {@code td_}
 * @param description plain and factual, in glossary words
 * @param inputSchema JSON Schema 2020-12 of the arguments
 * @param readOnly the tool changes nothing
 * @param destructive the tool may destroy what the caller holds
 * @param idempotent calling twice with the same arguments has the effect of once
 * @param scope the scope a grant needs
 * @param write counts against the write rate limit
 * @param handler runs the tool; a {@link ToolFailure} is a refusal, anything else an internal error
 */
public record ToolDef(
    String name,
    String description,
    Map<String, Object> inputSchema,
    boolean readOnly,
    boolean destructive,
    boolean idempotent,
    Scope scope,
    boolean write,
    Handler handler) {
  public static final int MAX_DESCRIPTION = 400;

  /** What a tool does with one call: the result is plain data (maps, lists, strings, numbers). */
  @FunctionalInterface
  public interface Handler {
    Mono<Map<String, Object>> handle(Call call);
  }

  /** One call: who made it and what they asked. */
  public record Call(Principal principal, Args args) {}

  public ToolDef {
    Objects.requireNonNull(name);
    if (!name.matches("td_[a-z0-9_]{1,60}")) {
      throw new IllegalArgumentException("Tool names are td_ and snake_case: " + name);
    }
    if (description.length() > MAX_DESCRIPTION) {
      throw new IllegalArgumentException("The description of " + name + " is too long");
    }
  }

  /** The property names the schema declares, for rejecting unknown arguments. */
  @SuppressWarnings("unchecked")
  public List<String> propertyNames() {
    return List.copyOf(((Map<String, Object>) inputSchema.get("properties")).keySet());
  }

  @SuppressWarnings("unchecked")
  public List<String> required() {
    return (List<String>) inputSchema.getOrDefault("required", List.of());
  }
}

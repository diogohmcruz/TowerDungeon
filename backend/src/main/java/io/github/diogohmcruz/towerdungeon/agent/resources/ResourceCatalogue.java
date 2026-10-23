package io.github.diogohmcruz.towerdungeon.agent.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.observe.Observations;
import io.github.diogohmcruz.towerdungeon.agent.observe.RuleViews;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolFailure;
import tools.jackson.databind.json.JsonMapper;

/**
 * The resources of the agent surface (design 4.8), defined once as data like the tools. Every
 * resource is also reachable through a tool, because some hosts only call tools. A read of a
 * resource goes through the same {@link Observations} the tools use, so the two cannot disagree,
 * and nothing here reaches the Tower's plan, a seed, or any hidden state.
 *
 * <p>{@code ttlMs} and {@code cacheScope} are carried for the day the binding speaks the revision
 * of the protocol that requires them; the current one does not emit them.
 */
@AgentEnabled
@Component
public class ResourceCatalogue {
  /** A fixed resource. */
  public record Def(
      String uri,
      String name,
      String description,
      String mimeType,
      long ttlMs,
      String cacheScope,
      Reader reader) {}

  /** A resource with one number in its address. */
  public record Template(
      String uriTemplate,
      String name,
      String description,
      long ttlMs,
      String cacheScope,
      Pattern address,
      NumberedReader reader) {}

  /** Reads a resource for a caller. */
  @FunctionalInterface
  public interface Reader {
    String read(Principal principal);
  }

  /** Reads a resource whose address holds a number. */
  @FunctionalInterface
  public interface NumberedReader {
    String read(Principal principal, int number);
  }

  /** What a read of an address found. */
  public record Content(String uri, String mimeType, String text) {}

  public static final String JSON = "application/json";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final List<Def> fixed;
  private final List<Template> templates;

  public ResourceCatalogue(Observations observations) {
    var glossary = Glossary.load().filtered();
    var all = new ArrayList<Def>();
    all.add(
        json(
            "td://state",
            "state",
            "The brief state of the Campaign.",
            0,
            "private",
            p -> observations.state(p, null, "brief", null)));
    all.add(
        json(
            "td://actions",
            "actions",
            "What the Campaign would accept now.",
            0,
            "private",
            p -> observations.actions(p, null)));
    all.add(
        json(
            "td://tower",
            "tower",
            "The Tower as far as the Party has climbed.",
            0,
            "private",
            p -> observations.tower(p, null)));
    all.add(
        json(
            "td://ledger",
            "ledger",
            "The Ledger and the latest Expedition Summaries.",
            0,
            "private",
            p -> observations.ledger(p, null, 20, 5)));
    all.add(
        json(
            "td://events",
            "events",
            "The newest events of the Campaign.",
            0,
            "private",
            p -> observations.events(p, null, 0, 100)));
    for (var section : RuleViews.SECTIONS) {
      all.add(
          json(
              "td://rules/" + section,
              "rules-" + section,
              "The rules section: " + section + ".",
              3_600_000,
              "public",
              p -> observations.rules(p, null, section)));
    }
    all.add(
        json(
            "td://rules/" + RuleViews.ENEMIES,
            "rules-enemies",
            "The Enemy Type table. Sandbox callers only.",
            3_600_000,
            "public",
            p -> observations.rules(p, null, RuleViews.ENEMIES)));
    all.add(
        new Def(
            "td://glossary",
            "glossary",
            "The shipped glossary: terms and definitions, nothing planned.",
            "text/markdown",
            3_600_000,
            "public",
            _ -> glossary));
    this.fixed = List.copyOf(all);
    this.templates =
        List.of(
            new Template(
                "td://tower/floors/{n}",
                "tower-floor",
                "One Floor the Party has reached.",
                0,
                "private",
                Pattern.compile("^td://tower/floors/(\\d{1,3})$"),
                (p, n) -> write(observations.floor(p, null, n))),
            new Template(
                "td://ledger/{n}",
                "ledger-entry",
                "An Expedition Summary by number (1 is the first); the latest 20 are readable.",
                0,
                "private",
                Pattern.compile("^td://ledger/(\\d{1,3})$"),
                (p, n) -> write(expedition(observations, p, n))));
  }

  public List<Def> fixed() {
    return fixed;
  }

  public List<Template> templates() {
    return templates;
  }

  /**
   * Reads an address; empty when it is none of ours. A failure of the read is a {@link
   * ToolFailure}.
   */
  public Optional<Content> read(Principal principal, String uri) {
    for (var def : fixed) {
      if (def.uri().equals(uri)) {
        return Optional.of(new Content(uri, def.mimeType(), def.reader().read(principal)));
      }
    }
    for (var template : templates) {
      var matcher = template.address().matcher(uri);
      if (matcher.matches()) {
        var number = Integer.parseInt(matcher.group(1));
        return Optional.of(new Content(uri, JSON, template.reader().read(principal, number)));
      }
    }
    return Optional.empty();
  }

  private static Map<String, Object> expedition(
      Observations observations, Principal p, int number) {
    var ledger = observations.ledger(p, null, 1, 20);
    @SuppressWarnings("unchecked")
    var list = (List<Map<String, Object>>) ledger.get("expeditions");
    var total = ((Number) ledger.get("expedition_count")).intValue();
    var index = number - (total - list.size()) - 1;
    if (number < 1 || index < 0 || index >= list.size()) {
      throw new ToolFailure("NO_SUCH_ENTRY", "There is no such Expedition Summary.");
    }
    return list.get(index);
  }

  private static Def json(
      String uri,
      String name,
      String description,
      long ttlMs,
      String scope,
      java.util.function.Function<Principal, Map<String, Object>> source) {
    return new Def(uri, name, description, JSON, ttlMs, scope, p -> write(source.apply(p)));
  }

  private static String write(Map<String, Object> value) {
    return MAPPER.writeValueAsString(value);
  }
}

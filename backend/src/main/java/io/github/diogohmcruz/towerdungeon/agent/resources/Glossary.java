package io.github.diogohmcruz.towerdungeon.agent.resources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;

/**
 * The project glossary ({@code CONTEXT.md}), filtered for an agent: only shipped entries, each as
 * its term and definition, and no {@code _Avoid_} line. Planned entries stay out so unbuilt design
 * is not readable, and so does the whole section of per-unit depth, whose terms the glossary marks
 * planned and which would name hidden mechanics. The avoided words are available separately so a
 * test can hold the tool and resource text to the same vocabulary.
 */
public final class Glossary {
  /** One glossary entry. {@code avoid} is the raw {@code _Avoid_} line, or null. */
  public record Entry(String section, String term, String definition, String avoid) {
    boolean planned() {
      return definition.contains("Planned") || definition.contains("Intended but not yet");
    }
  }

  private static final String SKIPPED_SECTION = "Bodies, gear and mind";

  private final List<Entry> entries;

  public Glossary(String source) {
    this.entries = parse(source);
  }

  /** The glossary shipped on the classpath. */
  public static Glossary load() {
    try (var in = new ClassPathResource("agent/CONTEXT.md").getInputStream()) {
      return new Glossary(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("The glossary is missing from the build", e);
    }
  }

  /** The filtered glossary as Markdown. */
  public String filtered() {
    var out = new StringBuilder("# Tower Dungeon glossary\n\nShipped terms only.\n");
    String section = null;
    for (var entry : entries) {
      if (entry.planned() || entry.section().equals(SKIPPED_SECTION)) {
        continue;
      }
      if (!entry.section().equals(section)) {
        section = entry.section();
        out.append("\n## ").append(section).append("\n");
      }
      out.append("\n**")
          .append(entry.term())
          .append("**: ")
          .append(entry.definition())
          .append("\n");
    }
    return out.toString();
  }

  /** The terms the source marks planned or not yet built, which the filtered text leaves out. */
  public List<String> plannedTerms() {
    return entries.stream().filter(Entry::planned).map(Entry::term).toList();
  }

  /** The raw {@code _Avoid_} lines of the source, which the filtered text never carries. */
  public List<String> avoidLines() {
    return entries.stream().map(Entry::avoid).filter(java.util.Objects::nonNull).toList();
  }

  /** Every term the glossary says to avoid, lower-cased, minus words that are terms themselves. */
  public Set<String> avoidedTerms() {
    var terms = new LinkedHashSet<String>();
    var defined = new LinkedHashSet<String>();
    entries.forEach(e -> defined.add(e.term().toLowerCase(Locale.ROOT)));
    for (var entry : entries) {
      if (entry.avoid() == null) {
        continue;
      }
      var plain = entry.avoid().replaceAll("\\([^)]*\\)", "");
      for (var part : plain.split(",")) {
        var term = part.strip().toLowerCase(Locale.ROOT).replaceAll("[.]+$", "");
        if (!term.isEmpty() && !defined.contains(term)) {
          terms.add(term);
        }
      }
    }
    return terms;
  }

  /** The avoided terms found in {@code text}, whole words or phrases, case-insensitive. */
  public List<String> avoidedIn(String text) {
    // A word is only avoided on its own: "Reinforcement Wave" is the term, "wave" is not.
    var rest = text;
    var defined = new ArrayList<>(entries.stream().map(Entry::term).toList());
    defined.sort((a, b) -> b.length() - a.length());
    for (var term : defined) {
      rest =
          Pattern.compile(
                  "(?<![A-Za-z])" + Pattern.quote(term) + "(?![A-Za-z])", Pattern.CASE_INSENSITIVE)
              .matcher(rest)
              .replaceAll(" ");
    }
    var found = new ArrayList<String>();
    for (var term : avoidedTerms()) {
      if (Pattern.compile(
              "(?<![A-Za-z])" + Pattern.quote(term) + "(?![A-Za-z])", Pattern.CASE_INSENSITIVE)
          .matcher(rest)
          .find()) {
        found.add(term);
      }
    }
    return found;
  }

  static List<Entry> parse(String source) {
    var entries = new ArrayList<Entry>();
    var term = (String) null;
    var section = "";
    var definition = new StringBuilder();
    String avoid = null;
    var termPattern = Pattern.compile("^\\*\\*(.+?)\\*\\*:\\s*$");
    for (var line : source.split("\n")) {
      var matcher = termPattern.matcher(line);
      if (matcher.matches() || line.startsWith("#")) {
        if (term != null) {
          entries.add(new Entry(section, term, definition.toString().strip(), avoid));
        }
        term = null;
        definition.setLength(0);
        avoid = null;
        if (matcher.matches()) {
          term = matcher.group(1);
        } else if (line.startsWith("### ")) {
          section = line.substring(4).strip();
        }
      } else if (term != null) {
        if (line.startsWith("_Avoid_:")) {
          avoid = line.substring("_Avoid_:".length()).strip();
        } else if (!line.isBlank()) {
          definition.append(definition.isEmpty() ? "" : " ").append(line.strip());
        }
      }
    }
    if (term != null) {
      entries.add(new Entry(section, term, definition.toString().strip(), avoid));
    }
    return entries;
  }
}

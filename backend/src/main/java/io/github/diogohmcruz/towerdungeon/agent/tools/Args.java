package io.github.diogohmcruz.towerdungeon.agent.tools;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The arguments of one tool call, read defensively: a wrong type or a value out of range is an
 * {@code INVALID_ARGUMENT} failure with fixed text, never an exception from the JSON layer.
 */
public final class Args {
  private final Map<String, Object> values;

  public Args(Map<String, Object> values) {
    this.values = values == null ? Map.of() : values;
  }

  public boolean has(String name) {
    return values.get(name) != null;
  }

  /** The value as sent, for the few tools whose argument is a map or one of two shapes. */
  public Object raw(String name) {
    return values.get(name);
  }

  /** A boolean, or {@code fallback} when absent; any other type is invalid. */
  public boolean bool(String name, boolean fallback) {
    var value = values.get(name);
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof Boolean flag)) {
      throw invalid(name);
    }
    return flag;
  }

  /** The keys the caller sent. */
  public Set<String> names() {
    return values.keySet();
  }

  public String string(String name, Pattern pattern, String fallback) {
    var value = values.get(name);
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof String text)
        || text.length() > 200
        || !pattern.matcher(text).matches()) {
      throw invalid(name);
    }
    return text;
  }

  /** One of {@code allowed}, matched exactly. */
  public String oneOf(String name, List<String> allowed, String fallback) {
    var value = values.get(name);
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof String text) || !allowed.contains(text)) {
      throw invalid(name);
    }
    return text;
  }

  public long integer(String name, long min, long max, long fallback) {
    var value = values.get(name);
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof Number number)
        || number.doubleValue() != Math.rint(number.doubleValue())
        || number.longValue() < min
        || number.longValue() > max) {
      throw invalid(name);
    }
    return number.longValue();
  }

  public double number(String name, double min, double max) {
    var value = values.get(name);
    if (!(value instanceof Number number)
        || Double.isNaN(number.doubleValue())
        || number.doubleValue() < min
        || number.doubleValue() > max) {
      throw invalid(name);
    }
    return number.doubleValue();
  }

  /** A list of objects, within the size bounds. */
  public List<Map<String, Object>> objects(String name, int min, int max) {
    var value = values.get(name);
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List<?> list) || list.size() < min || list.size() > max) {
      throw invalid(name);
    }
    var out = new java.util.ArrayList<Map<String, Object>>();
    for (var item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw invalid(name);
      }
      var copy = new java.util.LinkedHashMap<String, Object>();
      map.forEach(
          (k, v) -> {
            if (!(k instanceof String key)) {
              throw invalid(name);
            }
            copy.put(key, v);
          });
      out.add(copy);
    }
    return out;
  }

  /** The refusal for an argument that is not valid. */
  public static ToolFailure invalid(String name) {
    return new ToolFailure(
        "INVALID_ARGUMENT", "The argument '" + safeName(name) + "' is not valid.");
  }

  /** Argument names come from this code, never from the caller, but stay defensive. */
  private static String safeName(String name) {
    return name.replaceAll("[^A-Za-z0-9_]", "?");
  }
}

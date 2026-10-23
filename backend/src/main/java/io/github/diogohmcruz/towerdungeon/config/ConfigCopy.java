package io.github.diogohmcruz.towerdungeon.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deep copy of the configuration tree. Walks the fields instead of listing them, so a value added
 * to {@link GameProperties} later is copied without anyone remembering to touch this class. Objects
 * reachable by two paths are copied once, so what the original shares (the Body weights the Enemies
 * use) the copy shares too.
 */
final class ConfigCopy {
  private ConfigCopy() {}

  @SuppressWarnings("unchecked")
  static <T> T of(T source) {
    return (T) copy(source, new IdentityHashMap<>());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object copy(Object value, Map<Object, Object> done) {
    if (value == null || isImmutable(value)) {
      return value;
    }
    var existing = done.get(value);
    if (existing != null) {
      return existing;
    }
    if (value instanceof List<?> list) {
      var out = new ArrayList<Object>(list.size());
      done.put(value, out);
      list.forEach(item -> out.add(copy(item, done)));
      return out;
    }
    if (value instanceof Map<?, ?> map) {
      Map out = value instanceof EnumMap<?, ?> e ? new EnumMap(e) : new LinkedHashMap<>();
      done.put(value, out);
      map.forEach((k, v) -> out.put(k, copy(v, done)));
      return out;
    }
    if (value instanceof Set<?> set) {
      Set out = value instanceof EnumSet<?> e ? e.clone() : new LinkedHashSet<>();
      done.put(value, out);
      if (!(value instanceof EnumSet)) {
        set.forEach(item -> out.add(copy(item, done)));
      }
      return out;
    }
    return copyObject(value, done);
  }

  private static Object copyObject(Object value, Map<Object, Object> done) {
    try {
      var constructor = value.getClass().getDeclaredConstructor();
      constructor.setAccessible(true);
      var out = constructor.newInstance();
      done.put(value, out);
      for (var type = value.getClass(); type != Object.class; type = type.getSuperclass()) {
        for (var field : type.getDeclaredFields()) {
          copyField(field, value, out, done);
        }
      }
      return out;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Cannot copy configuration of type " + value.getClass().getName(), e);
    }
  }

  private static void copyField(Field field, Object from, Object to, Map<Object, Object> done)
      throws IllegalAccessException {
    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
      return;
    }
    field.setAccessible(true);
    field.set(to, copy(field.get(from), done));
  }

  private static boolean isImmutable(Object value) {
    return value instanceof Enum<?>
        || value instanceof Number
        || value instanceof CharSequence
        || value instanceof Boolean
        || value instanceof Character
        || value instanceof TemporalAmount
        || value instanceof TemporalAccessor;
  }
}

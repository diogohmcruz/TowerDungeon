package io.github.diogohmcruz.towerdungeon.agent.tools;

import java.util.Map;

/**
 * A tool refusing a call for a stable reason. The code and message are fixed text the caller may
 * see; the message never echoes caller-supplied strings. It carries no stack trace.
 */
public final class ToolFailure extends RuntimeException {
  private final String code;
  private final Map<String, Object> extra;

  public ToolFailure(String code, String message) {
    this(code, message, Map.of());
  }

  /** {@code extra} holds small fixed-shape facts, such as {@code retry_after_ms}. */
  public ToolFailure(String code, String message, Map<String, Object> extra) {
    super(message, null, false, false);
    this.code = code;
    this.extra = Map.copyOf(extra);
  }

  public String code() {
    return code;
  }

  public Map<String, Object> extra() {
    return extra;
  }
}

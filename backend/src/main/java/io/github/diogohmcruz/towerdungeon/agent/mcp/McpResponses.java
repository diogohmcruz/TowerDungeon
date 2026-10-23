package io.github.diogohmcruz.towerdungeon.agent.mcp;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;

import reactor.core.publisher.Mono;

/**
 * The fixed bodies of the endpoint's failures. Nothing the library or a handler said ever reaches a
 * caller through a 4xx or 5xx: the library serialises exceptions with their stack traces, so every
 * such body is replaced by one of these.
 */
final class McpResponses {
  static final String INVALID_REQUEST =
      "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"Invalid"
          + " request\"}}";
  static final String PARSE_ERROR =
      "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}";
  static final String METHOD_NOT_FOUND =
      "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32601,\"message\":\"Method not"
          + " found\"}}";
  static final String INTERNAL_ERROR =
      "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal"
          + " error\"}}";
  static final String UNAUTHORIZED = "{\"error\":\"unauthorized\"}";
  static final String FORBIDDEN = "{\"error\":\"forbidden\"}";
  static final String METHOD_NOT_ALLOWED = "{\"error\":\"method_not_allowed\"}";
  static final String TOO_LARGE = "{\"error\":\"payload_too_large\"}";
  static final String RATE_LIMITED = "{\"error\":\"rate_limited\"}";

  private McpResponses() {}

  /** The fixed body that stands for a failure with this status. */
  static String bodyFor(HttpStatusCode status) {
    return status.is5xxServerError() ? INTERNAL_ERROR : INVALID_REQUEST;
  }

  /** Writes a fixed JSON body with a status, on a response nobody else is writing to. */
  static Mono<Void> write(ServerHttpResponse response, HttpStatusCode status, String body) {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    response.getHeaders().setContentLength(bytes.length);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
  }
}

package io.github.diogohmcruz.towerdungeon.agent.mcp;

import java.net.InetSocketAddress;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.github.diogohmcruz.towerdungeon.agent.auth.AgentAccess;
import io.github.diogohmcruz.towerdungeon.agent.auth.AuditLog;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.RateLimiter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Guards the MCP endpoint before the library sees a request. In order: a {@code Host} that is not
 * listed is 403 (DNS rebinding), an {@code Origin} that is present and not listed is 403, a source
 * address over its limit is 429, any method but POST is 405, a missing or wrong bearer token is
 * 401, and a body over the cap is 413. A request that passes carries its {@link Principal} on the
 * exchange, and its response is wrapped so no 4xx or 5xx body from the library gets out.
 *
 * <p>No CORS header is ever written: the endpoint is for programs, not pages. Only the hash of the
 * token is held; the token itself is compared and forgotten.
 */
public class McpHttpFilter implements WebFilter, Ordered {
  private final AgentHttpProperties properties;
  private final AgentAccess access;
  private final RateLimiter limiter;
  private final AuditLog audit;

  public McpHttpFilter(
      AgentHttpProperties properties, AgentAccess access, RateLimiter limiter, AuditLog audit) {
    this.properties = properties;
    this.access = access;
    this.limiter = limiter;
    this.audit = audit;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    var request = exchange.getRequest();
    if (!properties.getEndpoint().equals(request.getPath().value())) {
      return chain.filter(exchange);
    }
    var response = exchange.getResponse();
    var headers = request.getHeaders();

    if (!hostAllowed(headers.getFirst(HttpHeaders.HOST))) {
      return reject(exchange, HttpStatus.FORBIDDEN, McpResponses.FORBIDDEN);
    }
    var origin = headers.getOrigin();
    if (origin != null && !properties.getAllowedOrigins().contains(origin)) {
      return reject(exchange, HttpStatus.FORBIDDEN, McpResponses.FORBIDDEN);
    }
    var verdict = limiter.tryAcquire(RateLimiter.Kind.SOURCE, source(request));
    if (!verdict.allowed()) {
      response
          .getHeaders()
          .set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, verdict.retryAfterMs() / 1000)));
      return reject(exchange, HttpStatus.TOO_MANY_REQUESTS, McpResponses.RATE_LIMITED);
    }
    if (request.getMethod() != HttpMethod.POST) {
      response.getHeaders().set(HttpHeaders.ALLOW, "POST");
      return reject(exchange, HttpStatus.METHOD_NOT_ALLOWED, McpResponses.METHOD_NOT_ALLOWED);
    }
    var principal = access.authenticate(bearer(headers.getFirst(HttpHeaders.AUTHORIZATION)));
    if (principal.isEmpty()) {
      response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      return reject(exchange, HttpStatus.UNAUTHORIZED, McpResponses.UNAUTHORIZED);
    }
    exchange.getAttributes().put(Principal.KEY, principal.get());

    var length = headers.getContentLength();
    if (length > properties.getMaxBodyBytes()) {
      return reject(exchange, HttpStatus.PAYLOAD_TOO_LARGE, McpResponses.TOO_LARGE);
    }
    if (length >= 0) {
      return chain.filter(wrap(exchange, request));
    }
    // No Content-Length (chunked): read at most the cap, then hand the same bytes on.
    return DataBufferUtils.join(request.getBody(), properties.getMaxBodyBytes())
        .map(buffer -> (ServerHttpRequest) new Buffered(request, buffer))
        .defaultIfEmpty(request)
        .flatMap(buffered -> chain.filter(wrap(exchange, buffered)))
        .onErrorResume(
            DataBufferLimitException.class,
            _ -> reject(exchange, HttpStatus.PAYLOAD_TOO_LARGE, McpResponses.TOO_LARGE));
  }

  private ServerWebExchange wrap(ServerWebExchange exchange, ServerHttpRequest request) {
    return exchange
        .mutate()
        .request(request)
        .response(new SanitisingResponse(exchange.getResponse()))
        .build();
  }

  private Mono<Void> reject(ServerWebExchange exchange, HttpStatusCode status, String body) {
    audit.record(
        new AuditLog.Entry("-", "http", "-", "rejected", "HTTP_" + status.value(), null, 0));
    return McpResponses.write(exchange.getResponse(), status, body);
  }

  /** The request's address, without a port; proxy headers are never trusted. */
  private static String source(ServerHttpRequest request) {
    InetSocketAddress remote = request.getRemoteAddress();
    return remote == null
        ? "unknown"
        : remote.getAddress() == null
            ? remote.getHostString()
            : remote.getAddress().getHostAddress();
  }

  private boolean hostAllowed(String host) {
    if (host == null || host.isBlank()) {
      return false;
    }
    var name = host.strip().toLowerCase(Locale.ROOT);
    if (name.startsWith("[")) {
      var end = name.indexOf(']');
      name = end < 0 ? name : name.substring(0, end + 1);
    } else {
      var colon = name.lastIndexOf(':');
      name = colon < 0 ? name : name.substring(0, colon);
    }
    final var wanted = name;
    return properties.getAllowedHosts().stream()
        .anyMatch(allowed -> allowed.toLowerCase(Locale.ROOT).equals(wanted));
  }

  private static String bearer(String header) {
    if (header == null || header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return null;
    }
    return header.substring(7).strip();
  }

  /** The request with its body already read, so the library can read it again. */
  private static final class Buffered extends ServerHttpRequestDecorator {
    private final org.springframework.core.io.buffer.DataBuffer body;

    private Buffered(
        ServerHttpRequest delegate, org.springframework.core.io.buffer.DataBuffer body) {
      super(delegate);
      this.body = body;
    }

    @Override
    public Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
      return Flux.just(body);
    }
  }
}

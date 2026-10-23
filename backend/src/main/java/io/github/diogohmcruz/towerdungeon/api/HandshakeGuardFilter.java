package io.github.diogohmcruz.towerdungeon.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Guards the socket handshake before the upgrade: refuses a plain {@code ws} connection when {@code
 * game.security.require-wss} is set, and a browser whose {@code Origin} is not on {@code
 * game.security.allowed-origins}.
 */
@Slf4j
@ConditionalOnWebApplication
@Component
@RequiredArgsConstructor
public class HandshakeGuardFilter implements WebFilter {
  static final String SOCKET_PATH = "/api/ws";

  private final GameProperties config;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    var request = exchange.getRequest();
    if (!SOCKET_PATH.equals(request.getPath().value())) {
      return chain.filter(exchange);
    }
    var security = config.getSecurity();
    if (security.isRequireWss() && !isSecure(exchange)) {
      log.warn("Refusing an insecure handshake (wss is required)");
      return refuse(exchange);
    }
    var origin = request.getHeaders().getOrigin();
    var allowed = security.getAllowedOrigins();
    if (origin != null && !allowed.isEmpty() && !allowed.contains(origin)) {
      log.warn("Refusing a handshake from origin {}", origin);
      return refuse(exchange);
    }
    return chain.filter(exchange);
  }

  /** TLS here, or terminated by a proxy that says so. A client gains nothing by lying. */
  private static boolean isSecure(ServerWebExchange exchange) {
    var request = exchange.getRequest();
    var scheme = request.getURI().getScheme();
    var forwarded = request.getHeaders().getFirst("X-Forwarded-Proto");
    return "https".equalsIgnoreCase(scheme)
        || "wss".equalsIgnoreCase(scheme)
        || "https".equalsIgnoreCase(forwarded);
  }

  private static Mono<Void> refuse(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
    return exchange.getResponse().setComplete();
  }
}

package io.github.diogohmcruz.towerdungeon.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import reactor.core.publisher.Mono;

class HandshakeGuardFilterTest {
  private final GameProperties config = new GameProperties();
  private final HandshakeGuardFilter filter = new HandshakeGuardFilter(config);

  /** Runs the filter and reports whether the request got through to the handler. */
  private boolean passes(MockServerHttpRequest.BaseBuilder<?> request) {
    var exchange = MockServerWebExchange.from(request);
    var reached = new AtomicBoolean();
    filter
        .filter(
            exchange,
            _ -> {
              reached.set(true);
              return Mono.empty();
            })
        .block();
    if (!reached.get()) {
      assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    return reached.get();
  }

  @Test
  void anyOriginIsAllowedWhenTheListIsEmpty() {
    assertThat(passes(MockServerHttpRequest.get("/api/ws").header("Origin", "https://x.test")))
        .isTrue();
  }

  @Test
  void onlyListedOriginsPassWhenTheListIsSet() {
    config.getSecurity().setAllowedOrigins(List.of("https://play.test"));

    assertThat(passes(MockServerHttpRequest.get("/api/ws").header("Origin", "https://play.test")))
        .isTrue();
    assertThat(passes(MockServerHttpRequest.get("/api/ws").header("Origin", "https://evil.test")))
        .isFalse();
  }

  @Test
  void aRequestWithNoOriginIsNotABrowserAndPasses() {
    config.getSecurity().setAllowedOrigins(List.of("https://play.test"));

    assertThat(passes(MockServerHttpRequest.get("/api/ws"))).isTrue();
  }

  @Test
  void plainWsIsRefusedWhenWssIsRequired() {
    config.getSecurity().setRequireWss(true);

    assertThat(passes(MockServerHttpRequest.get("http://host/api/ws"))).isFalse();
    assertThat(passes(MockServerHttpRequest.get("https://host/api/ws"))).isTrue();
    assertThat(
            passes(
                MockServerHttpRequest.get("http://host/api/ws")
                    .header("X-Forwarded-Proto", "https")))
        .isTrue();
  }

  @Test
  void otherPathsAreNotGuarded() {
    config.getSecurity().setRequireWss(true);

    assertThat(passes(MockServerHttpRequest.get("http://host/api/unit-stats"))).isTrue();
  }
}

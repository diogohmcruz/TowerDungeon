package io.github.diogohmcruz.towerdungeon.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import io.github.diogohmcruz.towerdungeon.agent.auth.AgentAccess;
import io.github.diogohmcruz.towerdungeon.agent.auth.AuditLog;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.RateLimiter;
import io.github.diogohmcruz.towerdungeon.agent.auth.TokenHasher;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The guarding filter and the response sanitiser, one rule at a time, with no server. */
class McpHttpFilterTest {
  private static final String TOKEN = "unit-test-token";
  private static final DefaultDataBufferFactory BUFFERS = DefaultDataBufferFactory.sharedInstance;

  private AgentHttpProperties properties;
  private McpHttpFilter filter;
  private final AtomicReference<ServerWebExchange> reached = new AtomicReference<>();
  private final AtomicReference<String> bodyReached = new AtomicReference<>();
  private final AtomicLong nanos = new AtomicLong();

  @BeforeEach
  void setUp() {
    properties = new AgentHttpProperties();
    properties.setDevTokenHash(TokenHasher.sha256Hex(TOKEN));
    properties.setAllowedOrigins(List.of("https://good.example"));
    properties.setMaxBodyBytes(100);
    filter =
        new McpHttpFilter(
            properties, new AgentAccess(properties), new RateLimiter(nanos::get), AuditLog.none());
  }

  private WebFilterChain chain(Mono<Void> answer) {
    return exchange -> {
      reached.set(exchange);
      return exchange
          .getRequest()
          .getBody()
          .map(
              buffer -> {
                var bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
              })
          .collectList()
          .doOnNext(parts -> bodyReached.set(String.join("", parts)))
          .then(answer);
    };
  }

  private MockServerHttpRequest.BodyBuilder post() {
    return postWithHost("localhost:8080");
  }

  private MockServerHttpRequest.BodyBuilder postWithHost(String host) {
    return MockServerHttpRequest.post("/mcp")
        .header(HttpHeaders.HOST, host)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
        .remoteAddress(new InetSocketAddress("10.1.1.1", 5000));
  }

  private MockServerWebExchange run(MockServerHttpRequest request) {
    return run(request, Mono.empty());
  }

  private MockServerWebExchange run(MockServerHttpRequest request, Mono<Void> answer) {
    var exchange = MockServerWebExchange.from(request);
    filter.filter(exchange, chain(answer)).block();
    return exchange;
  }

  private MockServerWebExchange runPost(String body) {
    return run(post().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body));
  }

  // ---------------------------------------------------------------- the matrix, one rule each

  @Test
  void aRequestToAnotherPathIsLeftAlone() {
    var exchange =
        run(
            MockServerHttpRequest.get("/actuator/health")
                .header(HttpHeaders.HOST, "evil.example")
                .build());

    assertThat(reached.get()).isNotNull();
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  void aHostThatIsNotListedIs403ButAPortOrIpv6FormIsFine() {
    var evil = run(postWithHost("evil.example").body("{}"));
    assertThat(evil.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(reached.get()).isNull();

    var v6 = run(postWithHost("[::1]:8080").body("{}"));
    assertThat(v6.getResponse().getStatusCode()).isNull();
    assertThat(reached.get()).isNotNull();
  }

  @Test
  void anOriginIsRefusedUnlessListedAndNoOriginIsFine() {
    var evil = run(post().header(HttpHeaders.ORIGIN, "https://evil.example").body("{}"));
    assertThat(evil.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(evil.getResponse().getHeaders().getAccessControlAllowOrigin()).isNull();

    var good = run(post().header(HttpHeaders.ORIGIN, "https://good.example").body("{}"));
    assertThat(good.getResponse().getStatusCode()).isNull();
    assertThat(good.getResponse().getHeaders().getAccessControlAllowOrigin()).isNull();
  }

  @Test
  void anyMethodButPostIs405WithAnAllowHeader() {
    for (var method :
        List.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)) {
      var exchange =
          run(
              MockServerHttpRequest.method(method, "/mcp")
                  .header(HttpHeaders.HOST, "localhost")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                  .build());

      assertThat(exchange.getResponse().getStatusCode())
          .as(method.name())
          .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
      assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.ALLOW)).isEqualTo("POST");
    }
  }

  @Test
  void aMissingOrWrongTokenIs401AndTheTokenIsNeverEchoed() {
    var none =
        run(MockServerHttpRequest.post("/mcp").header(HttpHeaders.HOST, "localhost").body("{}"));
    var wrong =
        run(
            MockServerHttpRequest.post("/mcp")
                .header(HttpHeaders.HOST, "localhost")
                .header(HttpHeaders.AUTHORIZATION, "Bearer secret-wrong")
                .body("{}"));
    var scheme =
        run(
            MockServerHttpRequest.post("/mcp")
                .header(HttpHeaders.HOST, "localhost")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + TOKEN)
                .body("{}"));

    for (var exchange : List.of(none, wrong, scheme)) {
      assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
          .isEqualTo("Bearer");
      assertThat(exchange.getResponse().getBodyAsString().block())
          .doesNotContain("secret-wrong", TOKEN);
    }
    assertThat(reached.get()).isNull();
  }

  @Test
  void aGoodTokenReachesTheLibraryWithItsPrincipal() {
    runPost("{}");

    assertThat(reached.get()).isNotNull();
    assertThat((Principal) reached.get().getAttribute(Principal.KEY)).isEqualTo(Principal.dev());
  }

  @Test
  void anOversizedBodyIs413ByContentLength() {
    var exchange = runPost("x".repeat(101));

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(reached.get()).isNull();
    assertThat(runPost("x".repeat(100)).getResponse().getStatusCode()).isNull();
  }

  @Test
  void aChunkedBodyOverTheCapIs413AndOneWithinItIsPassedOnIntact() {
    var big =
        Flux.just(BUFFERS.wrap("x".repeat(60).getBytes()), BUFFERS.wrap("y".repeat(60).getBytes()));
    var refused = run(post().body(big));
    assertThat(refused.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(reached.get()).isNull();

    var small = Flux.just(BUFFERS.wrap("{\"a\":".getBytes()), BUFFERS.wrap("1}".getBytes()));
    var passed = run(post().body(small));
    assertThat(passed.getResponse().getStatusCode()).isNull();
    assertThat(bodyReached.get()).isEqualTo("{\"a\":1}");
  }

  @Test
  void aSourceOverItsLimitIs429AndAnotherSourceIsNot() {
    for (var i = 0; i < 60; i++) {
      assertThat(runPost("{}").getResponse().getStatusCode()).as("request " + i).isNull();
    }
    var over = runPost("{}");
    assertThat(over.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(over.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();

    var other =
        run(
            MockServerHttpRequest.post("/mcp")
                .header(HttpHeaders.HOST, "localhost")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .remoteAddress(new InetSocketAddress("10.2.2.2", 1))
                .body("{}"));
    assertThat(other.getResponse().getStatusCode()).isNull();
  }

  @Test
  void theSourceLimitRunsBeforeTheTokenIsLookedAt() {
    for (var i = 0; i < 60; i++) {
      run(MockServerHttpRequest.post("/mcp").header(HttpHeaders.HOST, "localhost").body("{}"));
    }
    var over =
        run(MockServerHttpRequest.post("/mcp").header(HttpHeaders.HOST, "localhost").body("{}"));

    assertThat(over.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  // ---------------------------------------------------------------- the response sanitiser

  private static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status) {
    exchange.getResponse().setStatusCode(status);
    return exchange
        .getResponse()
        .writeWith(
            Mono.just(
                BUFFERS.wrap(
                    "{\"stackTrace\":[\"at io.modelcontextprotocol.Foo\"],\"trace\":\"secret\"}"
                        .getBytes())));
  }

  @Test
  void aLibraryErrorBodyIsReplacedByAFixedOne() {
    var exchange = MockServerWebExchange.from(post().body("{}"));
    filter.filter(exchange, ex -> writeError(ex, HttpStatus.BAD_REQUEST)).block();

    var body = exchange.getResponse().getBodyAsString().block();
    assertThat(body).isEqualTo(McpResponses.INVALID_REQUEST).doesNotContain("stackTrace", "secret");
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    var failing = MockServerWebExchange.from(post().body("{}"));
    filter.filter(failing, ex -> writeError(ex, HttpStatus.INTERNAL_SERVER_ERROR)).block();
    assertThat(failing.getResponse().getBodyAsString().block())
        .isEqualTo(McpResponses.INTERNAL_ERROR);
  }

  @Test
  void anEmptyLibraryErrorGetsTheFixedBodyToo() {
    var exchange = MockServerWebExchange.from(post().body("{}"));
    filter
        .filter(
            exchange,
            ex -> {
              ex.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
              return ex.getResponse().setComplete();
            })
        .block();

    assertThat(exchange.getResponse().getBodyAsString().block())
        .isEqualTo(McpResponses.INVALID_REQUEST);
  }

  @Test
  void aSuccessfulResponseIsLeftUntouched() {
    var exchange = MockServerWebExchange.from(post().body("{}"));
    filter
        .filter(
            exchange,
            ex -> {
              ex.getResponse().setStatusCode(HttpStatus.OK);
              return ex.getResponse()
                  .writeWith(Mono.just(BUFFERS.wrap("{\"ok\":true}".getBytes())));
            })
        .block();

    assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("{\"ok\":true}");
  }

  // ---------------------------------------------------------------- the error handler

  @Test
  void anUnknownMethodIsACleanMethodNotFoundAnyOtherFailureAFixed500() {
    var handler = new McpErrorHandler("/mcp");

    var unknown = MockServerWebExchange.from(post().body("{}"));
    handler
        .handle(
            unknown,
            McpError.builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND)
                .message("Missing handler: server/discover")
                .build())
        .block();
    assertThat(unknown.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(unknown.getResponse().getBodyAsString().block())
        .isEqualTo(McpResponses.METHOD_NOT_FOUND);

    var broken = MockServerWebExchange.from(post().body("{}"));
    handler.handle(broken, new IllegalStateException("db password is hunter2")).block();
    assertThat(broken.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(broken.getResponse().getBodyAsString().block())
        .isEqualTo(McpResponses.INTERNAL_ERROR)
        .doesNotContain("hunter2");

    var wrapped = MockServerWebExchange.from(post().body("{}"));
    handler
        .handle(
            wrapped,
            new RuntimeException(
                "wrapper",
                McpError.builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND).message("x").build()))
        .block();
    assertThat(wrapped.getResponse().getBodyAsString().block())
        .isEqualTo(McpResponses.METHOD_NOT_FOUND);
  }

  @Test
  void theErrorHandlerLeavesOtherPathsToTheNextHandler() {
    var handler = new McpErrorHandler("/mcp");
    var other = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ws").build());
    var propagated = new AtomicBoolean();

    handler
        .handle(other, new IllegalStateException("not ours"))
        .doOnError(e -> propagated.set(true))
        .onErrorResume(e -> Mono.empty())
        .block();

    assertThat(propagated).isTrue();
  }
}

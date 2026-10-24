package io.github.diogohmcruz.towerdungeon.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Large response bodies through the real server, byte for byte (issue #72, {@code
 * docs/research/undertow-starter-large-responses.md}).
 *
 * <p>The Undertow servlet bridge corrupts a response body when the producer is not the I/O thread
 * and the connection cannot take the whole body at once: over HTTP/2 (cleartext upgrade, the
 * default of {@code java.net.http.HttpClient}) that happens at every 16 KiB frame, over HTTP/1.1
 * only once the socket buffers fill (a few hundred KiB). The endpoint here produces its body on
 * another thread, as the MCP endpoint does. HTTP/2 is switched off in {@code application.yml} until
 * the library is fixed, so the sizes up to 64 KiB must come back intact whatever the client asks
 * for.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LargeResponseTest {
  @TestConfiguration
  static class Endpoint {
    @Bean
    RouterFunction<ServerResponse> largeBody() {
      return RouterFunctions.route()
          .GET(
              "/test-large/{kb}",
              request -> {
                int kb = Integer.parseInt(request.pathVariable("kb"));
                return Mono.fromCallable(
                        () -> new String(body(kb * 1024), StandardCharsets.US_ASCII))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(
                        text ->
                            ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(text));
              })
          .build();
    }
  }

  @LocalServerPort int port;

  /** A repeating pattern, so any corrupted, stale or missing byte is detected. */
  static byte[] body(int bytes) {
    var body = new byte[bytes];
    IntStream.range(0, bytes)
        .forEach(i -> body[i] = (byte) (i % 80 == 79 ? '\n' : 'a' + (i % 80) % 26));
    return body;
  }

  private void assertIntact(HttpClient.Version version, int kb) throws Exception {
    var client = HttpClient.newBuilder().version(version).build();
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/test-large/" + kb))
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).as("%d KiB over %s", kb, version).isEqualTo(body(kb * 1024));
  }

  @ParameterizedTest(name = "{0} KiB over HTTP/1.1")
  @ValueSource(ints = {8, 16, 17, 32, 64})
  void aBodyUpToSixtyFourKibIsIntactOverHttp11(int kb) throws Exception {
    assertIntact(HttpClient.Version.HTTP_1_1, kb);
  }

  /** Asks for the cleartext HTTP/2 upgrade, as the JDK client does by default. */
  @ParameterizedTest(name = "{0} KiB asking for HTTP/2")
  @ValueSource(ints = {8, 16, 17, 32, 64})
  void aBodyUpToSixtyFourKibIsIntactWhenTheClientAsksForHttp2(int kb) throws Exception {
    assertIntact(HttpClient.Version.HTTP_2, kb);
  }

  /**
   * Still corrupted over HTTP/1.1 by the same bug, once the socket buffers fill. Enable when the
   * library fix is in (see the research document), then also re-enable {@code server.http2}.
   */
  @Disabled(
      "#72: needs the undertow-servlet fix, see docs/research/undertow-starter-large-responses.md")
  @ParameterizedTest(name = "{0} KiB over HTTP/1.1, large")
  @ValueSource(ints = {600, 1024})
  void aLargeBodyIsIntactOverHttp11(int kb) throws Exception {
    assertIntact(HttpClient.Version.HTTP_1_1, kb);
  }

  @Disabled(
      "#72: needs the undertow-servlet fix, see docs/research/undertow-starter-large-responses.md")
  @ParameterizedTest(name = "{0} KiB over HTTP/2")
  @ValueSource(ints = {600, 1024})
  void aLargeBodyIsIntactOverHttp2(int kb) throws Exception {
    assertIntact(HttpClient.Version.HTTP_2, kb);
  }
}

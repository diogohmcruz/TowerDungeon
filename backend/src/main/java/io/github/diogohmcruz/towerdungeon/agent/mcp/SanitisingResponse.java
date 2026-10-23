package io.github.diogohmcruz.towerdungeon.agent.mcp;

import java.nio.charset.StandardCharsets;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Stands between the MCP library and the wire. A 2xx response passes untouched; for any 4xx or 5xx
 * the body the library wrote is discarded and a fixed one is written instead, because the library
 * serialises its exceptions with their stack traces (docs/research/mcp-binding-spike.md).
 */
final class SanitisingResponse extends ServerHttpResponseDecorator {
  SanitisingResponse(ServerHttpResponse delegate) {
    super(delegate);
  }

  private boolean failing() {
    var status = getStatusCode();
    return status != null && (status.is4xxClientError() || status.is5xxServerError());
  }

  @Override
  public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
    return failing()
        ? discard(Flux.from(body)).then(Mono.defer(this::fixed))
        : super.writeWith(body);
  }

  @Override
  public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
    return failing()
        ? discard(Flux.from(body).flatMap(Flux::from)).then(Mono.defer(this::fixed))
        : super.writeAndFlushWith(body);
  }

  @Override
  public Mono<Void> setComplete() {
    return failing() && !isCommitted() ? fixed() : super.setComplete();
  }

  private Mono<Void> discard(Flux<? extends DataBuffer> body) {
    return body.doOnNext(DataBufferUtils::release).then();
  }

  private Mono<Void> fixed() {
    var bytes = McpResponses.bodyFor(getStatusCode()).getBytes(StandardCharsets.UTF_8);
    getHeaders().setContentType(MediaType.APPLICATION_JSON);
    getHeaders().setContentLength(bytes.length);
    return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
  }
}

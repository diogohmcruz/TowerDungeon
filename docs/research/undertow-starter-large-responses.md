# Large HTTP responses are corrupted through the Undertow servlet bridge

Issue: diogohmcruz/TowerDungeon#72. Context: `docs/adr/0005-*.md` (Undertow on Boot 4 through the community starter).

Short version: a response body whose producer is not the Undertow I/O thread, and which the connection cannot take in one go, comes back corrupted (NUL bytes or stale bytes inserted, or truncated). Over HTTP/2 (including the cleartext `Upgrade: h2c` that `java.net.http.HttpClient` sends by default) that happens at every 16 KiB frame; over HTTP/1.1 only once the socket buffers fill (about 300 KiB on loopback). The defect is a one-line mistake in `io.undertow.servlet.spec.ServletOutputStreamImpl` (artifact `io.undertow.ee:undertow-servlet` 2.0.2.Final), reached through the starter's reactive-to-servlet bridge. It is not in the starter's own code, and no configuration removes it at all sizes.

## Versions

| Component | Version |
|---|---|
| Spring Boot | 4.1.1 (spring-web / spring-webflux 7.0.9, Reactor Core 3.8.7) |
| `io.undertow:undertow-spring-boot-starter` | 1.0.0.Alpha1-SNAPSHOT (local build, WebSocket patch of ADR 0005) |
| `io.undertow:undertow-core` | 2.4.3.Final |
| `io.undertow.ee:undertow-servlet` (Servlet 6.1) | 2.0.2.Final |
| XNIO | 3.8.16.Final |
| JVM | OpenJDK 26.0.2 (the project targets 24) |

## Symptom

- MCP: `td://glossary` read as 16,814 bytes of JSON fails with `Illegal unquoted character ((CTRL-CHAR, code 0))` in the client (`McpEndpointTest`, with 27 more CONTEXT.md terms shipped; the size was 11,411 bytes before and passed).
- The `Content-Length` header says 16,814. The JDK client (HTTP/2 upgrade) receives 33,178 bytes: the first 16,364 correct, then 16,364 NUL bytes, then the last 450 correct. curl (`--http2-prior-knowledge`) receives only the first 16,364 bytes and exits with 18 (partial file).
- Over HTTP/1.1 the same corruption shows from a few hundred KiB (the original report, "over about 0.5 MiB, with stale header text in the body"): the inserted block is a whole Undertow buffer (16,364 bytes by default) of whatever the pooled buffer held before, which is zeros for fresh memory and, for a recycled pooled buffer, old bytes such as earlier header text.

16,364 is Undertow's default buffer size: `Undertow.Builder` uses `16 * 1024 - 20` when the heap is over 128 MB (the 20 bytes are left for protocol headers).

## Minimal reproduction (outside the game code)

A Spring Boot 4.1.1 WebFlux app on the starter, with one endpoint that produces its body on another thread (as the MCP library does, and as any `publishOn`/`subscribeOn`/`delayElement` pipeline does) and one that produces it inline. Needs the starter in `~/.m2` (ADR 0005).

`pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
  </parent>
  <groupId>repro</groupId>
  <artifactId>undertow-large-response</artifactId>
  <version>1</version>
  <properties><java.version>24</java.version></properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webflux</artifactId>
      <exclusions>
        <exclusion>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-reactor-netty</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>io.undertow</groupId>
      <artifactId>undertow-spring-boot-starter</artifactId>
      <version>1.0.0.Alpha1-SNAPSHOT</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

`src/main/java/repro/ReproApp.java`:

```java
package repro;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
@RestController
public class ReproApp {
  public static void main(String[] args) {
    SpringApplication.run(ReproApp.class, args);
  }

  /** Repeating pattern (79 letters, newline), so every byte is predictable. */
  static String body(int kb) {
    byte[] b = new byte[kb * 1024];
    for (int i = 0; i < b.length; i++) b[i] = (byte) (i % 80 == 79 ? '\n' : 'a' + (i % 80) % 26);
    return new String(b, StandardCharsets.US_ASCII);
  }

  /** Body produced on the I/O thread. */
  @GetMapping(value = "/inline/{kb}", produces = MediaType.TEXT_PLAIN_VALUE)
  Mono<String> inline(@PathVariable int kb) {
    return Mono.just(body(kb));
  }

  /** Body produced on another thread. */
  @PostMapping(value = "/async/{kb}", produces = MediaType.APPLICATION_JSON_VALUE)
  Mono<String> async(@PathVariable int kb, @RequestBody String in) {
    return Mono.fromCallable(() -> body(kb)).subscribeOn(Schedulers.boundedElastic());
  }
}
```

Run, then check with curl (`want` is the expected size; `cmp` against a locally generated body shows the first bad byte):

```sh
mvn package -DskipTests
java -jar target/undertow-large-response-1.jar --server.port=18072 --server.http2.enabled=true &

for k in 8 15 16 17 32 64 256 320 600 1024; do
  for proto in --http1.1 --http2-prior-knowledge; do
    got=$(curl -s --max-time 20 $proto -X POST -H 'Content-Type: application/json' -d '{"a":1}' \
          -o /tmp/body.bin -w '%{size_download}' "http://127.0.0.1:18072/async/$k")
    nul=$(tr -cd '\000' < /tmp/body.bin | wc -c | tr -d ' ')
    echo "$proto ${k}K want=$((k*1024)) got=$got nul=$nul"
  done
done
```

The JDK client reproduces it too, because `HttpClient.newHttpClient()` asks for the cleartext HTTP/2 upgrade: send the same POST with `HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)` and compare `response.body()` with the pattern.

In this repository: `LargeResponseTest` (real server, random port, a test endpoint that produces on `boundedElastic`) and `McpEndpointTest.aResourceOverSixteenKibIsIntactWhateverTheClientAsksFor` run the same check. With `server.http2.enabled` back on (for example `SERVER_HTTP2_ENABLED=true mvn test -Dtest=LargeResponseTest`) the HTTP/2 cases from 16 KiB fail.

## Results

Default configuration (HTTP/2 enabled, `server.compression.enabled=true` as in `application.yml`), body produced on another thread (`POST /async/{kb}`):

| Size | HTTP/1.1, curl | HTTP/2 (curl prior knowledge, and JDK client upgrade) |
|---|---|---|
| 1, 8, 15 KiB | ok | ok |
| 16 KiB | ok | 16,364 bytes of 16,384 (curl); JDK: 16,364 good + 16,364 NUL + rest |
| 17 KiB | ok | 16,364 bytes of 17,408 (curl) |
| 32 KiB | ok | 32,728 of 32,768 (curl); one NUL block of 16,364 (JDK) |
| 64 KiB | ok | 65,456 of 65,536 (curl); NUL block of 16,364 (JDK) |
| 256 KiB | ok | corrupted |
| 320 KiB | NUL block, 468 to 611 NUL bytes | corrupted |
| 384 KiB and 448 KiB | 16,214 NUL bytes | corrupted |
| 512 KiB | 16,214 NUL bytes | corrupted |
| 600 KiB | 16,215 NUL bytes | corrupted |
| 1024 KiB | 16,214 NUL bytes | corrupted |
| 4096 KiB | 16,213 NUL bytes | corrupted |

The first failure over HTTP/1.1 lies between 256 and 320 KiB with curl on loopback; with the JDK client, which reads faster, 1 MiB passed in the same run. The threshold is the point where the socket send buffer is full when the body is written, so it depends on the reader and the OS. The producer thread is not essential over HTTP/1.1: the inline endpoint (`POST` echo variant) also failed at 320 and 512 KiB with curl. It is essential over HTTP/2: with the body produced inline (on the I/O thread) every size up to 1 MiB passed, over both versions, with or without compression and a gzip client, except two isolated failures in about ten runs (a 1 MiB `ServerResponse` and a 1 MiB chunked `Flux<DataBuffer>` with gzip), which are the same defect hitting a back-pressured write.

Controls, all correct at every size 1 KiB to 1 MiB over both versions: `Mono<String>` GET, `ResponseEntity<byte[]>` with an explicit `Content-Length`, `Flux<DataBuffer>` in 4 KiB chunks (chunked), `ServerResponse.bodyValue`, with and without `server.compression.enabled` and with a gzip client (a gzip client hides the defect for compressible bodies, because the compressed body is small).

Configuration workarounds tried on the unpatched libraries, same `POST /async/{kb}` check, curl (`ok` = correct to the byte; sizes in KiB):

| Configuration | HTTP/1.1 | HTTP/2 |
|---|---|---|
| default | ok to 256 (see above); bad from 320 | bad from 16 |
| `server.http2.enabled=false` | ok to 64, bad at 600+ | not served (no h2) |
| `server.undertow.buffer-size=8192` | ok to 64, bad at 600 and 4096 (1024 ok, a multiple of the buffer) | bad from 16 (an 8,192-byte NUL block) |
| `server.undertow.buffer-size=16384` | not run | 16 ok, 17 truncated to 16,384, 64 and 1024 ok |
| `server.undertow.buffer-size=65536` | ok to 64, bad at 600+ | ok to 64, bad at 600+ |
| `server.undertow.buffer-size=1048576` | ok to 600, bad at 1024 and 4096 | ok to 1024, bad at 4096 |
| `server.http2.enabled=false` and `buffer-size=1048576` | ok to 1024, bad at 4096 | not served |
| `server.undertow.direct-buffers=false` | same as default | same as default |
| `server.undertow.threads.io=1` | same as default | same as default |
| `server.undertow.threads.worker=1` | same as default | same as default |

(`always-set-keep-alive` and a response `Content-Length` are irrelevant: the explicit `Content-Length` control passes only because it is produced inline, and the MCP response already carries one.) Nothing removes the defect at all sizes; a bigger buffer only moves the first bad size up, at a cost of one buffer of that size per in-flight response. Switching HTTP/2 off removes the 16 KiB failures, which are the ones a client can hit with ordinary payloads, and leaves the HTTP/1.1 failures at several hundred KiB.

## Cause

Trace of the failing request (prints added to a copy of `ServletOutputStreamImpl`, XNIO and the starter unchanged):

```
TRACE write(ByteBuffer[]) res==0  thr=boundedElastic-1  writtenSoFar=16364 toWrite=17408 bufferRemainingBeforeCompact=0 userRemaining=1044
TRACE close   listener=true       thr=XNIO-1 I/O-1       bufPos=0 written=17408
TRACE closeAsync-enter            thr=XNIO-1 I/O-1       bufPos=0 written=17408
TRACE flushBufferAsync final=true thr=XNIO-1 I/O-1       pending=false
```

The path, from the response to the wire:

1. `UndertowReactiveWebServerFactory.ReactiveHttpHandlerFactory.getHandler` (starter, module `undertow-spring-boot-autoconfigure`) deploys Spring's `ServletHttpHandlerAdapter` as a servlet in Undertow's servlet container, with `setAsyncSupported(true)`. Every reactive response is therefore written through the Servlet API.
2. Spring's `ServletServerHttpResponse.writeToOutputStream` (spring-web 7.0.9, line 214) puts each `DataBuffer` on the servlet stream with `ServletOutputStream.write(ByteBuffer)` while `isReady()` is true, in async mode (a `WriteListener`).
3. Undertow's `ServletOutputStreamImpl.write(ByteBuffer)` calls `write(ByteBuffer[])`, async branch (listener set). When the body does not fit in the stream's own buffer it flips that buffer, builds `bufs = [buffer, user buffers...]` and calls `channel.write(bufs)` in a loop.
4. `AbstractFramedStreamSinkChannel.write` (undertow-core, used by HTTP/2) returns 0 when `safeToSend()` is false: after `handleBufferFull()` has queued a full 16,364-byte frame, `readyForFlush` stays set until the I/O thread has flushed it. From the I/O thread the frame is flushed inline so this is rare; from another thread it is the normal case after the first full buffer. On HTTP/1.1 the equivalent is a full socket send buffer.
5. On `res == 0` the method copies the unwritten user bytes into a new buffer, stores `this.buffersToWrite = { buffer, copy }`, clears `FLAG_READY` and returns so that `WriteChannelListener.handleEvent` finishes the write later.
6. The method's `finally` block then runs `buffer.compact()`. That is right for a normal exit, where `buffer` has been fully written and compact resets it for reuse. After the early return it is wrong: `buffer` is the very buffer saved in `buffersToWrite[0]`, and `compact()` turns it from read mode (position at the unwritten bytes, limit at the end of the data) into write mode (position = number of unwritten bytes, limit = capacity). When `WriteChannelListener.handleEvent` later calls `channel.write(buffersToWrite)`, it writes `position..capacity` of the buffer, which is garbage: 16,364 bytes of zeros or of whatever the pooled buffer held before. The unwritten data that was in `buffer` is skipped, and the stream has now sent more bytes than `Content-Length` (HTTP/2 clients then reset the stream or stop at the declared length, which is why curl got 16,364 bytes and the JDK client got the padded body).

The sibling method `writeAsync(byte[], int, int)` has the same structure and does it right: it keeps a `clearBuffer` flag, sets it to false on the `res == 0` path and compacts only `if (clearBuffer && buffer != null)`. The `ByteBuffer[]` variant lacks that flag. Spring only calls the `ByteBuffer` variant, so every reactive response is exposed.

Why the MCP glossary was the first victim: `McpEndpointTest` uses `JdkClientHttpConnector`, whose client asks for the HTTP/2 upgrade, and the MCP library produces its response on a scheduler thread. 16,814 bytes is one frame and a bit, so the second write hits `res == 0`. Why the earlier real payloads were fine: below one full buffer nothing goes to the channel before `close`.

Neither the starter's configuration nor its source is at fault, but the starter is what makes Spring route reactive output through the servlet stream, and `undertow-spring-boot-starter` 1.0.0 would carry the defect to users with it unless the servlet module is fixed or pinned.

## Proposed change to the library

Repository and artifact: Undertow EE, `io.undertow.ee:undertow-servlet`, class `io.undertow.servlet.spec.ServletOutputStreamImpl`, method `write(ByteBuffer[])`, async branch. Same fix as `writeAsync(byte[], int, int)` already contains:

```diff
@@ public void write(ByteBuffer[] buffers) throws IOException {
                 } else {
                     final ByteBuffer[] bufs = new ByteBuffer[buffers.length + 1];
                     buffer.flip();
+                    boolean clearBuffer = true;
                     try {
                         bufs[0] = buffer;
                         System.arraycopy(buffers, 0, bufs, 1, buffers.length);
@@
                                 this.buffersToWrite = new ByteBuffer[] { buffer, copy };
                                 clearFlags(FLAG_READY);
+                                clearBuffer = false;
                                 channel.resumeWrites();
                                 return;
                             }
                         } while (written < toWrite);
                     } finally {
-                        if (buffer != null)
+                        if (clearBuffer && buffer != null)
                             buffer.compact();
                     }
```

With this patch applied to a copy of the class placed first on the classpath, the repro above is correct at every size from 1 KiB to 4 MiB, over HTTP/1.1 and HTTP/2, with all the endpoint kinds, with and without compression (checked: 7 endpoint kinds by 10 sizes by both versions and a gzip client, plus 1, 8, 16, 17, 32, 64, 600, 1024 and 4096 KiB with curl).

Suggested regression test upstream: an async servlet whose `WriteListener` writes a `ByteBuffer` larger than the stream buffer from a non-I/O thread to a client that reads slowly (or over HTTP/2 with a 16 KiB frame), then compares the received bytes with the sent ones.

Related things to check in the same class while there: `write(byte[], int, int)`'s blocking path `writeTooLargeForBuffer` also compacts in a `finally`, but it is blocking and writes everything before returning, so it is not affected; `WriteChannelListener.handleEvent` clears `buffer` after draining `buffersToWrite`, which is consistent with the fix (the saved buffer is still in read mode).

## What this repository does

- `application.yml`: `server.http2.enabled: false`, with a comment pointing here. This removes the 16 KiB failures (the ones ordinary payloads hit, including the glossary and the JDK-client tests) and leaves the HTTP/1.1 failure above roughly 300 KiB, which no response in the game approaches (exports are tens of KiB) and no configuration removes. HTTP/2 is not used by browsers without TLS and the application serves no TLS.
- `LargeResponseTest`: 8, 16, 17, 32 and 64 KiB over HTTP/1.1 and asking for HTTP/2, on the real server; they fail with `server.http2.enabled=true`. The 600 and 1024 KiB cases are `@Disabled` until the library is fixed.
- `McpEndpointTest.aResourceOverSixteenKibIsIntactWhateverTheClientAsksFor`: the glossary read over HTTP/1.1 and over the HTTP/2 upgrade is byte-identical (meaningful once the glossary is over 16 KiB).
- When the library is fixed (a release of `undertow-servlet` with the change above, or the local build patched): set `server.http2.enabled` back to `true` if wanted, enable the two `@Disabled` tests, and delete the comment in `application.yml`.

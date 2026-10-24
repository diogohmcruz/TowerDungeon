# Getting the Undertow response corruption fixed upstream

Issue: diogohmcruz/TowerDungeon#72. Our reproduction, size tables and trace are in [undertow-starter-large-responses.md](undertow-starter-large-responses.md); they are not repeated here. Context: `docs/adr/0005-*.md`. Checked on 2026-10-24.

Nothing in this document was filed or pushed upstream. The patch and the test exist only as a local commit in a scratch clone (see "Verification you actually ran").

## Verdict

- It can be fixed upstream, and the fix is the one line (plus a flag) our research proposed. The defect is in `io.undertow.servlet.spec.ServletOutputStreamImpl.write(ByteBuffer[])` in the `undertow-io/undertow-ee` repository (artifact `io.undertow.ee:undertow-servlet`), not in undertow-core and not in the starter.
- It is not fixed in any release or in the current source. The class is byte-identical in `2.0.2.Final` (what the starter 1.0.0.Alpha1 uses), `2.0.3.Final` (released on Maven Central 2026-10-04, the newest) and `main` (`2.0.4.Final-SNAPSHOT`, commit `4c001222`, 2026-10-09). I found no Jira issue, GitHub issue or PR about it.
- With the patch, a new regression test goes from 5 failures to 5 passes, the whole `undertow-servlet` module passes (385 run, 0 failures, 7 skipped), and our own reproduction is correct from 8 KiB to 4 MiB over HTTP/1.1 and HTTP/2 (curl), where the unpatched `2.0.2.Final` fails 11 of 20 cases.

The same code is also present in `undertow-io/undertow-ee` branch `1.0.x` (Servlet 6.0) and in `undertow-io/undertow` `main` and `2.3.x` (their `servlet` module, same method, same missing flag, checked by grepping the raw files). I did not run tests there.

## Where to file

| What | Where | Notes |
|---|---|---|
| Issue | Red Hat Jira, project `UNDERTOW`: https://redhat.atlassian.net/projects/UNDERTOW (the old `issues.redhat.com` URL in CONTRIBUTING redirects here) | `undertow-io/undertow-ee` has GitHub Issues disabled. Needs an Atlassian account. Type `Bug`, component `Servlet` (the component used by UNDERTOW-2693), fix version `EE 2.0.4.Final` (and `EE 1.0.4.Final` for the 1.0.x branch; the maintainers set these). |
| Pull request | https://github.com/undertow-io/undertow-ee, target branch `main` | CONTRIBUTING says `master`, but the repository's branch is `main`. Commit message starts with the Jira key, `[UNDERTOW-NNNN] ...` (see the repository history). Maintainers backport to `1.0.x` ("maintenance branch" label). |
| Discussion (optional) | Zulip, https://wildfly.zulipchat.com/#narrow/stream/174183-undertow | CONTRIBUTING lists it. |
| Starter | https://github.com/undertow-io/spring-boot-undertow | No change needed there. Once `undertow-servlet` is released, the starter bumps `version.io.undertow.ee` (it is `2.0.2.Final` in `pom.xml`). Optionally a comment or issue there so it bumps. |

What CONTRIBUTING requires (both repositories, same text):

- The PR states what it does, points to the Jira issue, contains a test case, has a license header with the current year in new files (the test below has `2026`), and passes CI. CI is `mvn -B -fae -DskipTests clean install` on JDK 17 and then the tests (`.github/workflows/ci.yml`); the module is compiled with `maven.compiler.release` 17.
- Code style consistent with the class being edited; the review criteria include correctness, scope of the fix (the reviewers ask whether the same bug repeats elsewhere), and backwards compatibility.
- DCO: "All contributions are subject to the Developer Certificate of Origin" (`dco.txt`). There is no CLA and no automated DCO check in the workflows, but sign the commit off (`git commit -s`) with your own name and e-mail.
- Rebase on the target branch before submitting; tick "Allow edits from maintainers".

The scratch commit carries a `Signed-off-by` and a trailer that were added by tooling in this environment, with a placeholder Jira key `UNDERTOW-XXXX`. Re-create the commit under your own identity (`git commit -s`, key from the Jira issue) instead of sending that one.

## Issue text ready to paste

Title:

```
ServletOutputStreamImpl.write(ByteBuffer[]) in async mode corrupts the response when the channel returns 0: the buffer it queues for WriteChannelListener is compacted
```

Body (Jira wiki markup is not needed; Markdown-ish text is readable in the description field):

```
Summary
-------
With a WriteListener set (non-blocking mode), ServletOutputStreamImpl.write(ByteBuffer[]) (and so write(ByteBuffer)) sends wrong bytes when channel.write(...) returns 0 while the body is being written. The response then contains a block of stale buffer content (NUL bytes, or whatever the pooled buffer held earlier) in place of the data that was not written, and it is longer than the declared Content-Length. Over HTTP/2 this happens at every 16 KiB frame when the writer is not the I/O thread; over HTTP/1.1 it happens once the socket send buffer is full.

Versions
--------
io.undertow.ee:undertow-servlet 2.0.2.Final, 2.0.3.Final and main (4c001222, 2.0.4.Final-SNAPSHOT): same code.
io.undertow:undertow-core 2.4.3.Final (2.4.4.Final on main), XNIO 3.8.16.Final, OpenJDK 26 (also seen with the project target, 24).
Found through io.undertow:undertow-spring-boot-starter 1.0.0.Alpha1 with Spring Boot 4.1.1 WebFlux, where every reactive response is written through ServletOutputStream.write(ByteBuffer) in async mode.
The branch 1.0.x and undertow-io/undertow main and 2.3.x have the same code in the same method.

Steps to reproduce
------------------
Self-contained (undertow-ee, no Spring): the test in the attached patch,
servlet/src/test/java/io/undertow/servlet/test/streams/ServletOutputStreamAsyncByteBufferTestCase.java
  mvn -pl servlet -am -Dtest=ServletOutputStreamAsyncByteBufferTestCase -Dsurefire.failIfNoSpecifiedTests=false test

It deploys an async servlet. In doGet it calls req.startAsync() and sets a WriteListener. onWritePossible() hands the work to another thread, which loops "while (out.isReady()) out.write(ByteBuffer.wrap(data, pos, n))" (as Spring's ServletServerHttpResponse does for each DataBuffer) and calls AsyncContext.complete() at the end. The response sets Content-Length. The client is java.net.http.HttpClient, with HTTP_2 (cleartext upgrade, h2c) or HTTP_1_1 (the test sleeps 1 s before reading so the socket buffers fill).

Expected: the client receives exactly the bytes written.
Actual (unpatched main), all five cases:
  h2c, one 17 KiB write:   17,408 expected, 33,772 received, first difference at byte 16,364
  h2c, one 1 MiB write:    1,048,576 expected, 1,064,940 received, first difference at 16,364
  h2c, 5,000-byte writes:  1,048,576 expected, 1,713,160 received, first difference at 16,364
  HTTP/1.1, 16 MiB, slow reader, 4 MiB writes:  same length, first difference at byte 327,066
  HTTP/1.1, 16 MiB, slow reader, 50,000-byte writes: same length, first difference at byte 1,360,672
(16,364 is Undertow's default buffer size, 16 * 1024 - 20.) curl --http2-prior-knowledge stops at 16,364 bytes with exit code 18 because the stream carries more than Content-Length.

Cause
-----
In write(ByteBuffer[]), async branch (lines 349-387 on main):

    buffer.flip();                               // buffer is now in read mode
    try {
        bufs[0] = buffer; ...
        do {
            res = channel.write(bufs);
            written += res;
            if (res == 0) {
                final ByteBuffer copy = ByteBuffer.allocate(...);   // the unwritten user bytes
                ...
                this.buffersToWrite = new ByteBuffer[] { buffer, copy };
                clearFlags(FLAG_READY);
                channel.resumeWrites();
                return;
            }
        } while (written < toWrite);
    } finally {
        if (buffer != null)
            buffer.compact();                    // also runs after the return above
    }

On the "res == 0" path the stream's own buffer is stored in buffersToWrite and is still in read mode (position..limit are the bytes still to send). The finally block then calls compact(), which turns it into write mode (position = number of unwritten bytes, limit = capacity). WriteChannelListener.handleEvent later runs channel.write(buffersToWrite) and sends position..capacity of that buffer: stale bytes, not the unwritten ones, and up to a full buffer of them. When the buffer had been fully written before the zero (as in the h2c case, where a 16,364-byte frame is complete and the channel is not yet writable) compact() just resets it to 0..capacity and a whole buffer of stale content is sent.

channel.write(...) returning 0 is normal: AbstractFramedStreamSinkChannel.write returns 0 while a full frame is waiting to be flushed by the I/O thread (that is the usual case when the writer is another thread), and on HTTP/1.1 a full socket buffer does the same.

writeAsync(byte[], int, int) in the same class already handles this: it has a clearBuffer flag, sets it to false on the res == 0 path and compacts only "if (clearBuffer && buffer != null)" (introduced for the byte[] path by UNDERTOW-2425, commit cb0bef2b; the ByteBuffer[] path was not given the flag).

Fix
---
Same flag in write(ByteBuffer[]). Patch and test attached (patch is 5 lines in the main class).
```

## The patch

Against `undertow-io/undertow-ee` `main` at `4c001222`; it applies unchanged to the `2.0.2.Final` and `2.0.3.Final` tags (I applied it to `2.0.2.Final`). Main-code change only; the test is in the next section.

Commit message to use (replace the key):

```
[UNDERTOW-NNNN] ServletOutputStreamImpl.write(ByteBuffer[]) in async mode compacts the buffer it has just queued for the WriteChannelListener

When the channel returns 0 from write(ByteBuffer[]) with a WriteListener set,
the stream's own buffer (in read mode, holding the unwritten bytes) is stored
in buffersToWrite and the method returns. The finally block then compacted it,
turning the unwritten bytes into garbage: WriteChannelListener wrote
position..capacity of the buffer instead of the bytes that were not written.
writeAsync(byte[], int, int) already avoids this with a clearBuffer flag. Add
the same flag. Add a test that writes ByteBuffers larger than the buffer from
a non-I/O thread over HTTP/1.1 (slow reader) and h2c.
```

Diff:

```diff
diff --git a/servlet/src/main/java/io/undertow/servlet/spec/ServletOutputStreamImpl.java b/servlet/src/main/java/io/undertow/servlet/spec/ServletOutputStreamImpl.java
index 48270e96..8095c32f 100644
--- a/servlet/src/main/java/io/undertow/servlet/spec/ServletOutputStreamImpl.java
+++ b/servlet/src/main/java/io/undertow/servlet/spec/ServletOutputStreamImpl.java
@@ -353,6 +353,7 @@ public class ServletOutputStreamImpl extends ServletOutputStream implements Buff
                 } else {
                     final ByteBuffer[] bufs = new ByteBuffer[buffers.length + 1];
                     buffer.flip();
+                    boolean clearBuffer = true;
                     try {
                         bufs[0] = buffer;
                         System.arraycopy(buffers, 0, bufs, 1, buffers.length);
@@ -373,12 +374,14 @@ public class ServletOutputStreamImpl extends ServletOutputStream implements Buff
                                 copy.flip();
                                 this.buffersToWrite = new ByteBuffer[] { buffer, copy };
                                 clearFlags(FLAG_READY);
+                                // buffer is now owned by buffersToWrite, still in read mode: do not compact it
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
                 }
```

How the fix was re-derived from the clone (not taken from our earlier document):

- `write(ByteBuffer[])`, async branch, `ServletOutputStreamImpl.java` lines 345-388 at `4c001222`. Line 355 `buffer.flip()` puts the stream buffer in read mode. Lines 364-379 loop on `channel.write(bufs)`. On `res == 0`, line 374 stores `this.buffersToWrite = new ByteBuffer[] { buffer, copy }`, line 375 clears `FLAG_READY`, line 376 calls `channel.resumeWrites()` and line 377 returns. The `finally` at lines 380-383 runs on that return and calls `buffer.compact()` on the buffer just stored.
- `WriteChannelListener.handleEvent`, lines 873-894: `channel.write(buffersToWrite)` until drained, then `buffersToWrite = null; buffer.clear();`. It expects `buffersToWrite[0]` to still be in read mode, and it clears the buffer itself afterwards, so skipping `compact()` leaves nothing for the early return to restore.
- `flushBufferAsync`, lines 420-455, does the same hand-over (`buffersToWrite = bufs` on `res == 0`) and has no `compact()` on that path.
- `writeAsync(byte[], int, int)`, lines 245-291: `clearBuffer` declared at 257, set false at 278 on the `res == 0` path, read in the `finally` at 283. The patch gives `write(ByteBuffer[])` the same shape.
- Partial writes before the zero are fine: `bufs` holds the same `ByteBuffer` objects as the caller's `buffers`, so their positions have advanced by what was written, and `Buffers.copy(copy, buffers, ...)` copies only what remains.
- Not the right place for the fix: undertow-core. `AbstractFramedStreamSinkChannel.write` returning 0 when a frame is waiting is the normal non-blocking contract. Not the starter either; it only chooses to route reactive output through the servlet stream.
- The blocking branch (`listener == null`) uses `buffer.clear()` in its `finally` and writes everything before returning, so it is not affected. The other `compact()` calls (lines 232 and 614, blocking paths that finish writing before they return; line 549 in `flush()`, which deliberately compacts so the unwritten bytes stay at the front of the buffer and never hands the buffer to `buffersToWrite`) are correct as they are (read, not run).

## The regression test

New file `servlet/src/test/java/io/undertow/servlet/test/streams/ServletOutputStreamAsyncByteBufferTestCase.java`. JUnit 4 like the rest of the module. It starts its own `Undertow` server (HTTP/2 enabled, plain HTTP listener, random port) with the servlet deployed through `ServletContainer`, instead of `DefaultServer`, because `DefaultServer` serves HTTP/1.1 only and the test needs h2c. The client is the JDK `HttpClient`; the test asserts `response.version()` equals the version asked for, so the h2c upgrade really happened.

```java
/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.test.streams;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that a response written through {@link ServletOutputStream#write(ByteBuffer)} from a thread that is not an
 * I/O thread, in non-blocking mode, arrives intact when the channel cannot take all of it at once.
 * <p>
 * When the channel returns zero from the write, {@code ServletOutputStreamImpl} hands the unwritten bytes to the
 * {@code WriteChannelListener}. The stream's own buffer must stay in read mode until then, otherwise the listener
 * sends the stale tail of the buffer in place of the data that was not written.
 * <p>
 * HTTP/2 over cleartext (h2c, through the upgrade) reaches that state at every frame, because the frame is flushed
 * by the I/O thread and not by the writer. HTTP/1.1 reaches it once the socket buffers are full, which the test
 * forces by not reading the response for a while.
 */
public class ServletOutputStreamAsyncByteBufferTestCase {

    private static final String PATH = "/ctx/async-bytebuffer";

    private static Undertow server;
    private static int port;
    private static ExecutorService writers;

    /** The same expected body on both sides. It has no zero bytes and it does not repeat within a 16 KiB frame. */
    static byte[] body(final int size) {
        final byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (1 + (i * 31 + (i >>> 8)) % 251);
        }
        return data;
    }

    @BeforeClass
    public static void setup() throws Exception {
        writers = Executors.newCachedThreadPool(r -> {
            final Thread t = new Thread(r, "async-bytebuffer-writer");
            t.setDaemon(true);
            return t;
        });
        final ServletContainer container = ServletContainer.Factory.newInstance();
        final DeploymentInfo info = new DeploymentInfo()
                .setClassLoader(ServletOutputStreamAsyncByteBufferTestCase.class.getClassLoader())
                .setContextPath("/ctx")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("async-bytebuffer.war")
                .addServlet(new ServletInfo("async-bytebuffer", OffIoThreadByteBufferServlet.class)
                        .addMapping("/async-bytebuffer")
                        .setAsyncSupported(true));
        final DeploymentManager manager = container.addDeployment(info);
        manager.deploy();
        final PathHandler root = new PathHandler().addPrefixPath("/ctx", manager.start());
        server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setHandler(root)
                .build();
        server.start();
        port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
    }

    @AfterClass
    public static void cleanup() {
        if (server != null) {
            server.stop();
        }
        if (writers != null) {
            writers.shutdownNow();
        }
    }

    @Test
    public void testHttp2cSingleWriteLargerThanOneFrame() throws Exception {
        check(HttpClient.Version.HTTP_2, 17 * 1024, 17 * 1024, false);
    }

    @Test
    public void testHttp2cSingleWriteOfOneMiB() throws Exception {
        check(HttpClient.Version.HTTP_2, 1024 * 1024, 1024 * 1024, false);
    }

    @Test
    public void testHttp2cManyWrites() throws Exception {
        check(HttpClient.Version.HTTP_2, 1024 * 1024, 5000, false);
    }

    @Test
    public void testHttp11SlowReaderSingleWrites() throws Exception {
        check(HttpClient.Version.HTTP_1_1, 16 * 1024 * 1024, 4 * 1024 * 1024, true);
    }

    @Test
    public void testHttp11SlowReaderManyWrites() throws Exception {
        check(HttpClient.Version.HTTP_1_1, 16 * 1024 * 1024, 50000, true);
    }

    private void check(final HttpClient.Version version, final int size, final int chunk, final boolean slowReader)
            throws Exception {
        final HttpClient client = HttpClient.newBuilder().version(version).build();
        final HttpRequest request = HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:" + port + PATH + "?size=" + size + "&chunk=" + chunk))
                .version(version)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        final HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            Assert.assertEquals(200, response.statusCode());
            // h2c means the upgrade really happened, so that the HTTP/2 path is the one under test
            Assert.assertEquals(version, response.version());
            if (slowReader) {
                // let the socket buffers fill, so that the server writes return zero
                Thread.sleep(1000);
            }
            final byte[] actual = in.readAllBytes();
            final byte[] expected = body(size);
            if (actual.length != expected.length) {
                Assert.fail("expected " + expected.length + " bytes but received " + actual.length
                        + "; first difference at " + firstDifference(expected, actual));
            }
            final int diff = firstDifference(expected, actual);
            Assert.assertEquals("first differing byte", -1, diff);
        }
    }

    private static int firstDifference(final byte[] expected, final byte[] actual) {
        final int n = Math.min(expected.length, actual.length);
        for (int i = 0; i < n; i++) {
            if (expected[i] != actual[i]) {
                return i;
            }
        }
        return expected.length == actual.length ? -1 : n;
    }

    /**
     * Async servlet that writes the body from a thread of its own, as a reactive framework would: every
     * {@code onWritePossible} hands the writing to another thread, which writes while {@code isReady()} is true.
     */
    public static class OffIoThreadByteBufferServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
                throws ServletException, IOException {
            final int size = Integer.parseInt(req.getParameter("size"));
            final int chunk = Integer.parseInt(req.getParameter("chunk"));
            final byte[] data = body(size);
            resp.setContentType("application/octet-stream");
            resp.setContentLengthLong(size);
            final AsyncContext context = req.startAsync();
            context.setTimeout(0);
            final ServletOutputStream out = resp.getOutputStream();
            out.setWriteListener(new WriteListener() {
                private int position;
                private boolean completed;

                @Override
                public void onWritePossible() {
                    writers.execute(this::pump);
                }

                private synchronized void pump() {
                    try {
                        while (!completed && out.isReady()) {
                            if (position == size) {
                                completed = true;
                                context.complete();
                                return;
                            }
                            final int length = Math.min(chunk, size - position);
                            out.write(ByteBuffer.wrap(data, position, length));
                            position += length;
                        }
                    } catch (IOException e) {
                        onError(e);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    t.printStackTrace();
                    context.complete();
                }
            });
        }
    }
}
```

## Verification you actually ran

Environment: macOS, OpenJDK 26.0.2 (Temurin), Apache Maven 3.9.16 unpacked under the job directory (there was no `mvn` on the machine; I did not use the repository's `mvnw`, which would have written to `~/.m2/wrapper`). `-Dmaven.repo.local` pointed at directories under the job's `tmp`; `~/.m2` was only read (to copy the locally built starter into the temporary repository). Clone: `undertow-io/undertow-ee` at `4c001222` (main, 2026-10-09), branch `fix-async-bytebuffer-array-write-compact`, local commit `bce4592b`. Tags checked: `2.0.2.Final` (`87b6f1a7`), `2.0.3.Final`.

1. The class is unchanged between releases and main:

   ```
   git diff 2.0.2.Final HEAD -- servlet/src/main/java/io/undertow/servlet/spec/ServletOutputStreamImpl.java   (empty)
   ```

2. New test without the patch (main as cloned):

   ```
   mvn -B -f undertow-ee/pom.xml -pl servlet -am -Dtest=ServletOutputStreamAsyncByteBufferTestCase -Dsurefire.failIfNoSpecifiedTests=false test
   [ERROR] Tests run: 5, Failures: 5, Errors: 0, Skipped: 0
   testHttp2cSingleWriteLargerThanOneFrame   expected 17408 bytes but received 33772; first difference at 16364
   testHttp2cSingleWriteOfOneMiB             expected 1048576 bytes but received 1064940; first difference at 16364
   testHttp2cManyWrites                      expected 1048576 bytes but received 1713160; first difference at 16364
   testHttp11SlowReaderSingleWrites          first differing byte expected:<-1> but was:<327066>
   testHttp11SlowReaderManyWrites            first differing byte expected:<-1> but was:<1360672>
   BUILD FAILURE
   ```

3. Same command with the patch applied:

   ```
   [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.673 s -- in io.undertow.servlet.test.streams.ServletOutputStreamAsyncByteBufferTestCase
   BUILD SUCCESS
   ```

4. The whole `undertow-servlet` module with the patch and the new test (`mvn -B -o -pl servlet test`):

   ```
   [WARNING] Tests run: 385, Failures: 0, Errors: 0, Skipped: 7
   BUILD SUCCESS   (2:23 min)
   ```

   The 7 skipped are in the existing suite; I did not check that they are skipped on an unpatched tree too. The `checkstyle:check-style` goal ran as part of the build and did not fail it. I did not run `websockets`, `examples`, the `1.0.x` branch, the nightly integration workflow, or any JDK other than 26 (CI uses 17).

5. Our own reproduction (the app in the research document, unchanged) against `undertow-servlet` `2.0.2.Final` and against the same tag with the patch applied (built from the `2.0.2.Final` tag, version renamed to `2.0.2.Final-patched`, installed into a temporary repository, put in the app's `pom.xml` as a direct dependency so it overrides the starter's transitive `2.0.2.Final`). The starter is the local `1.0.0.Alpha1-SNAPSHOT` from ADR 0005. Server started with `--server.http2.enabled=true`; each case POSTs to `/async/{kb}` (body produced on `boundedElastic`), curl, byte-for-byte comparison with the generated pattern.

   Unpatched `2.0.2.Final`, 11 of 20 cases bad (HTTP/1.1 column ok to 256 KiB, as in the research document):

   ```
   --http2-prior-knowledge     16K want=   16384 got=   16364 nul=     0 curl_exit=18 BAD first_diff=16364
   --http2-prior-knowledge     17K want=   17408 got=   16364 nul=     0 curl_exit=18 BAD first_diff=16364
   --http2-prior-knowledge     32K want=   32768 got=   32728 nul= 16215 curl_exit=18 BAD first_diff=16364
   --http2-prior-knowledge     64K want=   65536 got=   65456 nul= 16215 curl_exit=18 BAD first_diff=16364
   --http2-prior-knowledge    256K want=  262144 got=  261824 nul= 16214 curl_exit=18 BAD first_diff=16364
   --http1.1                  600K want=  614400 got=  614400 nul= 16364 curl_exit=0 BAD first_diff=327062
   --http2-prior-knowledge    600K ... BAD    (1024K and 4096K the same, both versions)
   bad cases: 11
   ```

   Patched (`BOOT-INF/lib/undertow-servlet-2.0.2.Final-patched.jar` in the app jar), 8, 15, 16, 17, 32, 64, 256, 600, 1024 and 4096 KiB over both versions:

   ```
   --http2-prior-knowledge    16K want=   16384 got=   16384 nul=     0 curl_exit=0 OK
   --http2-prior-knowledge    17K want=   17408 got=   17408 nul=     0 curl_exit=0 OK
   --http2-prior-knowledge  1024K want= 1048576 got= 1048576 nul=     0 curl_exit=0 OK
   --http1.1                4096K want= 4194304 got= 4194304 nul=     0 curl_exit=0 OK
   bad cases: 0
   ```

   (Only a sample of the 20 lines is shown; all 20 were `OK`.) I did not run the JDK-client variant of the app check, nor the TowerDungeon backend tests (`LargeResponseTest`, `McpEndpointTest`) against the patched jar.

6. Jira and release searches (anonymous REST queries on `redhat.atlassian.net`, 2026-10-24): `text ~ "ServletOutputStreamImpl"` (the 40 newest hits, which is the query cap; none about this, the older ones are from 2014 to 2017 and I read only their titles), `text ~ "buffersToWrite"` (0), `text ~ "WriteListener"` (10). The closest are UNDERTOW-1103 (2017, "WriteListener causes incorrect data", a thread-safety fix between `flushBufferAsync` and the listener, resolved 2.0.0.Beta1), UNDERTOW-2425 (2023, the `clearBuffer` change for the `byte[]` path only), UNDERTOW-2424 (malformed HTTP/1.1 responses under load, chunked conduit, different cause) and UNDERTOW-2785 (JVM crash from buffer use, different). GitHub: `undertow-io/spring-boot-undertow` has no issue about it; `undertow-io/undertow-ee` has issues disabled, and no PR in its list touches this method. Maven Central metadata: `undertow-servlet` newest `2.0.3.Final`, `undertow-core` newest `2.4.4.Final`; the starter is not on Maven Central under `io.undertow` (404), so `1.0.0.Alpha1` is not downloadable from there as of today.

Saved in the job directory (`$CLAUDE_JOB_DIR/tmp`, not in the repository): `undertow-ee` (clone with the branch), `0001-fix.patch` (`git format-patch`, includes the test), `run-unpatched.log`, `run-patched.log`, `run-full.log`.

## Workarounds until released

- What this repository does now: `server.http2.enabled: false`. That removes the 16 KiB failures; HTTP/1.1 still fails above roughly 300 KiB when the client reads slowly, which nothing in the game approaches (see the research document).
- Run on a locally patched `undertow-servlet`: build `io.undertow.ee:undertow-servlet` from the `2.0.2.Final` or `2.0.3.Final` tag with the patch above, give it a version of your own (for example `2.0.2.Final-patched`), install it, and add it to `backend/pom.xml` as a direct dependency so it wins over the starter's transitive `2.0.2.Final`. This is what step 5 above did. It needs the same treatment on CI as the starter snapshot already needs (ADR 0005: the starter is not on Central either).
- A copy of the patched class first on the classpath (what the research did): works, but is easy to forget; prefer the version override.
- Not workarounds: a bigger `undertow.server.buffer-size` only moves the first failing size, `direct-buffers`, thread counts and compression do not remove it (gzip hides it for compressible bodies only). Details in the research document.
- Not available to us: making Spring write from the I/O thread. Reactor decides the thread.

## Steps to re-enable HTTP/2 here

When a release of `io.undertow.ee:undertow-servlet` with the fix exists (2.0.4.Final or later; check Maven Central) and the starter uses it, or when a locally patched build is in use:

1. `backend/pom.xml`: bump `undertow-starter.version` to the starter release that depends on the fixed servlet, or add the fixed `io.undertow.ee:undertow-servlet` as a direct dependency (line 36 holds the starter version property; the TODO above the dependency also refers to ADR 0005). Update ADR 0005 consequences.
2. First, with the application config unchanged, prove it: `SERVER_HTTP2_ENABLED=true mvn test -Dtest=LargeResponseTest` in `backend`. The HTTP/2 cases from 16 KiB (8, 16, 17, 32, 64 KiB, "asking for HTTP/2") must pass.
3. `backend/src/main/resources/application.yml`, `server:` block: delete the `http2: enabled: false` entry and the five-line comment above it (the comment starts with "Off until the Undertow servlet bridge is fixed"). HTTP/2 is then on by default again; set `enabled: true` explicitly if you want it visible.
4. `backend/src/test/java/io/github/diogohmcruz/towerdungeon/api/LargeResponseTest.java`: remove the two `@Disabled("#72: ...")` annotations (`aLargeBodyIsIntactOverHttp11`, 600 and 1024 KiB, and `aLargeBodyIsIntactOverHttp2`, the same sizes) and the Javadoc sentence telling readers to re-enable `server.http2`; remove the now unused `org.junit.jupiter.api.Disabled` import.
5. Run the backend tests, including `McpEndpointTest.aResourceOverSixteenKibIsIntactWhateverTheClientAsksFor` (it only means something while the glossary is over 16 KiB) and the WebSocket upgrade check from ADR 0005.
6. Delete or update `docs/research/undertow-starter-large-responses.md` (mark it resolved with the fixed version) and update issue #72 yourself; this document does not change it.

## Open questions

- Which Jira fix versions the maintainers will set, and whether they will backport to `EE 1.0.x`, `undertow-io/undertow` `2.3.x` and `2.2.x`. The same method in those branches has the same shape; only `undertow-ee` was checked by reading the file, not by running a test.
- When a `2.0.4.Final` is planned, and when the starter moves to it. The starter's only published state is `1.0.0.Alpha1-SNAPSHOT`/`Alpha1` and its `pom.xml` pins `2.0.2.Final`.
- Whether the maintainers prefer a test on `DefaultServer` (which would also cover its proxy and SSL modes) over the self-contained server used here. I did not use `DefaultServer` because it has no HTTP/2 cleartext listener.
- The HTTP/1.1 test case depends on a one second pause and on the socket buffers being smaller than 16 MiB; it failed unpatched and passed patched on this macOS machine, in two patched runs (isolated and in the full module). It has not run on Linux or in CI.
- Whether the test should also cover the third write entry point of the class (`write(byte[])` already has the flag) and `ServletPrintWriter`; I did not look at the writer.
- The other `compact()` calls in the class (lines 232, 549, 614 on main) were read but not exercised; by reading they are not affected (see the derivation above).

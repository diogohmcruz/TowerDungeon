# MCP binding spike (issue #31)

Run on 2026-10-23 against the repository as it stands: Spring Boot 4.1.1, Java 26 (target 24), Jackson 3, WebFlux on Undertow through the community starter (ADR 0005), with `@EnableWebFlux` on `WebSocketConfig`. Claude Code 2.1.309 was the host. The spike code was throwaway and is not in the repository; the findings it produced are recorded here so the real implementation (#63) can start from them.

**Decision:** bind with the **Spring AI 2.0.1 stateless, async MCP server** (SDK 2.0.0, spec 2025-11-25), with tools registered programmatically from the `ToolCatalogue`. It passes checks 1 to 4 once the response sanitiser and error handler described below are in place. The hand-written stateless endpoint stays the fallback. The decision is recorded in ADR 0007.

## What was built for the spike

- `spring-ai-bom` 2.0.1 and `spring-ai-starter-mcp-server-webflux` added to the pom. This pulls `spring-ai-mcp` and `mcp-spring-webflux` 2.0.1, `io.modelcontextprotocol.sdk:mcp-core` 2.0.0 and `mcp-json-jackson3` 2.0.0.
- Properties: `spring.ai.mcp.server.protocol=STATELESS`, `spring.ai.mcp.server.type=ASYNC` (the endpoint defaults to `/mcp`).
- Two tools (`td_ping`, `td_boom`) registered with no `@McpTool` annotations.
- A `WebFilter` at highest precedence for `/mcp`, and a `WebExceptionHandler`.

## Results

| # | Check | Result |
|---|---|---|
| 1 | The app boots with the starter present and `/api/ws` still returns 101 | **Pass.** Started in 1.6 s; a WebSocket upgrade request returned `101 Switching Protocols`. |
| 2 | `claude mcp add --transport http` lists and calls a tool with a static bearer header | **Pass.** Run through `claude -p --mcp-config <file> --strict-mcp-config` (equivalent configuration, and it leaves the user's settings alone): the server showed as `connected`, both tools were listed, and `td_ping` was called and its result used. Raw `tools/list` and `tools/call` over `curl` also work without an `initialize` call (stateless). |
| 3 | The filter returns 401 with no token and 403 for a bad `Origin`, before the library sees the request | **Pass.** 401 without a token, 403 for `Origin: https://evil.example`, 405 with `Allow: POST` for GET. |
| 4 | 400 and 500 bodies contain no stack trace | **Fails natively, passes with two mitigations.** See below. |
| 5 | A result with both `structuredContent` and a JSON text block does not double the tokens | **Pass.** The model received one copy of the result (the structured content, serialised); the duplicate text block was not added to the context. |
| 6 | A `server/discover` probe gets a clean 400, with and without `MCP_PROTOCOL_NEGOTIATION=legacy` | **Pass with a handler.** Natively the library throws and Boot answers 500; the error handler turns it into a 400 JSON-RPC "method not found". Claude Code connected and called the tool in both the default and the legacy mode, and the server saw no `server/discover` request from it in either. |
| 7 | An `agent-stdio` profile boots with clean stdout | **Pass with caveats.** See below. |
| 8 | The library's mapper and the app's `ObjectMapper` do not conflict | **Pass.** One Jackson 3 `JsonMapper` bean serves the app and the library (`mcp-json-jackson3`); the application started with no ambiguity. |

## Findings that shape the implementation

1. **Tools must be provided as a list bean.** The stateless auto-configuration reads `ObjectProvider<List<McpStatelessServerFeatures.AsyncToolSpecification>>`, so it needs a bean whose type is `List<AsyncToolSpecification>`. Individual specification beans are ignored and `tools/list` returns an empty list with no warning.
2. **The library leaks stack traces, so a sanitiser is mandatory.** A malformed request body returns `400` with the serialised `McpError`, including a `stackTrace` array of about 25 frames. An unknown method returns `500`, and with devtools active Boot adds a `trace` field. The fix that worked: a `ServerHttpResponseDecorator` installed by the `/mcp` filter that, for any 4xx or 5xx response, discards what the library wrote and writes a fixed body (`{"error":"bad_request"}` or `{"error":"internal_error"}`).
3. **`server/discover` and other unknown methods need an error handler.** A `WebExceptionHandler` ordered before Boot's default (order -2) that maps `McpError` on `/mcp` to `400` with `{"jsonrpc":"2.0","id":null,"error":{"code":-32601,"message":"Method not found"}}`.
4. **Tool handler exceptions leak their message.** A handler that throws returns `200` with a JSON-RPC error whose message is the exception message (`boom: internal detail`); there is no stack trace, but the message is exposed. The `ToolCatalogue` adapter must wrap every handler and turn any exception into a fixed `isError` result.
5. **`notifications/initialized` has no handler.** The stateless server logs a WARN ("Missing handler for notification type") on each client connection; the call still succeeds. It is noise to suppress in the logging configuration.
6. **stdio needs the stateful server and a quiet logger.** With `spring.main.web-application-type=none` and `spring.ai.mcp.server.stdio=true` the app boots and answers `initialize` on stdout with only JSON-RPC, but only with the default (non-stateless) protocol; with `protocol=STATELESS` the stdio transport produced no reply. So the stdio profile registers tools through the stateful specification types (`McpServerFeatures`), and the `ToolCatalogue` adapter must be able to produce both the stateless and the stateful forms. The default console log appender writes to stdout, so the profile must send logs to stderr and turn ANSI off. The existing `WebSocketConfig` loaded without error in the non-web run, so `@ConditionalOnWebApplication` was not needed to boot (it may still be wanted for clarity).
7. **Observed on JDK 26 with the Undertow snapshot:** `XNIO008001: Socket accept failed, backing off for 250 milliseconds: java.net.SocketException: Invalid argument` appeared a few times while Claude Code connected and disconnected. Every request still succeeded. Not tied to MCP as far as the spike could tell; watch for it in M4's concurrency test.

## Not verified

- The `claude mcp add` command itself (its configuration was exercised through `--mcp-config`).
- Behaviour of Claude web or Desktop connectors, other hosts, and any host that sends only the 2026-07-28 revision.
- Concurrency of long-lived `td_wait` POSTs beside the scheduler (design 5.4); that is part of M4.

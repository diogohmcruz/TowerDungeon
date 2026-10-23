---
status: proposed
---

# One tool catalogue behind a Spring AI stateless binding

The agent surface defines its tools once as data (a `ToolCatalogue`) and binds them to MCP through the Spring AI 2.0.x stateless, async server (MCP SDK 2.0.0, spec 2025-11-25), registered programmatically and never with `@McpTool`. Our own `McpHttpFilter` sits in front of the library for authentication, Origin checks, size limits and response sanitising. The binding lives in `agent.mcp`, so swapping it touches no tool. Spike and evidence: `docs/research/mcp-binding-spike.md` (issue #31).

We chose it because it is the same stack as the app (Boot 4.1, Jackson 3, one mapper bean), works through the Undertow community starter, and was accepted by Claude Code with a static bearer header. We rejected Spring AI 1.1.8 because it needs Boot 3.5, a hand-written endpoint as the first choice because it makes us track the specification, and a sidecar in another language because it adds a deployable and a hop. The hand-written stateless endpoint remains the fallback if the library's transport proves unsafe.

## Consequences

- The library writes serialised exceptions, stack traces included, into error bodies. The filter must replace every 4xx and 5xx body it produces, and an error handler must turn an unknown method (including a `server/discover` probe) into a clean 400. Both need tests in M4.
- A tool handler that throws exposes its message, so the catalogue adapter wraps every handler and converts failures to a fixed error result.
- Tools reach the library as one `List<AsyncToolSpecification>` bean; individual beans are silently ignored.
- The stdio profile needs the stateful specification types, not the stateless ones, and logging redirected to stderr.
- The binding rests on an unreleased Undertow starter (ADR 0005) and a young Spring AI line; a binding for spec 2026-07-28 is a later step (M12).

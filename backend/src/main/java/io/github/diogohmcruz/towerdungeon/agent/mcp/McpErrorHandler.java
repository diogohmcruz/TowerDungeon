package io.github.diogohmcruz.towerdungeon.agent.mcp;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Turns what the MCP library throws on the endpoint into a fixed JSON-RPC answer. An unknown method
 * (a {@code server/discover} probe, say) is a clean 400 "method not found"; anything else the
 * library did wrong is a plain 400 or 500. Ordered before Boot's own handler, which would otherwise
 * answer 500 and, with devtools, add a trace.
 */
@Slf4j
@Order(-2)
public class McpErrorHandler implements WebExceptionHandler {
  private final String endpoint;

  public McpErrorHandler(String endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
    var response = exchange.getResponse();
    if (!endpoint.equals(exchange.getRequest().getPath().value()) || response.isCommitted()) {
      return Mono.error(error);
    }
    var mcp = find(error);
    if (mcp == null) {
      log.error("The MCP endpoint failed", error);
      return McpResponses.write(
          response, HttpStatus.INTERNAL_SERVER_ERROR, McpResponses.INTERNAL_ERROR);
    }
    var body =
        switch (mcp.getJsonRpcError().code()) {
          case McpSchema.ErrorCodes.METHOD_NOT_FOUND -> McpResponses.METHOD_NOT_FOUND;
          case McpSchema.ErrorCodes.PARSE_ERROR -> McpResponses.PARSE_ERROR;
          default -> McpResponses.INVALID_REQUEST;
        };
    return McpResponses.write(response, HttpStatus.BAD_REQUEST, body);
  }

  private static McpError find(Throwable error) {
    for (var cause = error;
        cause != null;
        cause = cause.getCause() == cause ? null : cause.getCause()) {
      if (cause instanceof McpError mcp) {
        return mcp;
      }
    }
    return null;
  }
}

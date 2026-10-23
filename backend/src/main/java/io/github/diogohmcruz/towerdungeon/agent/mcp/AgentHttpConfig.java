package io.github.diogohmcruz.towerdungeon.agent.mcp;

import java.util.List;
import java.util.Map;

import org.springframework.ai.mcp.server.webflux.transport.WebFluxStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.auth.AgentAccess;
import io.github.diogohmcruz.towerdungeon.agent.auth.AuditLog;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.RateLimiter;
import io.github.diogohmcruz.towerdungeon.agent.prompts.PromptCatalogue;
import io.github.diogohmcruz.towerdungeon.agent.resources.ResourceCatalogue;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolCatalogue;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import tools.jackson.databind.json.JsonMapper;

/**
 * The HTTP door: the Spring AI stateless, async server (docs/adr/0007) on {@code POST /mcp}, with
 * the tools and resources handed to it as list beans (individual beans are ignored by the library),
 * the guarding filter in front, and the error handler behind. Present only on a web application
 * that runs the stateless protocol.
 */
@AgentEnabled
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(
    prefix = "spring.ai.mcp.server",
    name = "protocol",
    havingValue = "STATELESS")
public class AgentHttpConfig {

  /**
   * The transport, with a context extractor that carries the filter's principal to the tools. The
   * library's own bean backs off for this one.
   */
  @Bean
  public WebFluxStatelessServerTransport webFluxStatelessServerTransport(
      @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper, AgentHttpProperties http) {
    return WebFluxStatelessServerTransport.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .messageEndpoint(http.getEndpoint())
        .contextExtractor(
            request ->
                request
                    .attribute(Principal.KEY)
                    .map(
                        principal ->
                            McpTransportContext.create(
                                Map.<String, Object>of(Principal.KEY, principal)))
                    .orElse(McpTransportContext.EMPTY))
        .build();
  }

  @Bean
  public List<McpStatelessServerFeatures.AsyncToolSpecification> mcpTools(ToolCatalogue tools) {
    return McpBinding.statelessTools(tools);
  }

  @Bean
  public List<McpStatelessServerFeatures.AsyncResourceSpecification> mcpResources(
      ResourceCatalogue resources) {
    return McpBinding.statelessResources(resources);
  }

  @Bean
  public List<McpStatelessServerFeatures.AsyncResourceTemplateSpecification> mcpResourceTemplates(
      ResourceCatalogue resources) {
    return McpBinding.statelessTemplates(resources);
  }

  @Bean
  public List<McpStatelessServerFeatures.AsyncPromptSpecification> mcpPrompts(
      PromptCatalogue prompts) {
    return McpBinding.statelessPrompts(prompts);
  }

  @Bean
  public McpHttpFilter mcpHttpFilter(
      AgentHttpProperties http, AgentAccess access, RateLimiter limiter, AuditLog audit) {
    return new McpHttpFilter(http, access, limiter, audit);
  }

  @Bean
  public McpErrorHandler mcpErrorHandler(AgentHttpProperties http) {
    return new McpErrorHandler(http.getEndpoint());
  }
}

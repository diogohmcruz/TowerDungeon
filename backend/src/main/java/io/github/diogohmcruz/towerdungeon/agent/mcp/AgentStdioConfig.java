package io.github.diogohmcruz.towerdungeon.agent.mcp;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.resources.ResourceCatalogue;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolCatalogue;
import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * The stdio door: the library's stateful server over standard input and output, which the stateless
 * transport cannot serve. It needs the stateful specification types, so the same catalogues are
 * bound in that form. The caller is a local process the developer started, so there is no token.
 */
@AgentEnabled
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "stdio", havingValue = "true")
public class AgentStdioConfig {

  @Bean
  public List<McpServerFeatures.AsyncToolSpecification> mcpTools(ToolCatalogue tools) {
    return McpBinding.statefulTools(tools);
  }

  @Bean
  public List<McpServerFeatures.AsyncResourceSpecification> mcpResources(
      ResourceCatalogue resources) {
    return McpBinding.statefulResources(resources);
  }

  @Bean
  public List<McpServerFeatures.AsyncResourceTemplateSpecification> mcpResourceTemplates(
      ResourceCatalogue resources) {
    return McpBinding.statefulTemplates(resources);
  }
}

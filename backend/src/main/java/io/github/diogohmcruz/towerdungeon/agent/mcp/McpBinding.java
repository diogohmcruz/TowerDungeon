package io.github.diogohmcruz.towerdungeon.agent.mcp;

import java.util.List;

import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.resources.ResourceCatalogue;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolCatalogue;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolDef;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolFailure;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The only place the MCP library's types meet the catalogues. It turns the {@link ToolCatalogue}
 * and the {@link ResourceCatalogue} into the specification types of the library, in two forms: the
 * stateless one the HTTP door needs and the stateful one stdio needs (the stateless transport gives
 * no stdio reply). Swapping the library means rewriting this class and nothing else.
 */
@Slf4j
public final class McpBinding {
  private McpBinding() {}

  /** The tools as the library lists them: explicit annotations, static description. */
  public static McpSchema.Tool toolOf(ToolDef def) {
    return McpSchema.Tool.builder(def.name(), def.inputSchema())
        .description(def.description())
        .annotations(
            McpSchema.ToolAnnotations.builder()
                .readOnlyHint(def.readOnly())
                .destructiveHint(def.destructive())
                .idempotentHint(def.idempotent())
                .openWorldHint(false)
                .build())
        .build();
  }

  // ---------------------------------------------------------------- stateless (HTTP)

  public static List<McpStatelessServerFeatures.AsyncToolSpecification> statelessTools(
      ToolCatalogue catalogue) {
    return catalogue.tools().stream()
        .map(
            def ->
                new McpStatelessServerFeatures.AsyncToolSpecification(
                    toolOf(def),
                    (context, request) ->
                        catalogue.call(principalOf(context), request.name(), request.arguments())))
        .toList();
  }

  public static List<McpStatelessServerFeatures.AsyncResourceSpecification> statelessResources(
      ResourceCatalogue catalogue) {
    return catalogue.fixed().stream()
        .map(
            def ->
                new McpStatelessServerFeatures.AsyncResourceSpecification(
                    resourceOf(def),
                    (context, request) -> read(catalogue, principalOf(context), request.uri())))
        .toList();
  }

  public static List<McpStatelessServerFeatures.AsyncResourceTemplateSpecification>
      statelessTemplates(ResourceCatalogue catalogue) {
    return catalogue.templates().stream()
        .map(
            template ->
                new McpStatelessServerFeatures.AsyncResourceTemplateSpecification(
                    templateOf(template),
                    (context, request) -> read(catalogue, principalOf(context), request.uri())))
        .toList();
  }

  // ---------------------------------------------------------------- stateful (stdio)

  public static List<McpServerFeatures.AsyncToolSpecification> statefulTools(
      ToolCatalogue catalogue) {
    return catalogue.tools().stream()
        .map(
            def ->
                new McpServerFeatures.AsyncToolSpecification(
                    toolOf(def),
                    (exchange, request) ->
                        catalogue.call(Principal.stdio(), request.name(), request.arguments())))
        .toList();
  }

  public static List<McpServerFeatures.AsyncResourceSpecification> statefulResources(
      ResourceCatalogue catalogue) {
    return catalogue.fixed().stream()
        .map(
            def ->
                new McpServerFeatures.AsyncResourceSpecification(
                    resourceOf(def),
                    (exchange, request) -> read(catalogue, Principal.stdio(), request.uri())))
        .toList();
  }

  public static List<McpServerFeatures.AsyncResourceTemplateSpecification> statefulTemplates(
      ResourceCatalogue catalogue) {
    return catalogue.templates().stream()
        .map(
            template ->
                new McpServerFeatures.AsyncResourceTemplateSpecification(
                    templateOf(template),
                    (exchange, request) -> read(catalogue, Principal.stdio(), request.uri())))
        .toList();
  }

  // ---------------------------------------------------------------- shared

  private static McpSchema.Resource resourceOf(ResourceCatalogue.Def def) {
    return McpSchema.Resource.builder(def.uri(), def.name())
        .description(def.description())
        .mimeType(def.mimeType())
        .build();
  }

  private static McpSchema.ResourceTemplate templateOf(ResourceCatalogue.Template template) {
    return McpSchema.ResourceTemplate.builder(template.uriTemplate(), template.name())
        .description(template.description())
        .mimeType(ResourceCatalogue.JSON)
        .build();
  }

  /** The principal the filter left in the transport context; null when there is none. */
  private static Principal principalOf(McpTransportContext context) {
    return context != null && context.get(Principal.KEY) instanceof Principal principal
        ? principal
        : null;
  }

  /**
   * Reads a resource for a caller. A refusal is a fixed JSON-RPC error; nothing else about a
   * failure reaches the caller.
   */
  private static Mono<McpSchema.ReadResourceResult> read(
      ResourceCatalogue catalogue, Principal principal, String uri) {
    return Mono.fromCallable(
            () -> {
              if (principal == null) {
                throw new ToolFailure("UNAUTHENTICATED", "No caller was identified.");
              }
              var content = catalogue.read(principal, uri);
              if (content.isEmpty()) {
                throw new ToolFailure("NO_SUCH_RESOURCE", "There is no such resource.");
              }
              var found = content.get();
              return McpSchema.ReadResourceResult.builder(
                      List.<McpSchema.ResourceContents>of(
                          McpSchema.TextResourceContents.builder(found.uri(), found.text())
                              .mimeType(found.mimeType())
                              .build()))
                  .build();
            })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(McpBinding::fixedError);
  }

  /** Maps any failure of a read to a fixed error. */
  private static Throwable fixedError(Throwable error) {
    if (error instanceof ToolFailure failure) {
      return McpError.builder(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
          .message(failure.getMessage())
          .build();
    }
    if (error instanceof McpError) {
      return error;
    }
    log.error("A resource read failed", error);
    return McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
        .message("The resource could not be read.")
        .build();
  }
}

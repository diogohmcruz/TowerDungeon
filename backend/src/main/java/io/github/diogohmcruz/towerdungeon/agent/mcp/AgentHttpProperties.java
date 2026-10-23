package io.github.diogohmcruz.towerdungeon.agent.mcp;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Settings of the HTTP door of the agent surface ({@code game.agent.http.*}). The door is
 * loopback-only unless the allow-lists say otherwise: an {@code Origin} header that is present and
 * not listed is refused, and so is a {@code Host} that is not listed (DNS rebinding).
 */
@Data
@ConfigurationProperties(prefix = "game.agent.http")
public class AgentHttpProperties {
  /** The path the MCP endpoint answers on; it must match the library's endpoint. */
  private String endpoint = "/mcp";

  /**
   * SHA-256 of the developer bearer token, as hex. Only the hash is configured, never the token:
   * {@code printf '%s' "$TD_AGENT_DEV_TOKEN" | shasum -a 256}. Empty means no token works.
   */
  private String devTokenHash = "";

  /**
   * The human Campaign (a player id) the developer token may read when it names no Sandbox. Empty
   * means a human Campaign is out of reach.
   */
  private String devCampaign = "";

  /**
   * Browser origins that may call the endpoint. Empty: any request that carries an Origin fails.
   */
  private List<String> allowedOrigins = new ArrayList<>();

  /** Host names the endpoint answers to. */
  private List<String> allowedHosts = new ArrayList<>(List.of("localhost", "127.0.0.1", "[::1]"));

  /** The largest request body, in bytes. */
  private int maxBodyBytes = 64 * 1024;

  /** Where the audit log is written; empty means beside the Campaign files. */
  private String auditFile = "";
}

package org.zalava.modules.mcpbridge;

import java.util.List;
import java.util.Map;
import org.zalava.api.ModuleConfigurationDescriptor;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;

public final class McpBridgeSeaModule implements ZalavaModule {
  static final String MODULE_ID = "zalava-module-mcp-bridge";
  private static final String VERSION = ModuleVersion.value();
  private final McpServiceConnector connector;

  public McpBridgeSeaModule() {
    this(new McpJavaServiceConnector());
  }

  McpBridgeSeaModule(McpServiceConnector connector) {
    this.connector = connector;
  }

  @Override
  public ModuleDescriptor descriptor() {
    return new ModuleDescriptor(
        MODULE_ID, VERSION, "MCP Bridge", "Bridges configured MCP services into SEA providers.");
  }

  @Override
  public List<ProviderFactory> providerFactories() {
    return List.of(new McpBridgeProviderFactory(VERSION, connector));
  }

  @Override
  public ModuleConfigurationDescriptor configuration() {
    return new ModuleConfigurationDescriptor(
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                McpBridgeProviderFactory.FACTORY_ID,
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "registrations",
                        Map.of(
                            "type",
                            "array",
                            "items",
                            Map.of(
                                "type",
                                "object",
                                "properties",
                                Map.of(
                                    "id", Map.of("type", "string"),
                                    "transport",
                                        Map.of(
                                            "type",
                                            "string",
                                            "enum",
                                            List.of("streamable-http", "stdio")),
                                    "endpoint", Map.of("type", "string"),
                                    "command", Map.of("type", "string"),
                                    "arguments",
                                        Map.of("type", "array", "items", Map.of("type", "string")),
                                    "credentialReferences",
                                        Map.of(
                                            "type",
                                            "object",
                                            "additionalProperties",
                                            Map.of("type", "string"))),
                                "required",
                                List.of("id", "transport"),
                                "additionalProperties",
                                false))),
                    "required",
                    List.of("registrations"),
                    "additionalProperties",
                    false)),
            "additionalProperties",
            false));
  }
}

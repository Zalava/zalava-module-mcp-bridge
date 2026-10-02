package org.zalava.mcpbridge;

import java.util.List;
import java.util.Map;
import org.zalava.InvocationContext;
import org.zalava.PromptDescriptor;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ResourceDescriptor;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import tools.jackson.databind.JsonNode;

final class McpBridgeProvider implements ZalavaProvider {
  final McpServiceSession session;
  private final ProviderDescriptor descriptor;

  McpBridgeProvider(McpRegistration registration, String version, McpServiceSession session) {
    this.session = session;
    this.descriptor =
        new ProviderDescriptor(
            registration.id(),
            McpBridgeSeaModule.MODULE_ID,
            "mcp-service",
            registration.id(),
            "MCP " + registration.transport().name().toLowerCase().replace('_', ' ') + " service",
            version,
            new ProviderCapabilities(
                !session.tools().isEmpty(),
                !session.resources().isEmpty(),
                !session.prompts().isEmpty(),
                false,
                false,
                false,
                true,
                false),
            List.of("external-mcp", registration.transport().name().toLowerCase()),
            Map.of(
                "transport",
                registration.transport().name().toLowerCase(),
                "credentialReferences",
                String.join(",", registration.credentialReferences().values())));
  }

  @Override
  public ProviderDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public ProviderCapabilities capabilities() {
    return descriptor.capabilities();
  }

  @Override
  public List<ZalavaToolDescriptor> listTools() {
    return session.tools();
  }

  @Override
  public ZalavaOperationResult callTool(
      String name, JsonNode arguments, InvocationContext context) {
    return session.callTool(name, arguments);
  }

  @Override
  public List<ResourceDescriptor> listResources() {
    return session.resources();
  }

  @Override
  public ZalavaOperationResult readResource(String uri, InvocationContext context) {
    return session.readResource(uri);
  }

  @Override
  public List<PromptDescriptor> listPrompts() {
    return session.prompts();
  }

  @Override
  public ZalavaOperationResult resolvePrompt(
      String name, JsonNode arguments, InvocationContext context) {
    return session.resolvePrompt(name, arguments);
  }
}

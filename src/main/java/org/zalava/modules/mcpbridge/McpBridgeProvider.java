package org.zalava.modules.mcpbridge;

import java.util.List;
import java.util.Map;
import org.zalava.api.InvocationContext;
import org.zalava.api.PromptDescriptor;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ResourceDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;

final class McpBridgeProvider implements ZalavaProvider {
  final McpServiceSession session;
  private final ProviderDescriptor descriptor;

  McpBridgeProvider(McpRegistration registration, String version, McpServiceSession session) {
    this.session = session;
    this.descriptor =
        new ProviderDescriptor(
            registration.id(),
            McpBridgeZalavaModule.MODULE_ID,
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
      String name, java.util.Map<String, Object> argumentValues, InvocationContext context) {
    tools.jackson.databind.JsonNode arguments =
        new tools.jackson.databind.json.JsonMapper().valueToTree(argumentValues);
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
      String name, java.util.Map<String, Object> argumentValues, InvocationContext context) {
    tools.jackson.databind.JsonNode arguments =
        new tools.jackson.databind.json.JsonMapper().valueToTree(argumentValues);
    return session.resolvePrompt(name, arguments);
  }
}

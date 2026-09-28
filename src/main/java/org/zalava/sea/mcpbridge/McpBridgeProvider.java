package org.zalava.mcpbridge;

import tools.jackson.databind.JsonNode;
import org.zalava.InvocationContext;
import org.zalava.PromptDescriptor;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ResourceDescriptor;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;

import java.util.List;
import java.util.Map;

final class McpBridgeProvider implements SeaProvider {
    final McpServiceSession session;
    private final ProviderDescriptor descriptor;

    McpBridgeProvider(McpRegistration registration, String version, McpServiceSession session) {
        this.session = session;
        this.descriptor = new ProviderDescriptor(registration.id(), McpBridgeSeaModule.MODULE_ID, "mcp-service",
                registration.id(), "MCP " + registration.transport().name().toLowerCase().replace('_', ' ') + " service",
                version, new ProviderCapabilities(!session.tools().isEmpty(), !session.resources().isEmpty(), !session.prompts().isEmpty(),
                false, false, false, true, false), List.of("external-mcp", registration.transport().name().toLowerCase()),
                Map.of("transport", registration.transport().name().toLowerCase(),
                        "credentialReferences", String.join(",", registration.credentialReferences().values())));
    }

    @Override public ProviderDescriptor descriptor() { return descriptor; }
    @Override public ProviderCapabilities capabilities() { return descriptor.capabilities(); }
    @Override public List<SeaToolDescriptor> listTools() { return session.tools(); }
    @Override public SeaOperationResult callTool(String name, JsonNode arguments, InvocationContext context) { return session.callTool(name, arguments); }
    @Override public List<ResourceDescriptor> listResources() { return session.resources(); }
    @Override public SeaOperationResult readResource(String uri, InvocationContext context) { return session.readResource(uri); }
    @Override public List<PromptDescriptor> listPrompts() { return session.prompts(); }
    @Override public SeaOperationResult resolvePrompt(String name, JsonNode arguments, InvocationContext context) { return session.resolvePrompt(name, arguments); }
}

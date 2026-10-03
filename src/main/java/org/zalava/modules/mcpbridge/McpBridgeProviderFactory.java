package org.zalava.modules.mcpbridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaProvider;

final class McpBridgeProviderFactory implements ProviderFactory {
  static final String FACTORY_ID = "mcp-service-factory";
  private final String version;
  private final McpServiceConnector connector;

  McpBridgeProviderFactory(String version, McpServiceConnector connector) {
    this.version = version;
    this.connector = connector;
  }

  @Override
  public ProviderFactoryDescriptor descriptor() {
    return new ProviderFactoryDescriptor(
        FACTORY_ID,
        McpBridgeZalavaModule.MODULE_ID,
        "mcp-service",
        "MCP service bridge",
        "Creates one provider for each configured remote MCP service.");
  }

  @Override
  public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
    List<ZalavaProvider> providers = new ArrayList<>();
    for (McpRegistration registration : registrations(context.configuration())) {
      try {
        providers.add(
            new McpBridgeProvider(registration, version, connector.connect(registration)));
      } catch (RuntimeException ignored) {
        // A remote outage must not prevent Zalava from exposing module configuration.
      }
    }
    return List.copyOf(providers);
  }

  @SuppressWarnings("unchecked")
  private static List<McpRegistration> registrations(Map<String, Object> configuration) {
    Object values = configuration.get("registrations");
    if (!(values instanceof List<?> registrations)) return List.of();
    List<McpRegistration> parsed = new ArrayList<>();
    for (Object value : registrations) {
      if (!(value instanceof Map<?, ?> map)) continue;
      try {
        String transport = String.valueOf(map.get("transport"));
        Map<String, String> refs =
            map.get("credentialReferences") instanceof Map<?, ?> rawRefs
                ? ((Map<?, ?>) rawRefs)
                    .entrySet().stream()
                        .collect(
                            java.util.stream.Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()),
                                entry -> String.valueOf(entry.getValue())))
                : Map.of();
        parsed.add(
            new McpRegistration(
                String.valueOf(map.get("id")),
                McpRegistration.Transport.valueOf(transport.replace('-', '_').toUpperCase()),
                (String) map.get("endpoint"),
                (String) map.get("command"),
                map.get("arguments") instanceof List<?> args
                    ? ((List<?>) args).stream().map(String::valueOf).toList()
                    : List.of(),
                refs));
      } catch (IllegalArgumentException ignored) {
        // Invalid individual registrations remain recoverable through the Zalava-owned
        // configuration
        // flow.
      }
    }
    return List.copyOf(parsed);
  }
}

package org.zalava.mcpbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.zalava.InvocationContext;
import org.zalava.PromptDescriptor;
import org.zalava.ResourceDescriptor;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.testing.ConfigFixture;
import org.zalava.testing.ModuleContractKit;
import org.zalava.testing.ProviderFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the real built module JAR at the stable {@code module-api} boundary through the released
 * contract kit. The MCP connector seam is driven by an in-process double so no MCP server or
 * external process is needed; host-owned resolution, validation, permissions, approvals,
 * persistence and transport stay covered by SEA.
 */
class McpBridgeSeaModuleTest {

    private static final String MODULE_ID = "zalava-module-mcp-bridge";
    private static final String FACTORY_ID = "mcp-service-factory";
    private static final String MODULE_CLASS = "org.zalava.mcpbridge.McpBridgeSeaModule";
    private static final String CONNECTOR_TYPE = "org.zalava.mcpbridge.McpServiceConnector";
    private static final String SESSION_TYPE = "org.zalava.mcpbridge.McpServiceSession";

    private ModuleContractKit kit;

    @BeforeEach
    void loadTheBuiltArtifact() {
        kit = ModuleContractKit.load(Path.of(System.getProperty("module.artifact")), List.of(),
                MODULE_ID, System.getProperty("module.version"));
    }

    @AfterEach
    void closeTheArtifact() throws Exception {
        if (kit != null) {
            kit.close();
        }
    }

    @Test
    void loadsTheModuleFromTheBuiltArtifact() {
        assertThat(kit.module().getClass().getClassLoader()).isNotSameAs(getClass().getClassLoader());
        assertThat(kit.module().getClass().getProtectionDomain().getCodeSource().getLocation().toString())
                .endsWith(".jar");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesTheModuleOwnedDescriptorAndRegistrationConfiguration() {
        ZalavaModule module = kit.module();
        assertThat(kit.moduleId()).isEqualTo(MODULE_ID);
        assertThat(kit.version()).isEqualTo(System.getProperty("module.version"));
        assertThat(module.descriptor().displayName()).isEqualTo("MCP Bridge");
        assertThat(module.providerFactories()).hasSize(1);

        var factory = module.providerFactories().getFirst().descriptor();
        assertThat(factory.factoryId()).isEqualTo(FACTORY_ID);
        assertThat(factory.moduleId()).isEqualTo(MODULE_ID);
        assertThat(factory.providerType()).isEqualTo("mcp-service");

        Map<String, Object> schema = module.configuration().jsonSchema();
        assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> factorySchema = (Map<String, Object>) properties.get(FACTORY_ID);
        assertThat(factorySchema).containsEntry("type", "object").containsEntry("additionalProperties", false);
        assertThat((List<String>) factorySchema.get("required")).contains("registrations");

        Map<String, Object> factoryProperties = (Map<String, Object>) factorySchema.get("properties");
        Map<String, Object> registrations = (Map<String, Object>) factoryProperties.get("registrations");
        assertThat(registrations).containsEntry("type", "array");
        Map<String, Object> item = (Map<String, Object>) registrations.get("items");
        assertThat(item).containsEntry("type", "object").containsEntry("additionalProperties", false);
        assertThat((List<String>) item.get("required")).contains("id", "transport");
        Map<String, Object> itemProperties = (Map<String, Object>) item.get("properties");
        assertThat((Map<String, Object>) itemProperties.get("transport"))
                .containsEntry("enum", List.of("streamable-http", "stdio"));
    }

    @Test
    void createsNoProviderUntilHostConfiguresRegistrations() {
        try (ProviderFixture providers = kit.providers(ConfigFixture.empty())) {
            assertThat(providers.providers()).isEmpty();
        }
    }

    @Test
    void createsOneProviderPerValidRegistrationAndMirrorsDiscovery() throws Exception {
        RecordingConnector connector = new RecordingConnector(loader());
        try (ModuleContractKit injected = injected(connector);
                ProviderFixture providers = injected.providers(configuration(List.of(
                        Map.of("id", "github", "transport", "streamable-http", "endpoint", "https://mcp.example/github"),
                        Map.of("id", "local-docs", "transport", "stdio", "command", "docs-mcp",
                                "arguments", List.of("--safe")))))) {

            assertThat(providers.providers()).extracting(provider -> provider.descriptor().providerId())
                    .containsExactly("github", "local-docs");

            ZalavaProvider github = providers.requireProvider("github");
            assertThat(github.descriptor().moduleId()).isEqualTo(MODULE_ID);
            assertThat(github.descriptor().providerType()).isEqualTo("mcp-service");
            assertThat(github.descriptor().scope()).containsEntry("transport", "streamable_http");
            assertThat(github.listTools()).extracting(ZalavaToolDescriptor::name).containsExactly("search");
            assertThat(github.listResources()).containsExactly(new ResourceDescriptor("docs://readme", "README"));
            assertThat(github.listPrompts()).containsExactly(new PromptDescriptor("summarize", "Summarize content"));
            assertThat(github.callTool("search", arguments(), InvocationContext.system()).success()).isTrue();

            assertThat(providers.requireProvider("local-docs").descriptor().scope())
                    .containsEntry("transport", "stdio");
        }
        assertThat(connector.connected).containsExactly("github", "local-docs");
    }

    @Test
    void skipsMalformedAndUnavailableRegistrations() throws Exception {
        RecordingConnector connector = new RecordingConnector(loader());
        connector.failing.add("unavailable");
        try (ModuleContractKit injected = injected(connector);
                ProviderFixture providers = injected.providers(configuration(List.of(
                        Map.of("id", "bad", "transport", "stdio"),
                        Map.of("id", "unavailable", "transport", "streamable-http",
                                "endpoint", "https://mcp.example",
                                "credentialReferences", Map.of("Authorization", "github-token")))))) {
            assertThat(providers.providers()).isEmpty();
        }
        assertThat(connector.connected).containsExactly("unavailable");
    }

    @Test
    void keepsCredentialReferencesOpaqueAndNeverSurfacesResolvedSecrets() throws Exception {
        RecordingConnector connector = new RecordingConnector(loader());
        ConfigFixture secrets = configuration(List.of(Map.of(
                "id", "private", "transport", "stdio", "command", "mcp",
                "credentialReferences", Map.of("TOKEN", "github-token"))))
                .secrets(MODULE_ID, reference -> Optional.of("top-secret-value".toCharArray()));
        try (ModuleContractKit injected = injected(connector);
                ProviderFixture providers = injected.providers(secrets)) {
            ZalavaProvider provider = providers.requireProvider("private");
            assertThat(provider.descriptor().scope()).containsEntry("credentialReferences", "github-token");
            assertThat(provider.descriptor().toString()).doesNotContain("top-secret-value");
            assertThat(provider.listTools().toString()).doesNotContain("top-secret-value");
        }
    }

    private ClassLoader loader() {
        return kit.module().getClass().getClassLoader();
    }

    private ModuleContractKit injected(RecordingConnector connector) throws Exception {
        ClassLoader loader = loader();
        Class<?> connectorType = Class.forName(CONNECTOR_TYPE, true, loader);
        connector.initialize(loader, Class.forName(SESSION_TYPE, true, loader));
        Object connectorProxy = Proxy.newProxyInstance(loader, new Class<?>[] {connectorType}, connector);
        Class<?> moduleType = Class.forName(MODULE_CLASS, true, loader);
        Constructor<?> constructor = moduleType.getDeclaredConstructor(connectorType);
        constructor.setAccessible(true);
        return ModuleContractKit.of((ZalavaModule) constructor.newInstance(connectorProxy));
    }

    private static ConfigFixture configuration(List<Map<String, Object>> registrations) {
        return ConfigFixture.empty()
                .factoryConfiguration(MODULE_ID, FACTORY_ID, Map.of("registrations", registrations));
    }

    private static ObjectNode arguments() {
        return JsonNodeFactory.instance.objectNode();
    }

    /** Connector and session invocation double backed by the module classloader's own SPI types. */
    private static final class RecordingConnector implements InvocationHandler {

        private final List<String> connected = new ArrayList<>();
        private final Set<String> failing = new HashSet<>();
        private final List<String> closed = new ArrayList<>();
        private ClassLoader loader;
        private Class<?> sessionType;

        RecordingConnector(ClassLoader loader) {
            this.loader = loader;
        }

        void initialize(ClassLoader loader, Class<?> sessionType) {
            this.loader = loader;
            this.sessionType = sessionType;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "connect" -> {
                    String id = id(args[0]);
                    connected.add(id);
                    if (failing.contains(id)) {
                        throw new IllegalStateException("server rejected top-secret-value");
                    }
                    yield session(id);
                }
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "recording-mcp-connector";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private Object session(String id) {
            return Proxy.newProxyInstance(loader, new Class<?>[] {sessionType},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "tools" -> List.of(new ZalavaToolDescriptor("search", "Search", false,
                                List.of("remote-mcp"), Map.of()));
                        case "resources" -> List.of(new ResourceDescriptor("docs://readme", "README"));
                        case "prompts" -> List.of(new PromptDescriptor("summarize", "Summarize content"));
                        case "callTool" -> ZalavaOperationResult.success("ok");
                        case "readResource" -> ZalavaOperationResult.success("resource");
                        case "resolvePrompt" -> ZalavaOperationResult.success("prompt");
                        case "close" -> {
                            closed.add(id);
                            yield null;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "recording-mcp-session-" + id;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private static String id(Object registration) throws Exception {
            Method accessor = registration.getClass().getMethod("id");
            accessor.setAccessible(true);
            return (String) accessor.invoke(registration);
        }
    }
}
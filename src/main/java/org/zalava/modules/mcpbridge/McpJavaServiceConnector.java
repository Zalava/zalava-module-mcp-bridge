package org.zalava.modules.mcpbridge;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import org.zalava.api.PromptDescriptor;
import org.zalava.api.ResourceDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaToolDescriptor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class McpJavaServiceConnector implements McpServiceConnector {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public McpServiceSession connect(McpRegistration registration) {
    McpSyncClient client =
        withModuleClassLoaderContext(() -> McpClient.sync(transport(registration)).build());
    client.initialize();
    return new Session(client);
  }

  static <T> T withModuleClassLoaderContext(Supplier<T> action) {
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(McpJavaServiceConnector.class.getClassLoader());
      return action.get();
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  private static McpClientTransport transport(McpRegistration registration) {
    if (registration.transport() == McpRegistration.Transport.STREAMABLE_HTTP) {
      return HttpClientStreamableHttpTransport.builder(registration.endpoint()).build();
    }
    return new StdioClientTransport(
        ServerParameters.builder(registration.command()).args(registration.arguments()).build(),
        jsonMapper());
  }

  private static McpJsonMapper jsonMapper() {
    return ServiceLoader.load(McpJsonMapperSupplier.class)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No MCP JSON mapper is available"))
        .get();
  }

  private static final class Session implements McpServiceSession {
    private final McpSyncClient client;

    private Session(McpSyncClient client) {
      this.client = client;
    }

    @Override
    public List<ZalavaToolDescriptor> tools() {
      return client.listTools().tools().stream()
          .map(
              tool ->
                  new ZalavaToolDescriptor(
                      tool.name(),
                      tool.description(),
                      tool.annotations() == null
                          || !Boolean.TRUE.equals(tool.annotations().readOnlyHint()),
                      List.of("remote-mcp"),
                      tool.inputSchema()))
          .toList();
    }

    @Override
    public List<ResourceDescriptor> resources() {
      return client.listResources().resources().stream()
          .map(resource -> new ResourceDescriptor(resource.uri(), resource.description()))
          .toList();
    }

    @Override
    public List<PromptDescriptor> prompts() {
      return client.listPrompts().prompts().stream()
          .map(prompt -> new PromptDescriptor(prompt.name(), prompt.description()))
          .toList();
    }

    @Override
    public ZalavaOperationResult callTool(String name, JsonNode arguments) {
      try {
        McpSchema.CallToolResult result =
            client.callTool(new McpSchema.CallToolRequest(name, map(arguments)));
        return new ZalavaOperationResult(
            !Boolean.TRUE.equals(result.isError()), result.content(), result.meta());
      } catch (RuntimeException exception) {
        return remoteFailure();
      }
    }

    @Override
    public ZalavaOperationResult readResource(String uri) {
      try {
        McpSchema.ReadResourceResult result =
            client.readResource(new McpSchema.ReadResourceRequest(uri));
        return new ZalavaOperationResult(true, result.contents(), result.meta());
      } catch (RuntimeException exception) {
        return remoteFailure();
      }
    }

    @Override
    public ZalavaOperationResult resolvePrompt(String name, JsonNode arguments) {
      try {
        McpSchema.GetPromptResult result =
            client.getPrompt(new McpSchema.GetPromptRequest(name, map(arguments)));
        return new ZalavaOperationResult(true, result.messages(), result.meta());
      } catch (RuntimeException exception) {
        return remoteFailure();
      }
    }

    @Override
    public void close() {
      client.closeGracefully();
    }

    private static Map<String, Object> map(JsonNode arguments) {
      return arguments == null || arguments.isNull()
          ? Map.of()
          : OBJECT_MAPPER.convertValue(arguments, new TypeReference<>() {});
    }

    private static ZalavaOperationResult remoteFailure() {
      return new ZalavaOperationResult(
          false, "Remote MCP operation failed", Map.of("category", "remote_mcp_failure"));
    }
  }
}

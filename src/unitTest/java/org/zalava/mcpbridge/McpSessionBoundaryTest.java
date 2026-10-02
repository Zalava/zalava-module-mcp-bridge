package org.zalava.mcpbridge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class McpSessionBoundaryTest {
  @Test
  void mapsCapabilitiesResultsAndClosesSession() throws Exception {
    McpSyncClient client = mock(McpSyncClient.class, RETURNS_DEEP_STUBS);
    McpSchema.Tool tool = mock(McpSchema.Tool.class, RETURNS_DEEP_STUBS);
    when(tool.name()).thenReturn("search");
    when(tool.description()).thenReturn("Search");
    when(client.listTools().tools()).thenReturn(List.of(tool));
    McpSchema.Resource resource = mock(McpSchema.Resource.class);
    when(resource.uri()).thenReturn("docs://readme");
    when(resource.description()).thenReturn("Readme");
    when(client.listResources().resources()).thenReturn(List.of(resource));
    McpSchema.Prompt prompt = mock(McpSchema.Prompt.class);
    when(prompt.name()).thenReturn("summarize");
    when(prompt.description()).thenReturn("Summary");
    when(client.listPrompts().prompts()).thenReturn(List.of(prompt));
    var session = session(client);
    assertThat(session.tools()).hasSize(1);
    assertThat(session.tools().getFirst().sideEffecting()).isTrue();
    when(tool.annotations().readOnlyHint()).thenReturn(true);
    assertThat(session.tools().getFirst().sideEffecting()).isFalse();
    when(tool.annotations()).thenReturn(null);
    assertThat(session.tools().getFirst().sideEffecting()).isTrue();
    assertThat(session.resources()).hasSize(1);
    assertThat(session.prompts()).hasSize(1);
    McpSchema.CallToolResult result = mock(McpSchema.CallToolResult.class);
    when(result.content()).thenReturn(List.of());
    when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(result);
    assertThat(session.callTool("search", null).success()).isTrue();
    when(result.isError()).thenReturn(true);
    assertThat(session.callTool("search", new JsonMapper().nullNode()).success()).isFalse();
    var json = new JsonMapper();
    when(result.isError()).thenReturn(false);
    assertThat(session.callTool("search", json.createObjectNode().put("query", "text")).success())
        .isTrue();
    McpSchema.ReadResourceResult read = mock(McpSchema.ReadResourceResult.class);
    when(read.contents()).thenReturn(List.of());
    when(client.readResource(any(McpSchema.ReadResourceRequest.class))).thenReturn(read);
    assertThat(session.readResource("docs://readme").success()).isTrue();
    McpSchema.GetPromptResult resolved = mock(McpSchema.GetPromptResult.class);
    when(resolved.messages()).thenReturn(List.of());
    when(client.getPrompt(any(McpSchema.GetPromptRequest.class))).thenReturn(resolved);
    assertThat(session.resolvePrompt("summarize", json.createObjectNode()).success()).isTrue();
    var provider =
        new McpBridgeProvider(
            new McpRegistration(
                "remote",
                McpRegistration.Transport.STREAMABLE_HTTP,
                "https://example.com",
                null,
                List.of(),
                Map.of()),
            "1",
            session);
    assertThat(provider.capabilities()).isNotNull();
    assertThat(
            provider.readResource("docs://readme", org.zalava.InvocationContext.system()).success())
        .isTrue();
    assertThat(
            provider
                .resolvePrompt(
                    "summarize", json.createObjectNode(), org.zalava.InvocationContext.system())
                .success())
        .isTrue();
    session.close();
    verify(client).closeGracefully();
  }

  @Test
  void translatesRemoteFailuresAndRestoresContextLoader() throws Exception {
    McpSyncClient client = mock(McpSyncClient.class);
    var session = session(client);
    when(client.callTool(any(McpSchema.CallToolRequest.class)))
        .thenThrow(new IllegalStateException("remote"));
    when(client.readResource(any(McpSchema.ReadResourceRequest.class)))
        .thenThrow(new IllegalStateException("remote"));
    when(client.getPrompt(any(McpSchema.GetPromptRequest.class)))
        .thenThrow(new IllegalStateException("remote"));
    assertThat(session.callTool("search", null).success()).isFalse();
    assertThat(session.readResource("docs://readme").success()).isFalse();
    assertThat(session.resolvePrompt("summary", null).success()).isFalse();
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    assertThat(
            McpJavaServiceConnector.withModuleClassLoaderContext(
                () -> Thread.currentThread().getContextClassLoader()))
        .isEqualTo(McpJavaServiceConnector.class.getClassLoader());
    assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
    assertThatThrownBy(
            () ->
                McpJavaServiceConnector.withModuleClassLoaderContext(
                    () -> {
                      throw new IllegalStateException("failure");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
  }

  @Test
  void validatesRegistrationsAndSkipsMalformedConfiguration() {
    for (String invalid : Arrays.asList(null, " "))
      assertThatThrownBy(
              () ->
                  new McpRegistration(
                      invalid, McpRegistration.Transport.STDIO, null, "command", null, null))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new McpRegistration(
                    "id", McpRegistration.Transport.STREAMABLE_HTTP, " ", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new McpRegistration("id", McpRegistration.Transport.STDIO, null, " ", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    var factory =
        new McpBridgeProviderFactory(
            "1",
            registration -> {
              throw new IllegalStateException("unavailable");
            });
    var context =
        new org.zalava.ProviderFactoryContext(Map.of(), null, Map.of(), Map.of())
            .forFactory("zalava-module-mcp-bridge", "mcp-service-factory");
    assertThat(factory.createProviders(context)).isEmpty();
  }

  private static McpServiceSession session(McpSyncClient client) throws Exception {
    var constructor =
        Class.forName("org.zalava.mcpbridge.McpJavaServiceConnector$Session")
            .getDeclaredConstructor(McpSyncClient.class);
    constructor.setAccessible(true);
    return (McpServiceSession) constructor.newInstance(client);
  }
}

package org.zalava.mcpbridge;

import java.util.List;
import java.util.Map;
import java.util.Objects;

record McpRegistration(
    String id,
    Transport transport,
    String endpoint,
    String command,
    List<String> arguments,
    Map<String, String> credentialReferences) {
  enum Transport {
    STREAMABLE_HTTP,
    STDIO
  }

  McpRegistration {
    requireText(id, "id");
    transport = Objects.requireNonNull(transport, "transport must not be null");
    arguments = arguments == null ? List.of() : List.copyOf(arguments);
    credentialReferences =
        credentialReferences == null ? Map.of() : Map.copyOf(credentialReferences);
    if (transport == Transport.STREAMABLE_HTTP) requireText(endpoint, "endpoint");
    if (transport == Transport.STDIO) requireText(command, "command");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
  }
}

package org.zalava.mcpbridge;

@FunctionalInterface
interface McpServiceConnector {
  McpServiceSession connect(McpRegistration registration);
}

package org.zalava.modules.mcpbridge;

@FunctionalInterface
interface McpServiceConnector {
  McpServiceSession connect(McpRegistration registration);
}

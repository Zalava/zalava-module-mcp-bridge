package org.zalava.mcpbridge;

import java.util.List;
import org.zalava.PromptDescriptor;
import org.zalava.ResourceDescriptor;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaToolDescriptor;
import tools.jackson.databind.JsonNode;

interface McpServiceSession extends AutoCloseable {
  List<ZalavaToolDescriptor> tools();

  List<ResourceDescriptor> resources();

  List<PromptDescriptor> prompts();

  ZalavaOperationResult callTool(String name, JsonNode arguments);

  ZalavaOperationResult readResource(String uri);

  ZalavaOperationResult resolvePrompt(String name, JsonNode arguments);

  @Override
  void close();
}

package org.zalava.modules.mcpbridge;

import java.util.List;
import org.zalava.api.PromptDescriptor;
import org.zalava.api.ResourceDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaToolDescriptor;
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

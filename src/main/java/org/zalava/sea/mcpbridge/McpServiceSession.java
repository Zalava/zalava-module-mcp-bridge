package org.zalava.mcpbridge;

import tools.jackson.databind.JsonNode;
import org.zalava.PromptDescriptor;
import org.zalava.ResourceDescriptor;
import org.zalava.SeaOperationResult;
import org.zalava.SeaToolDescriptor;

import java.util.List;

interface McpServiceSession extends AutoCloseable {
    List<SeaToolDescriptor> tools();
    List<ResourceDescriptor> resources();
    List<PromptDescriptor> prompts();
    SeaOperationResult callTool(String name, JsonNode arguments);
    SeaOperationResult readResource(String uri);
    SeaOperationResult resolvePrompt(String name, JsonNode arguments);
    @Override void close();
}

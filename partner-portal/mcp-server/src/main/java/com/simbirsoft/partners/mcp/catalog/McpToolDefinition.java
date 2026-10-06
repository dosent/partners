package com.simbirsoft.partners.mcp.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record McpToolDefinition(String name, String description, String behavior, String method, String path,
        List<ParameterDefinition> parameters, JsonNode requestSchema, JsonNode inputSchema, String requestMediaType,
        String responseMediaType, boolean adapterRequired) {
    public McpToolDefinition { parameters=List.copyOf(parameters); }
    public record ParameterDefinition(String name, String location, boolean required, JsonNode schema) { }
}

package com.simbirsoft.partners.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.mcp.backend.BackendApiClient;
import com.simbirsoft.partners.mcp.backend.BackendToolResult;
import com.simbirsoft.partners.mcp.security.PartnerApiKeyHeaderFilter;
import com.simbirsoft.partners.mcp.catalog.McpToolDefinition;
import com.simbirsoft.partners.mcp.catalog.OpenApiToolCatalog.ToolCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class OpenApiToolCallbackProvider implements ToolCallbackProvider {
    private final ToolCatalog catalog;
    private final BackendApiClient backend;
    private final ObjectMapper mapper;

    public OpenApiToolCallbackProvider(ToolCatalog catalog,BackendApiClient backend,ObjectMapper mapper) {
        this.catalog=catalog; this.backend=backend; this.mapper=mapper;
    }

    @Override public ToolCallback[] getToolCallbacks() {
        var callbacks=new ArrayList<ToolCallback>();
        catalog.tools().values().stream().filter(tool->!tool.adapterRequired())
                .forEach(tool->callbacks.add(callback(tool,args->render(backend.invoke(tool,args,currentKey())))));
        return callbacks.toArray(ToolCallback[]::new);
    }

    static ToolCallback callback(McpToolDefinition tool,Function<Map<String,Object>,String> function) {
        try {
            return FunctionToolCallback.builder(tool.name(),function)
                    .description(tool.description()).inputType(Map.class)
                    .inputSchema(tool.inputSchema().toString()).build();
        } catch (RuntimeException invalidSchema) {
            throw new IllegalArgumentException("MCP tool has invalid input schema: "+tool.name(),invalidSchema);
        }
    }

    static String currentKey() {
        var attributes=RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes))
            throw new IllegalStateException("Partner API key is missing from the current request");
        Object value=servletAttributes.getRequest().getAttribute(PartnerApiKeyHeaderFilter.REQUEST_ATTRIBUTE);
        if (!(value instanceof String key) || key.isBlank()) throw new IllegalStateException("Partner API key is missing from the current request");
        return key;
    }

    static String render(BackendToolResult result) {
        try {
            JsonNode body=new ObjectMapper().readTree(result.body());
            Map<String,Object> mapped=new LinkedHashMap<>();
            mapped.put("status",result.status());
            if (result.traceId()!=null) mapped.put("traceId",result.traceId());
            if(result.status()>=400) {
                mapped.put("error",Map.of("code",body.path("code").asText("BACKEND_ERROR"),
                        "message",body.path("message").asText("Backend operation failed")));
            } else mapped.put("result",body);
            return new ObjectMapper().writeValueAsString(mapped);
        } catch(Exception invalidJson) {
            return "{\"status\":"+result.status()+",\"traceId\":"+quote(result.traceId())+",\"result\":"+
                    quote(result.bodyText())+"}";
        }
    }
    private static String quote(String value) {
        if(value==null) return "null";
        try{return new ObjectMapper().writeValueAsString(value);}catch(Exception impossible){return "null";}
    }

}

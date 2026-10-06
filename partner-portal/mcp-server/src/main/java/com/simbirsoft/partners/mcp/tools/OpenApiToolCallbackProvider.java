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
        if (result.status() >= 400) throw backendError(result);
        try {
            JsonNode body=result.body().length==0?null:new ObjectMapper().readTree(result.body());
            Map<String,Object> mapped=new LinkedHashMap<>();
            mapped.put("status",result.status());
            if (result.traceId()!=null) mapped.put("traceId",result.traceId());
            mapped.put("result",body);
            return new ObjectMapper().writeValueAsString(mapped);
        } catch(Exception invalidJson) {
            throw new IllegalStateException("Backend returned an invalid JSON response");
        }
    }

    static RuntimeException backendError(BackendToolResult result) {
        String code="BACKEND_ERROR", message="Backend operation failed";
        try {
            JsonNode body=new ObjectMapper().readTree(result.body());
            code=body.path("code").asText(code);
            message=body.path("message").asText(message);
        } catch(Exception ignored) { }
        var error=new LinkedHashMap<String,Object>();
        error.put("status",result.status()); error.put("code",code); error.put("message",message);
        if(result.traceId()!=null) error.put("traceId",result.traceId());
        try { return new IllegalStateException(new ObjectMapper().writeValueAsString(error)); }
        catch(Exception impossible) { return new IllegalStateException("Backend operation failed"); }
    }
}

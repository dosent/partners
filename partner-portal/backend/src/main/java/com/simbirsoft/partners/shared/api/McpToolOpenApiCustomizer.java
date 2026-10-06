package com.simbirsoft.partners.shared.api;

import io.swagger.v3.oas.models.Operation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

public final class McpToolOpenApiCustomizer implements OperationCustomizer {
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        McpTool metadata = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), McpTool.class);
        if (metadata == null) return operation;
        var hints = new LinkedHashMap<String, Object>();
        hints.put("readOnlyHint", metadata.behavior().readOnlyHint());
        hints.put("destructiveHint", metadata.behavior().destructiveHint());
        hints.put("idempotentHint", metadata.behavior().idempotentHint());
        hints.put("openWorldHint", false);
        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("enabled", true);
        extension.put("name", metadata.name());
        extension.put("description", metadata.description());
        extension.put("behavior", metadata.behavior().name());
        extension.put("hints", hints);
        operation.addExtension("x-mcp", extension);
        return operation;
    }
}

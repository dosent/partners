package com.simbirsoft.partners.mcp.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class OpenApiToolCatalog {
    private static final List<String> METHODS=List.of("get", "post", "put", "patch", "delete");
    private final ObjectMapper mapper;

    public OpenApiToolCatalog(ObjectMapper mapper) { this.mapper=mapper; }

    public ToolCatalog load(URI openApiUri) {
        String spec=RestClient.create().get().uri(openApiUri).retrieve().body(String.class);
        if (spec == null || spec.isBlank()) throw new IllegalArgumentException("OpenAPI document is empty");
        return parse(spec);
    }

    public ToolCatalog parse(String openApiJson) {
        try {
            JsonNode root=mapper.readTree(openApiJson);
            if(!root.path("openapi").asText("").startsWith("3.")) throw new IllegalArgumentException("OpenAPI 3 document is required");
            JsonNode paths=root.path("paths");
            if (!paths.isObject()) throw new IllegalArgumentException("OpenAPI paths object is required");
            Map<String, McpToolDefinition> tools=new LinkedHashMap<>();
            Iterator<Map.Entry<String,JsonNode>> pathIterator=paths.fields();
            while (pathIterator.hasNext()) {
                var pathEntry=pathIterator.next();
                JsonNode pathItem=pathEntry.getValue();
                for (String method:METHODS) {
                    JsonNode operation=pathItem.get(method);
                    if (operation == null) continue;
                    JsonNode extension=operation.get("x-mcp");
                    if (extension == null) continue;
                    if (!extension.isObject() || !extension.has("enabled") || !extension.get("enabled").isBoolean())
                        throw new IllegalArgumentException("Malformed x-mcp extension");
                    if (!extension.get("enabled").asBoolean()) continue;
                    McpToolDefinition definition=parseTool(root,pathEntry.getKey(),method,operation,extension);
                    if (tools.putIfAbsent(definition.name(),definition) != null)
                        throw new IllegalArgumentException("Duplicate MCP tool name: " + definition.name());
                }
            }
            if (tools.isEmpty()) throw new IllegalArgumentException("OpenAPI catalog has no enabled MCP tools");
            return new ToolCatalog(Map.copyOf(tools));
        } catch (IllegalArgumentException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalArgumentException("Malformed OpenAPI document",failure); }
    }

    private McpToolDefinition parseTool(JsonNode root,String path,String method,JsonNode operation,JsonNode extension) {
        String name=text(extension,"name"), description=text(extension,"description"), behavior=text(extension,"behavior");
        if (!name.matches("[a-z][a-z0-9_]{2,63}") || description.isBlank() || description.length()>1000
                || !List.of("READ_ONLY","MUTATING","DESTRUCTIVE","IDEMPOTENT").contains(behavior))
            throw new IllegalArgumentException("Invalid x-mcp tool metadata");
        if (!path.startsWith("/api/v1/") || path.contains("..") || path.startsWith("//") || path.contains("?")
                || path.contains("#") || path.contains("\\") || path.contains("%") || path.contains(":") )
            throw new IllegalArgumentException("MCP tool path must be a fixed versioned backend path");

        List<McpToolDefinition.ParameterDefinition> parameters=new ArrayList<>();
        java.util.Set<String> parameterNames=new java.util.HashSet<>();
        JsonNode parameterNodes=operation.path("parameters");
        if (!parameterNodes.isMissingNode()) {
            if (!parameterNodes.isArray()) throw new IllegalArgumentException("Invalid operation parameters");
            for (JsonNode parameter:parameterNodes) {
                String parameterName=text(parameter,"name"), location=text(parameter,"in");
                if (parameterName.isBlank() || !List.of("path","query").contains(location) || parameter.path("schema").isMissingNode())
                    throw new IllegalArgumentException("Unsupported MCP parameter");
                if (!parameterNames.add(parameterName)) throw new IllegalArgumentException("Duplicate MCP argument name");
                parameters.add(new McpToolDefinition.ParameterDefinition(parameterName,location,
                        parameter.path("required").asBoolean(false),resolveSchema(parameter.path("schema"),root,new java.util.HashSet<>())));
            }
        }

        java.util.Set<String> declaredPathParameters=new java.util.HashSet<>();
        parameters.stream().filter(parameter->"path".equals(parameter.location())).forEach(parameter->declaredPathParameters.add(parameter.name()));
        var pathMatcher=java.util.regex.Pattern.compile("\\{([^{}]+)}").matcher(path);
        while(pathMatcher.find()) if(!declaredPathParameters.contains(pathMatcher.group(1)))
            throw new IllegalArgumentException("Undeclared MCP path parameter: "+pathMatcher.group(1));
        JsonNode requestSchema=null;
        String requestMedia=null;
        boolean adapterRequired=false;
        JsonNode requestBody=operation.get("requestBody");
        if (requestBody != null) {
            JsonNode content=requestBody.path("content");
            if (!content.isObject() || content.size()!=1) throw new IllegalArgumentException("MCP operation must declare one request media type");
            requestMedia=content.fieldNames().next();
            requestSchema=resolveSchema(content.path(requestMedia).path("schema"),root,new java.util.HashSet<>());
            if (!requestSchema.isObject() || !"object".equals(requestSchema.path("type").asText()))
                throw new IllegalArgumentException("MCP request schema must be an object");
            if ("application/json".equals(requestMedia)) { }
            else if ("multipart/form-data".equals(requestMedia) && "partner_lead_create_with_attachment".equals(name)) adapterRequired=true;
            else throw new IllegalArgumentException("Unsupported MCP request media type: " + requestMedia);
        }

        String responseMedia="application/json";
        JsonNode responses=operation.path("responses");
        if (responses.isObject()) {
            Iterator<Map.Entry<String,JsonNode>> responseIterator=responses.fields();
            while (responseIterator.hasNext()) {
                var responseEntry=responseIterator.next();
                if (!responseEntry.getKey().matches("2[0-9][0-9]|default")) continue;
                JsonNode content=responseEntry.getValue().path("content");
                if (!content.isObject() || content.isEmpty()) continue;
                if (content.size()!=1) throw new IllegalArgumentException("MCP operation must declare one success response media type");
                String media=content.fieldNames().next();
                if ("application/json".equals(media)) responseMedia=media;
                else if ("application/octet-stream".equals(media) && "partner_lead_attachment_download".equals(name)) {
                    responseMedia=media; adapterRequired=true;
                } else throw new IllegalArgumentException("Unsupported MCP response media type: " + media);
            }
        }
        ObjectNode inputSchema=mapper.createObjectNode().put("type","object").put("additionalProperties",false);
        ObjectNode properties=inputSchema.putObject("properties");
        ArrayNode required=inputSchema.putArray("required");
        for (var parameter:parameters) {
            properties.set(parameter.name(),parameter.schema().deepCopy());
            if (parameter.required() || "path".equals(parameter.location())) required.add(parameter.name());
        }
        if (requestSchema != null && requestSchema.path("properties").isObject()) {
            JsonNode bodyProperties=requestSchema.path("properties");
            Iterator<Map.Entry<String,JsonNode>> bodyFields=bodyProperties.fields();
            while (bodyFields.hasNext()) {
                var field=bodyFields.next();
                if (properties.has(field.getKey())) throw new IllegalArgumentException("Duplicate MCP argument name");
                properties.set(field.getKey(),field.getValue().deepCopy());
            }
            for (JsonNode requiredField:requestSchema.path("required")) {
                if (requiredField.isTextual()) required.add(requiredField.asText());
            }
        }
        if ("partner_lead_create_with_attachment".equals(name) && adapterRequired) {
            ObjectNode fileSchema=mapper.createObjectNode().put("type","object").put("additionalProperties",false);
            ObjectNode fileProperties=fileSchema.putObject("properties");
            fileProperties.putObject("filename").put("type","string");
            fileProperties.putObject("contentType").put("type","string");
            fileProperties.putObject("base64").put("type","string").put("contentEncoding","base64");
            ArrayNode fileRequired=fileSchema.putArray("required");
            fileRequired.add("filename"); fileRequired.add("contentType"); fileRequired.add("base64");
            properties.set("attachment",fileSchema);
            if(!contains(required,"attachment")) required.add("attachment");
        }
        return new McpToolDefinition(name,description,behavior,method.toUpperCase(Locale.ROOT),path,parameters,
                requestSchema,inputSchema,requestMedia,responseMedia,adapterRequired);
    }


    private JsonNode resolveSchema(JsonNode schema,JsonNode root,java.util.Set<String> activeRefs) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) throw new IllegalArgumentException("OpenAPI schema is required");
        JsonNode source=schema;
        if(schema.isObject()&&schema.has("$ref")) {
            String ref=schema.path("$ref").asText();
            if(!ref.startsWith("#/components/schemas/")||!activeRefs.add(ref))
                throw new IllegalArgumentException("Unsupported or recursive OpenAPI schema reference");
            JsonNode target=root.at(ref.substring(1));
            if(target.isMissingNode()) throw new IllegalArgumentException("Unresolved OpenAPI schema reference");
            ObjectNode merged=((ObjectNode)resolveSchema(target,root,activeRefs).deepCopy());
            schema.fields().forEachRemaining(entry->{if(!"$ref".equals(entry.getKey())) merged.set(entry.getKey(),resolveSchema(entry.getValue(),root,activeRefs));});
            activeRefs.remove(ref);
            return merged;
        }
        if(source.isObject()) {
            ObjectNode copy=mapper.createObjectNode();
            source.fields().forEachRemaining(entry->copy.set(entry.getKey(),resolveSchema(entry.getValue(),root,activeRefs)));
            return copy;
        }
        if(source.isArray()) {
            ArrayNode copy=mapper.createArrayNode();
            source.forEach(item->copy.add(resolveSchema(item,root,activeRefs)));
            return copy;
        }
        return source.deepCopy();
    }

    private static boolean contains(ArrayNode array,String value) {
        for(JsonNode element:array) if(element.isTextual()&&value.equals(element.asText())) return true;
        return false;
    }

    private static String text(JsonNode node,String field) {
        JsonNode value=node.get(field);
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }

    public record ToolCatalog(Map<String,McpToolDefinition> tools) {
        public ToolCatalog { tools=Map.copyOf(tools); }
    }
}

package com.simbirsoft.partners.mcp.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.mcp.catalog.McpToolDefinition;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.netty.http.client.HttpClient;

@Component
public class BackendApiClient {
    public static final int MAX_UPLOAD_BYTES=20*1024*1024;
    public static final int MAX_RESPONSE_BYTES=20*1024*1024;
    public static final String API_KEY_HEADER="X-Partner-API-Key";
    private final WebClient client;
    private final ObjectMapper mapper;
    private final Duration timeout;

    @Autowired
    public BackendApiClient(WebClient.Builder builder,ObjectMapper mapper,
            @Value("${BACKEND_API_URL}") String baseUrl,
            @Value("${mcp.backend.timeout:PT10S}") Duration timeout) {
        this(builder,mapper,baseUrl,timeout,MAX_RESPONSE_BYTES);
    }

    public BackendApiClient(WebClient.Builder builder,ObjectMapper mapper,String baseUrl,Duration timeout,int maxResponseBytes) {
        this.mapper=mapper; this.timeout=timeout;
        var connector=new ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout));
        var strategies=ExchangeStrategies.builder().codecs(codecs->codecs.defaultCodecs().maxInMemorySize(maxResponseBytes)).build();
        this.client=builder.clone().baseUrl(stripSlash(baseUrl)).clientConnector(connector).exchangeStrategies(strategies).build();
    }

    public BackendToolResult invoke(McpToolDefinition tool,Map<String,Object> arguments,String partnerKey) {
        validateArguments(tool,arguments);
        Map<String,Object> body=bodyArguments(tool,arguments);
        return send(tool,arguments,partnerKey,body.isEmpty()?null:body,null,null,null);
    }

    public BackendToolResult invokeMultipart(McpToolDefinition tool,Map<String,Object> arguments,String partnerKey,
            Map<String,Object> requestFields,String filename,String contentType,byte[] fileBytes) {
        if (!tool.adapterRequired() || !"partner_lead_create_with_attachment".equals(tool.name()))
            throw new IllegalArgumentException("No multipart adapter is registered for this tool");
        if (fileBytes==null || fileBytes.length>MAX_UPLOAD_BYTES) throw new IllegalArgumentException("Upload exceeds 20 MB limit");
        validateArguments(tool,arguments);
        return send(tool,arguments,partnerKey,requestFields,filename,contentType,fileBytes);
    }

    public int verifyPartnerKey(String partnerKey) {
        if(partnerKey==null||partnerKey.isBlank()) return 401;
        try {
            Integer status=client.get().uri("/api/v1/partner/mcp/access").header(API_KEY_HEADER,partnerKey)
                    .exchangeToMono(response->response.releaseBody().thenReturn(response.statusCode().value())).block(timeout);
            return status==null?503:status;
        } catch(RuntimeException unavailable) { return 503; }
    }

    public boolean backendHealthy() {
        try {
            Integer status=client.get().uri("/actuator/health/readiness")
                    .exchangeToMono(response->response.releaseBody().thenReturn(response.statusCode().value())).block(timeout);
            return status!=null&&status>=200&&status<300;
        } catch(RuntimeException unavailable) { return false; }
    }

    private BackendToolResult send(McpToolDefinition tool,Map<String,Object> args,String key,Map<String,Object> body,
            String filename,String contentType,byte[] fileBytes) {
        try {
            var request=client.method(org.springframework.http.HttpMethod.valueOf(tool.method()))
                    .uri(uri->buildUri(uri,tool,args)).header(API_KEY_HEADER,key);
            if(fileBytes!=null) {
                MultipartBodyBuilder multipart=new MultipartBodyBuilder();
                multipart.part("request",body,MediaType.APPLICATION_JSON);
                multipart.part("attachment",new ByteArrayResource(fileBytes){@Override public String getFilename(){return filename;}})
                        .contentType(MediaType.parseMediaType(contentType));
                request.contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(multipart.build()));
            } else if(body!=null) request.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
            return request.exchangeToMono(response->response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                    .map(bytes->new BackendToolResult(response.statusCode().value(),bytes,
                            response.headers().contentType().map(MediaType::toString).orElse("application/octet-stream"),
                            response.headers().header("X-Trace-Id").stream().findFirst().orElse(null))))
                    .block(timeout);
        } catch(RuntimeException failure) {
            if(hasCause(failure,java.util.concurrent.TimeoutException.class)||hasCause(failure,java.net.SocketTimeoutException.class))
                return safeFailure(504,"BACKEND_TIMEOUT");
            if(hasCause(failure,org.springframework.core.io.buffer.DataBufferLimitException.class))
                return safeFailure(502,"BACKEND_RESPONSE_TOO_LARGE");
            if(hasCause(failure,java.io.IOException.class)) return safeFailure(502,"BACKEND_UNAVAILABLE");
            throw failure;
        }
    }

    private URI buildUri(UriBuilder uri,McpToolDefinition tool,Map<String,Object> args) {
        Map<String,Object> pathVariables=new LinkedHashMap<>();
        var query=new LinkedMultiValueMap<String,String>();
        for(var parameter:tool.parameters()) {
            if(!args.containsKey(parameter.name())) continue;
            Object value=args.get(parameter.name());
            if("path".equals(parameter.location())) pathVariables.put(parameter.name(),value);
            else query.add(parameter.name(),String.valueOf(value));
        }
        uri.path(tool.path());
        query.forEach((key,values)->values.forEach(value->uri.queryParam(key,value)));
        return uri.build(pathVariables);
    }

    private void validateArguments(McpToolDefinition tool,Map<String,Object> args) {
        if(args==null) throw new IllegalArgumentException("Tool arguments are required");
        for(var parameter:tool.parameters()) if((parameter.required()||"path".equals(parameter.location()))&&!args.containsKey(parameter.name()))
            throw new IllegalArgumentException("Missing required argument: "+parameter.name());
        validateSchema(tool.inputSchema(),mapper.valueToTree(args),"arguments");
    }

    private void validateSchema(com.fasterxml.jackson.databind.JsonNode schema,
            com.fasterxml.jackson.databind.JsonNode value,String path) {
        String type=schema.path("type").asText("");
        if(value.isNull()&&schema.path("nullable").asBoolean(false)) return;
        boolean matches=switch(type) {
            case "object" -> value.isObject(); case "array" -> value.isArray(); case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber(); case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean(); case "" -> true;
            default -> throw new IllegalArgumentException("Unsupported input schema type at "+path);
        };
        if(!matches) throw new IllegalArgumentException("Invalid tool argument type at "+path);
        var enumeration=schema.path("enum");
        if(enumeration.isArray()&&enumeration.size()>0&&!contains(enumeration,value))
            throw new IllegalArgumentException("Invalid tool argument value at "+path);
        if(value.isObject()) {
            var properties=schema.path("properties");
            for(var required:schema.path("required")) if(required.isTextual()&&!value.has(required.asText()))
                throw new IllegalArgumentException("Missing required argument: "+path+"."+required.asText());
            if(!schema.path("additionalProperties").asBoolean(false)) {
                value.fieldNames().forEachRemaining(name->{if(!properties.has(name))
                    throw new IllegalArgumentException("Unknown tool argument: "+path+"."+name);});
            }
            value.fields().forEachRemaining(field->{if(properties.has(field.getKey()))
                validateSchema(properties.get(field.getKey()),field.getValue(),path+"."+field.getKey());});
        }
        if(value.isArray()&&schema.has("items")) value.forEach(item->validateSchema(schema.get("items"),item,path+"[]"));
    }

    private static boolean contains(com.fasterxml.jackson.databind.JsonNode choices,com.fasterxml.jackson.databind.JsonNode value) {
        for(var choice:choices) if(choice.equals(value)) return true;
        return false;
    }

    private Map<String,Object> bodyArguments(McpToolDefinition tool,Map<String,Object> args) {
        var body=new LinkedHashMap<String,Object>();
        if(tool.requestSchema()==null||!tool.requestSchema().path("properties").isObject()) return body;
        tool.requestSchema().path("properties").fieldNames().forEachRemaining(name->{if(args.containsKey(name))body.put(name,args.get(name));});
        return body;
    }

    private static BackendToolResult safeFailure(int status,String code) {
        return new BackendToolResult(status,("{\"code\":\""+code+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8),"application/json",null);
    }
    private static boolean hasCause(Throwable value,Class<? extends Throwable> type) {
        for(Throwable current=value;current!=null;current=current.getCause()) if(type.isInstance(current)) return true;
        return false;
    }
    private static String stripSlash(String value){return value.endsWith("/")?value.substring(0,value.length()-1):value;}
}

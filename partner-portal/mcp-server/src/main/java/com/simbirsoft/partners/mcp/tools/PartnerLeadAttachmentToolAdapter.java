package com.simbirsoft.partners.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.mcp.backend.BackendApiClient;
import com.simbirsoft.partners.mcp.backend.BackendToolResult;
import com.simbirsoft.partners.mcp.catalog.McpToolDefinition;
import com.simbirsoft.partners.mcp.catalog.OpenApiToolCatalog.ToolCatalog;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class PartnerLeadAttachmentToolAdapter implements ToolCallbackProvider {
    private static final String CREATE="partner_lead_create_with_attachment";
    private static final String DOWNLOAD="partner_lead_attachment_download";
    private final ToolCatalog catalog;
    private final BackendApiClient backend;
    private final ObjectMapper mapper;

    public PartnerLeadAttachmentToolAdapter(ToolCatalog catalog,BackendApiClient backend,ObjectMapper mapper) {
        this.catalog=catalog; this.backend=backend; this.mapper=mapper;
    }

    @Override public ToolCallback[] getToolCallbacks() {
        var callbacks=new ArrayList<ToolCallback>();
        var create=catalog.tools().get(CREATE);
        if(create!=null) callbacks.add(OpenApiToolCallbackProvider.callback(create,args->create(args,create)));
        var download=catalog.tools().get(DOWNLOAD);
        if(download!=null) callbacks.add(OpenApiToolCallbackProvider.callback(download,args->download(args,download)));
        return callbacks.toArray(ToolCallback[]::new);
    }

    private String create(Map<String,Object> args,McpToolDefinition tool) {
        Object request=args.get("request"), attachment=args.get("attachment");
        if(!(request instanceof Map<?,?> requestMap)||!(attachment instanceof Map<?,?> file))
            throw new IllegalArgumentException("request and attachment objects are required");
        String filename=string(file.get("filename")), contentType=string(file.get("contentType")), encoded=string(file.get("base64"));
        if(filename.isBlank()||filename.contains("/")||filename.contains("\\")||encoded.isBlank())
            throw new IllegalArgumentException("Invalid attachment metadata");
        if(encoded.length()>((BackendApiClient.MAX_UPLOAD_BYTES+2L)/3L)*4L+4L)
            throw new IllegalArgumentException("Upload exceeds 20 MB limit");
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(encoded); }
        catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("Attachment must be valid base64",invalid); }
        if(bytes.length>BackendApiClient.MAX_UPLOAD_BYTES) throw new IllegalArgumentException("Upload exceeds 20 MB limit");
        var requestFields=new LinkedHashMap<String,Object>();
        requestMap.forEach((key,value)->requestFields.put(String.valueOf(key),value));
        return OpenApiToolCallbackProvider.render(backend.invokeMultipart(tool,args,OpenApiToolCallbackProvider.currentKey(),
                requestFields,filename,contentType,bytes));
    }

    private String download(Map<String,Object> args,McpToolDefinition tool) {
        BackendToolResult response=backend.invoke(tool,args,OpenApiToolCallbackProvider.currentKey());
        if(response.status()>=400) throw OpenApiToolCallbackProvider.backendError(response);
        var body=new LinkedHashMap<String,Object>();
        body.put("status",response.status());
        if(response.traceId()!=null) body.put("traceId",response.traceId());
        if(response.status()>=400) {
            try {
                var error=mapper.readTree(response.body());
                body.put("error",Map.of("code",error.path("code").asText("BACKEND_ERROR"),
                        "message",error.path("message").asText("Attachment could not be downloaded")));
            } catch(Exception ignored) { body.put("error",Map.of("code","BACKEND_ERROR","message","Attachment could not be downloaded")); }
        } else {
            body.put("fileName",fileNameFromContentDisposition(response));
            body.put("contentType",response.contentType());
            body.put("base64",Base64.getEncoder().encodeToString(response.body()));
        }
        try { return mapper.writeValueAsString(body); }
        catch(Exception impossible) { return "{\"status\":502,\"error\":{\"code\":\"SERIALIZATION_FAILED\"}}"; }
    }

    private static String fileNameFromContentDisposition(BackendToolResult response) {
        // Backend attachment adapter returns the safe file bytes. Filename is intentionally omitted if not exposed.
        return "attachment";
    }
    private static String string(Object value) { return value instanceof String text ? text : ""; }
}

package com.simbirsoft.partners.mcp.backend;

public record BackendToolResult(int status, byte[] body, String contentType, String traceId) {
    public BackendToolResult { body=body == null ? new byte[0] : body.clone(); }
    @Override public byte[] body() { return body.clone(); }
    public String bodyText() { return new String(body,java.nio.charset.StandardCharsets.UTF_8); }
}

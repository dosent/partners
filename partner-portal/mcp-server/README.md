# Partner MCP service

Standalone Spring Boot 3.5 / Spring AI 1.1.8 adapter. It exposes MCP Streamable HTTP at `/mcp` and reads its allowlisted tools from the backend OpenAPI `x-mcp` extensions at startup. It has no database connection, JPA repositories, frontend assets, or database credentials.

## Local Compose

Copy `.env.example` to `.env`, then run:

```sh
docker compose up --build mcp
```

The local endpoint is `http://localhost:${MCP_PORT:-8081}/mcp`; readiness is `/actuator/health/readiness`. Compose supplies internal backend URLs. Outside local Compose, set `BACKEND_API_URL` and `BACKEND_OPENAPI_URL` through deployment environment/secret configuration. Do not bake credentials into the image.

## Client authentication

Every MCP HTTP request must include the partner's static API key in `X-Partner-API-Key` over HTTPS. Configure that custom header in the MCP client. This product API key is not an MCP OAuth access token and must not be sent as `Authorization: Bearer`, in a URL, or in logs. The service asks backend `GET /api/v1/partner/mcp/access` to validate the key on every MCP request, including `tools/list`; each tool call forwards the same key to the ordinary backend route, where owner checks run again. Key creation, list, and revoke happen in the partner portal using its web session.

HTTP MCP transports do not authenticate clients by themselves; this service's header filter enforces the partner key before the MCP transport handles `/mcp`. Keep health endpoints on the internal/operations network. Production ingress must also set a per-key/partner rate limit before exposing the hostname.

## Adding a tool

Implement the normal partner backend endpoint and application use case, enforce owner scope in backend, then add `@McpTool(name, description, behavior)` to only the controller method intended for partners. SpringDoc publishes `x-mcp`; unmarked routes stay hidden. Check the OpenAPI and backend rules in [backend README](../backend/README.md). Generic JSON methods become MCP tools without changing this service. Multipart/binary behavior uses the named adapter and its 20 MB upload and 20 MB backend response bounds.

The catalog is loaded at service startup. After changing `x-mcp`, restart/recreate this container to load the new catalog. The parser rejects malformed metadata, duplicate names, external backend paths, unsupported media types, or schemas it cannot resolve. Never accept a caller-provided method or URL.

## Client compatibility

MCP clients differ in whether they let a user configure a static HTTP header. Validate the selected partner client(s) before announcing support. If a client cannot send `X-Partner-API-Key`, it is not compatible with this product authentication flow; do not weaken backend authorization to work around it.

# Proposal

## Why

Партнёрам нужно подключать MCP-клиенты к функциям кабинета, управлять интеграционными ключами и безопасно расширять набор доступных операций вместе с backend API.

## What Changes

- Добавить создание, листинг и отзыв партнёрских MCP API-ключей в кабинете; секрет показывается только при выпуске.
- Добавить отдельный stateless MCP HTTP-сервис в собственном Docker image, который вызывает только явно опубликованные backend операции.
- Размечать доступные backend endpoints аннотацией `@McpTool` и экспортировать metadata в OpenAPI `x-mcp`.
- Передавать статический API key в `X-Partner-API-Key`; backend валидирует его для каждого запроса и применяет обычный owner scope.
- Документировать добавление операций и custom adapters для multipart/binary.

## Capabilities

### New Capabilities

- `partner-mcp-access`: выпуск и проверка API-ключей, MCP-каталог партнёрских операций и аудит вызовов.

### Modified Capabilities

- Нет.

## Impact

- Backend: access module, Liquibase, Spring Security, партнёрские controllers и SpringDoc.
- Frontend: раздел API/MCP-ключей в кабинете и API-клиент с cookie/CSRF.
- Новый отдельный `mcp-server/` Spring Boot service и Docker image.
- Docker Compose, README, OpenSpec и browser E2E.

# Tasks

## 1. API keys and backend authorization

- [x] 1.1 Добавить хешируемые ключи, Liquibase changeset, owner-scoped issue/list/revoke и одноразовый показ секрета.
- [x] 1.2 Добавить API-key authentication filter и закрытый access check; управление ключами оставить только web-session principal.
- [x] 1.3 Добавить тесты хеширования, отзыва, session/API-key authorization и owner isolation.

## 2. Partner UI

- [x] 2.1 Добавить раздел ключей с созданием, одноразовым отображением секрета, листингом и подтверждаемым отзывом.
- [x] 2.2 Добавить локализационные ключи и frontend/API tests.

## 3. OpenAPI MCP catalog

- [x] 3.1 Добавить `@McpTool`, behavior metadata и SpringDoc `x-mcp`; размечать только существующие partner operations.
- [x] 3.2 Документировать процедуру публикации API и проверки OpenAPI.

## 4. Separate MCP service

- [x] 4.1 Создать отдельное Spring Boot MCP Streamable HTTP приложение, OpenAPI catalog parser и generic JSON adapter.
- [x] 4.2 Проверять ключ через backend на каждом MCP HTTP запросе и передавать его backend при tool call.
- [x] 4.3 Добавить explicit multipart/binary adapter, лимиты размера/таймаута, readiness и Docker image без DB credentials.

## 5. Integration and verification

- [x] 5.1 Добавить Compose wiring и E2E сценарии create/list/call/isolation/revoke.
- [x] 5.2 Выполнить backend, frontend, MCP, OpenSpec и Compose E2E проверки; документировать эксплуатацию и MCP client compatibility.

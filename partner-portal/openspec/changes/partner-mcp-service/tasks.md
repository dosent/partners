# Tasks

## 1. API keys and backend authorization

- [ ] 1.1 Добавить хешируемые ключи, Liquibase changeset, owner-scoped issue/list/revoke и одноразовый показ секрета.
- [ ] 1.2 Добавить API-key authentication filter и закрытый access check; управление ключами оставить только web-session principal.
- [ ] 1.3 Добавить тесты хеширования, отзыва, session/API-key authorization и owner isolation.

## 2. Partner UI

- [ ] 2.1 Добавить раздел ключей с созданием, одноразовым отображением секрета, листингом и подтверждаемым отзывом.
- [ ] 2.2 Добавить локализационные ключи и frontend/API tests.

## 3. OpenAPI MCP catalog

- [ ] 3.1 Добавить `@McpTool`, behavior metadata и SpringDoc `x-mcp`; размечать только существующие partner operations.
- [ ] 3.2 Документировать процедуру публикации API и проверки OpenAPI.

## 4. Separate MCP service

- [ ] 4.1 Создать отдельное Spring Boot MCP Streamable HTTP приложение, OpenAPI catalog parser и generic JSON adapter.
- [ ] 4.2 Проверять ключ через backend на каждом MCP HTTP запросе и передавать его backend при tool call.
- [ ] 4.3 Добавить explicit multipart/binary adapter, лимиты размера/таймаута, readiness и Docker image без DB credentials.

## 5. Integration and verification

- [ ] 5.1 Добавить Compose wiring и E2E сценарии create/list/call/isolation/revoke.
- [ ] 5.2 Выполнить backend, frontend, MCP, OpenSpec и Compose E2E проверки; документировать эксплуатацию и MCP client compatibility.

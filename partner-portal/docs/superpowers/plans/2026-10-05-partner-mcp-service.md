# MCP-сервис для партнёров — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дать партнёрам возможность управлять MCP API-ключами в кабинете и выполнять через отдельный MCP Docker-сервис все уже реализованные партнёрские backend операции.

**Architecture:** Backend `access` хранит хеши ключей, выпускает партнёрский security principal и остаётся источником проверки owner scope. `@McpTool` добавляет явные `x-mcp` метаданные в OpenAPI; отдельный Java 21 / Spring Boot 3.5 сервис загружает этот каталог и переводит MCP-вызовы в REST-вызовы backend, передавая ключ в `X-Partner-API-Key`. UI ключей использует существующую сессию Yandex ID и опубликованные локали.

**Tech Stack:** React 19 / Vite, Java 21, Spring Boot 3.5.16, Spring AI MCP Server WebMVC 1.1.8, Spring Security, PostgreSQL 16, Liquibase, Docker Compose, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-05-partner-mcp-service-design.md`

## Global Constraints

- Партнёр входит через Yandex ID; сотрудники используют LDAP; локальные пароли, 2FA и подтверждение email не добавляются.
- API маршруты остаются под `/api/v1`; DTO — Java records с `@Schema`; OpenAPI создаётся SpringDoc.
- Backend остаётся модульным монолитом; доступ к данным другого модуля идёт через application interface/domain event.
- DDL добавляется только новым Liquibase changeset; хранимые процедуры и `SimpleJdbcCall` запрещены.
- Секрет ключа показывается один раз; в базе хранится только хеш; ключи не попадают в логи, image, frontend bundle и URL.
- MCP endpoint принимает ключ в `X-Partner-API-Key`; это продуктовый API-key механизм, не OAuth-аутентификация MCP.
- MCP публикует только API-методы с явным `@McpTool`/`x-mcp`; backend проверяет партнёра и owner scope при каждом REST вызове.
- RU/KZ/EN строки берутся из управляемой людьми локализации; машинный перевод и текст юридических документов не добавляются.
- Использовать только вымышленные данные в тестах; production credentials не нужны.
- В feature-ветке запускать применимые unit/API/integration/architecture/frontend проверки и обязательный браузерный Compose E2E до интеграционного коммита в локальную `main`.

## Review Focus

- **Пустой, неверный или отозванный ключ:** запрос должен получить `401`, даже если одновременно отправлена валидная browser session. Проверить в Task 2.
- **Ключ принадлежит другому партнёру или передан чужой object ID:** backend не должен менять owner scope; проверка Task 2 и изоляционный E2E в Task 6.
- **API-key обращается к управлению другими ключами:** операции списка/создания/отзыва доступны только Yandex web session, не API-key principal. Проверить в Task 2.
- **OpenAPI содержит неразмеченный или некорректно размеченный метод:** такой метод не становится инструментом; некорректная MCP-схема не запускается как произвольный REST вызов. Проверить в Task 4 и Task 5.
- **Multipart upload или бинарное вложение:** данные проходят через явный адаптер с лимитом размера и корректным MCP result; ключ и содержимое не журналируются. Проверить в Task 5 и Task 6.

---

## Структура файлов

| Область | Основные файлы и ответственность |
|---|---|
| OpenSpec | `openspec/changes/partner-mcp-service/` — требования, решение, сценарии и отслеживаемые задачи |
| Backend API key | `backend/src/main/java/com/simbirsoft/partners/access/{api,application,domain,infrastructure}/` — DTO/controller, use cases/store contract, credential aggregate, persistence/filter |
| Backend schema | Новый `backend/src/main/resources/db/changelog/013-partner-api-keys.yaml` и подключение из `db.changelog-master.yaml` |
| MCP exposure metadata | `backend/src/main/java/com/simbirsoft/partners/shared/api/McpTool.java`, `McpToolBehavior.java`, `ApiDocumentationConfig.java`; тесты в `backend/src/test/java/com/simbirsoft/partners/shared/api/` |
| Existing partner operations | `partner/api/PartnerProfileController.java`, `lead/api/PartnerLeadController.java`; только партнёрские операции размечаются |
| MCP service | Новый самостоятельный `mcp-server/` Gradle/Spring Boot проект: MCP transport, OpenAPI catalog, generic JSON REST adapter, key verification, multipart/binary adapters |
| Web UI | `src/App.jsx` для секции кабинета, новый `src/partner/PartnerApiKeysSection.jsx` и CSS, `src/api/partnerRegistrationApi.js` для cookie/CSRF методов |
| Localization | `backend/.../RegistrationContentService.java`, `src/registration/partnerProfileContentDefaults.js`, `e2e/support/test-data.js` — обязательные ключи и тестовые RU/KZ/EN строки; переводные значения предоставляет локализация, не MCP-код |
| Deployment/docs/e2e | `compose.yaml`, `compose.e2e.yaml`, `backend/README.md`, новый `mcp-server/README.md`, `e2e/partner-mcp-access.spec.js`, `playwright.config.js` при необходимости |

## Task 1: Зафиксировать функцию в OpenSpec

**Files:**
- Create: `openspec/changes/partner-mcp-service/proposal.md`
- Create: `openspec/changes/partner-mcp-service/design.md`
- Create: `openspec/changes/partner-mcp-service/tasks.md`
- Create: `openspec/changes/partner-mcp-service/specs/partner-mcp-access/spec.md`

**Interfaces:**
- Produces: capability `partner-mcp-access` с требованиями к ключам, статическому API-key HTTP-заголовку, OpenAPI `x-mcp`, отдельному MCP service и аудиту.

- [ ] Перенести в proposal/design утверждённое решение из ссылки на spec; зафиксировать ключ `X-Partner-API-Key`, один раз показываемый секрет, session-only key management, Streamable HTTP, `@McpTool` → `x-mcp`, отдельный Docker image, отсутствие DB credentials в MCP.
- [ ] Добавить scenarios: выпуск/листинг/отзыв, невалидный и отозванный ключ, чужой объект, инструменты только для `x-mcp`, generic JSON вызов, multipart/binary adapter.
- [ ] Запустить `openspec validate partner-mcp-service --strict`; исправить ошибки структурных заголовков/ссылок до перехода к коду.
- [ ] Закоммитить: `docs: specify partner MCP service`.

## Task 2: Ключи партнёра и backend authentication

**Files:**
- Create: `backend/src/main/java/com/simbirsoft/partners/access/domain/PartnerApiKey.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/application/PartnerApiKeyService.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/application/PartnerApiKeyStore.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/PartnerApiKeyRecord.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/PartnerApiKeyJpaRepository.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/JpaPartnerApiKeyStore.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/PartnerApiKeyAuthenticationFilter.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/PartnerApiKeyAuthenticationToken.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/api/PartnerApiKeyController.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/api/PartnerApiKeyResponse.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/api/CreatedPartnerApiKeyResponse.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/api/PartnerMcpAccessController.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/api/PartnerMcpAccessResponse.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/SecurityConfiguration.java`
- Create: `backend/src/main/resources/db/changelog/013-partner-api-keys.yaml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- Test: `backend/src/test/java/com/simbirsoft/partners/access/application/PartnerApiKeyServiceTest.java`
- Test: `backend/src/test/java/com/simbirsoft/partners/access/infrastructure/PartnerApiKeyPersistenceTest.java`
- Test: `backend/src/test/java/com/simbirsoft/partners/access/api/PartnerApiKeyAuthenticationMockMvcTest.java`

**Interfaces:**
- `PartnerApiKeyService.create(PartnerPrincipal owner, String name) -> CreatedKey`; `list(PartnerPrincipal owner) -> List<KeySummary>`; `revoke(PartnerPrincipal owner, UUID keyId) -> void`; `authenticate(String rawKey) -> Optional<ApiKeyIdentity>`.
- `PartnerApiKeyStore` reads/writes credential records by key UUID and owner `(provider, subject)`; it exposes no cross-module JPA entity.
- `PartnerApiKeyResponse(id, name, prefix, createdAt, lastUsedAt, revokedAt)`; `CreatedPartnerApiKeyResponse(id, name, prefix, secret, createdAt)`.
- Routes: `GET/POST /api/v1/partner/api-keys`, `DELETE /api/v1/partner/api-keys/{keyId}`, `GET /api/v1/partner/mcp/access`.

- [ ] Add failing `PartnerApiKeyServiceTest` cases for a one-time generated secret (`ppk_<uuid>_<base64url-32-byte-secret>`), hash-only persistence, owner-scoped list/revoke, and revoked-key rejection.
- [ ] Run `cd backend && ./gradlew test --tests '*PartnerApiKeyServiceTest'`; confirm the tests fail for missing implementation.
- [ ] Implement access-module domain/service/store. Hash the complete random key with SHA-256; use `MessageDigest.isEqual` for comparison. Persist UUID, identity provider/subject, name, visible prefix, hash, created/last-used/revoked timestamps via new Liquibase changeset.
- [ ] Add MockMvc cases for web-session-only key management, valid custom-header principal, missing/invalid/revoked key, request with valid session plus invalid key, and denied cross-owner access. The API-key principal has `ROLE_PARTNER` plus `PARTNER_API_KEY`; a web-session principal has `PARTNER_WEB_SESSION`. Management routes require the latter; MCP access endpoint requires the former.
- [ ] Install key filter before CSRF; bypass CSRF only for requests carrying `X-Partner-API-Key`, and reject invalid key headers without falling back to a web session. Never save API-key principal in an HTTP session.
- [ ] Add `GET /api/v1/partner/mcp/access`, returning only key ID/status needed by MCP service; do not mark this endpoint with `@McpTool`.
- [ ] Run `cd backend && ./gradlew test --tests '*PartnerApiKey*'` and `cd backend && ./gradlew test --tests '*PostgreSqlStartupTest'`; confirm mapping and changeset.
- [ ] Commit: `feat: add partner MCP API keys`.

## Task 3: Управление ключами в кабинете партнёра

**Files:**
- Create: `src/partner/PartnerApiKeysSection.jsx`
- Create: `src/partner/PartnerApiKeysSection.css`
- Modify: `src/App.jsx`
- Modify: `src/api/partnerRegistrationApi.js`
- Modify: `backend/src/main/java/com/simbirsoft/partners/localization/application/RegistrationContentService.java`
- Modify: `src/registration/partnerProfileContentDefaults.js`
- Modify: `e2e/support/test-data.js`
- Test: `tests/partner-api-keys.test.mjs`
- Test: `e2e/partner-mcp-access.spec.js`

**Interfaces:**
- `listMyPartnerApiKeys() -> Promise<KeySummary[]>`, `createMyPartnerApiKey(name) -> Promise<CreatedKey>`, `revokeMyPartnerApiKey(id) -> Promise<void>` in `partnerRegistrationApi.js`; mutating requests use the existing CSRF helper.
- `PartnerApiKeysSection({content, onError})` renders the list, create form, one-time secret reveal, and per-key revoke state.
- Localization keys are `portal.apiKeys.navigation`, `title`, `description`, `empty`, `name.label`, `actions.create`, `actions.creating`, `actions.revoke`, `actions.cancel`, `status.created`, `status.revoked`, `loading`, `errors.load`, `errors.create`, `errors.revoke`, `secret.title`, `secret.description`, and `revoke.confirm`; E2E content uses synthetic `E2E <locale> ...` text only.

- [ ] Add failing node tests proving GET includes cookie credentials, POST/DELETE fetch CSRF header and send JSON/id correctly, and API errors preserve status/code.
- [ ] Run `node --test tests/partner-api-keys.test.mjs`; confirm failures.
- [ ] Implement the API client functions using `csrfHeaders()` and existing `decode()` without copying key values to localStorage/sessionStorage.
- [ ] Add a partner-cabinet `API/MCP keys` section to `ApprovedPartnerPortal`, load list on open, show secret only in a dismissible one-time view, clear it from React state on close/unmount, and require explicit confirmation before revoke.
- [ ] Add the listed `portal.apiKeys.*` names to the required localization contract and E2E fixture for RU/KZ/EN; add Russian-only legacy fallback where existing localization behavior requires it. Do not add machine translations or legal copy. Do not show an enabled API-key section when required published strings are missing.
- [ ] Run `node --test tests/partner-api-keys.test.mjs`; confirm pass. Add browser assertions for create/list/one-time display/revoke/error and keyboard/viewport behavior to the e2e scenario; synthetic translations stay test-only.
- [ ] Commit: `feat: add partner API key management UI`.

## Task 4: Явная OpenAPI-разметка MCP-инструментов

**Files:**
- Create: `backend/src/main/java/com/simbirsoft/partners/shared/api/McpTool.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/shared/api/McpToolBehavior.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/shared/api/ApiDocumentationConfig.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/partner/api/PartnerProfileController.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/lead/api/PartnerLeadController.java`
- Modify: `backend/README.md`
- Test: `backend/src/test/java/com/simbirsoft/partners/shared/api/McpToolOpenApiCustomizerTest.java`
- Test: `backend/src/test/java/com/simbirsoft/partners/shared/api/ApiDocumentationConfigTest.java`

**Interfaces:**
- `@McpTool(name, description, behavior)` is runtime-retained and targets controller methods.
- `McpToolBehavior` values: `READ_ONLY`, `MUTATING`, `DESTRUCTIVE`, `IDEMPOTENT`; the `OperationCustomizer` exports `x-mcp.enabled`, `name`, `description`, and the mapped behavior hints.
- The MCP catalog includes: profile read/update; lead board/create (JSON and multipart); lead detail; attachment read; partner message. API-key management and MCP access endpoints stay unmarked.

- [ ] Add failing customizer tests proving marked `HandlerMethod` yields the complete `x-mcp` extension and unmarked method yields none; verify name/description and behavioral hints remain stable.
- [ ] Run `cd backend && ./gradlew test --tests '*McpToolOpenApiCustomizerTest'`; confirm failure.
- [ ] Implement `@McpTool`, enum and SpringDoc `OperationCustomizer`; annotate only the listed existing partner controller methods. Model file upload/download as distinct names for custom adapters.
- [ ] Extend `ApiDocumentationConfigTest` to assert generated `/v3/api-docs` excludes unmarked management/admin routes and preserves JSON DTO schemas for marked routes.
- [ ] Document in `backend/README.md` how to add a backend use case, add the annotation, choose stable tool name/description/behavior, verify `x-mcp`, and implement a custom adapter for multipart/binary/long-running behavior. Include the minimal Java example from the approved design.
- [ ] Run `cd backend && ./gradlew test --tests '*McpTool*' --tests '*ApiDocumentationConfigTest'`; inspect OpenAPI output locally and confirm marker allowlist.
- [ ] Commit: `feat: publish explicit MCP tool metadata`.

## Task 5: Отдельный MCP Docker-сервис

**Files:**
- Create: `mcp-server/settings.gradle`
- Create: `mcp-server/build.gradle`
- Create: `mcp-server/src/main/java/com/simbirsoft/partners/mcp/McpServerApplication.java`
- Create: `mcp-server/src/main/java/com/simbirsoft/partners/mcp/catalog/OpenApiToolCatalog.java`
- Create: `mcp-server/src/main/java/com/simbirsoft/partners/mcp/catalog/McpToolDefinition.java`
- Create: `mcp-server/src/main/java/com/simbirsoft/partners/mcp/security/PartnerApiKeyHeaderFilter.java`
- Create: `mcp-server/src/main/java/com/simbirsoft/partners/mcp/backend/BackendApiClient.java`
- Create: `mcp-server/src/main/java/com/simbirsoft/partners/mcp/tools/OpenApiToolCallbackProvider.java`
- Create: `mcp-server/src/main/java/com/simbirsoft/partners/mcp/tools/PartnerLeadAttachmentToolAdapter.java`
- Create: `mcp-server/src/main/java/com/simbirsoft/partners/mcp/health/McpHealthController.java`
- Create: `mcp-server/src/main/resources/application.yml`
- Create: `mcp-server/Dockerfile`
- Create: `mcp-server/README.md`
- Test: `mcp-server/src/test/java/com/simbirsoft/partners/mcp/catalog/OpenApiToolCatalogTest.java`
- Test: `mcp-server/src/test/java/com/simbirsoft/partners/mcp/tools/OpenApiToolCallbackProviderTest.java`
- Test: `mcp-server/src/test/java/com/simbirsoft/partners/mcp/backend/BackendApiClientTest.java`
- Modify: `compose.yaml`

**Interfaces:**
- `OpenApiToolCatalog.load(URI openApiUri) -> ToolCatalog` parses only operations with `x-mcp.enabled=true`; `ToolCatalog` maps stable tool name to HTTP method/path/input schema/behavior.
- `BackendApiClient.invoke(ToolDefinition tool, Map<String,Object> arguments, String partnerKey) -> BackendToolResult` substitutes validated path/query/JSON body fields, sends `X-Partner-API-Key`, enforces timeouts and response-size bounds, and never accepts a caller-supplied URL or method.
- `PartnerApiKeyHeaderFilter` requires `X-Partner-API-Key` on MCP HTTP requests and verifies it with backend `GET /api/v1/partner/mcp/access` before tool dispatch.
- `OpenApiToolCallbackProvider` exposes generic JSON operations as MCP tools; `PartnerLeadAttachmentToolAdapter` owns multipart/binary conversion only.

- [ ] Add failing catalog tests for valid marker extraction, hidden unmarked operation, duplicate tool name rejection, malformed extension rejection and unsupported media type rejection.
- [ ] Run `./backend/gradlew -p mcp-server test --tests '*OpenApiToolCatalogTest'`; confirm failures.
- [ ] Create standalone Java 21/Spring Boot 3.5 app using Spring AI MCP Server WebMVC 1.1.8 with Streamable HTTP; load OpenAPI on startup from runtime `BACKEND_OPENAPI_URL`. Use the official Spring AI MCP transport; implement custom-header verification separately and do not present it as OAuth.
- [ ] Implement a generic callback provider limited to OpenAPI-declared method/path/request schema; reject unknown argument fields and unsupported media types. Map response/error envelopes and trace IDs without logging headers, arguments or payloads.
- [ ] Add failing backend-client tests for `X-Partner-API-Key` forwarding, URL/method non-overriding, timeout, 401/403 propagation, maximum 20 MB upload and bounded response, and multipart/binary adapter behavior; then implement the WebClient client and custom attachment adapter.
- [ ] Add `/actuator/health` readiness requiring a valid OpenAPI catalog and reachable backend; provide `BACKEND_API_URL` and `BACKEND_OPENAPI_URL` only through environment variables. Document that changing `x-mcp` requires restarting/recreating the container so the new catalog is loaded.
- [ ] Add separate Docker image. Build with `./backend/gradlew -p mcp-server bootJar`; runtime image has no DB credentials or frontend assets. Add `mcp` to `compose.yaml` with backend dependency, internal network and health check.
- [ ] Run `./backend/gradlew -p mcp-server test bootJar`; run `DB_PASSWORD=dummy S3_ACCESS_KEY=dummy S3_SECRET_KEY=dummy docker compose --env-file .env.example config`; ensure no required production secrets are output.
- [ ] Commit: `feat: add partner MCP adapter service`.

## Task 6: Сквозная проверка, документация и поставка

**Files:**
- Modify: `compose.e2e.yaml`
- Modify: `e2e/partner-mcp-access.spec.js`
- Modify: `e2e/support/fixtures.js`
- Modify: `e2e/support/test-data.js`
- Modify: `backend/README.md`
- Modify: `mcp-server/README.md`
- Modify: `openspec/changes/partner-mcp-service/tasks.md`

**Interfaces:**
- `compose.e2e.yaml` запускает настоящий frontend, backend, PostgreSQL и отдельный MCP container с фиктивными e2e параметрами.
- E2E использует `loginAsPartner(page, subject)` и текущую тестовую публикацию контента; MCP JSON-RPC requests используют официальный протокол и `X-Partner-API-Key`.

- [x] Добавить e2e успешного пути: партнёр входит, создаёт ключ через кабинет, видит его один раз, подключается к `/mcp`, запрашивает `tools/list`, вызывает профиль/свой лид и отзывает ключ.
- [x] Добавить e2e проверки: нет ключа/отозванный ключ → `401`; второй партнёр не получает первый профиль/лид; API-key не может читать или отзывать список ключей; multipart attachment путь отрабатывает корректным MCP результатом.
- [x] Добавить отдельный `mcp-server` README: Compose запуск, endpoint, custom header configuration, подключение тестового клиента, как backend-изменение с `@McpTool` попадает в каталог, когда нужен custom adapter, обновление каталога и оговорка по совместимости MCP-клиентов.
- [x] Убедиться, что документация говорит о ключе как о статическом API key, не как о MCP OAuth access token, и не советует помещать его в URL/логи.
- [x] Запустить `openspec validate partner-mcp-service --strict`, `cd backend && ./gradlew test`, `./backend/gradlew -p mcp-server test bootJar`, `npm run build`, применимые `node --test` проверки и обязательный `npm run test:e2e` (Compose с PostgreSQL). Исправлять ошибки и повторять e2e до успеха.
- [x] Выполнить применимые ArchUnit/API/security/translation checks, проверить `git diff --check`, отсутствие секретов/демо-данных в production образе и статус `.env`/`.env.example`.
- [x] Закоммитить документацию и e2e: `test: verify partner MCP access end to end`.
- [ ] После успешных проверок интегрировать feature-ветку отдельным коммитом в локальную `main`; не выполнять push.

## Notes

- Spring AI 1.1.8 предназначена для Spring Boot 3.5.x и является зафиксированной версией; MCP security header остаётся нашей интеграционной политикой поверх транспорта, не сторонним security starter.
- У целевых MCP-хостов в задаче не задан список. До production-включения проверяются реальные хосты партнёров на поддержку статического `X-Partner-API-Key`; пока список не зафиксирован, совместимость обозначается условной.
- В репозитории нет production ingress конфигурации. До публикации MCP hostname инфраструктурный владелец должен установить и проверить rate limits по ключу/партнёру; feature код сам не выдаётся за распределённый rate limiter.
- Реальная backend-функция публикуется через MCP только после успешной проверки собственной backend авторизации и owner scope. UI mock-only разделы не получают фиктивных MCP операций.

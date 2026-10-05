# План реализации заявок на специалистов

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Цель:** Дать одобренному партнёру рабочую форму создания заявки на специалиста, серверное сохранение и список собственных заявок.

**Архитектура:** Модуль `staffing` владеет агрегатом и API; единственная связь первой версии — доступный партнёру лид, проверяемый через application interface модуля `lead`. Файлы идут через `document`, строки формы берутся из существующей публикации localization. В рабочем кабинете форма находится отдельным компонентом; существующий `/demo` и его mock-данные не меняются.

**Технологический стек:** React 19 / Vite, Java 21 / Spring Boot 3, Spring Data JPA, PostgreSQL, Liquibase, Playwright, JUnit 5, Mockito, Spring MockMvc.

**Спецификация:** `openspec/changes/partner-staffing-request-intake/specs/staffing-request-intake/spec.md`; техническое решение — `openspec/changes/partner-staffing-request-intake/design.md`.

## Global Constraints

- Backend SHALL remain a modular monolith with modules `staffing`, `lead`, `document`, and `localization`.
- API SHALL use `/api/v1`; DTOs SHALL be Java records and documented through SpringDoc.
- DDL SHALL use new Liquibase changesets; applied changesets SHALL NOT be edited.
- JPA entities SHALL NOT cross API or module boundaries; module calls SHALL use application interfaces.
- Partner ownership and project/lead access SHALL be checked server-side from the authenticated session.
- RU/KZ/EN text SHALL be authored and published by people; machine translation SHALL NOT be used.
- Files SHALL be type/size checked and malware-scanned before use; file access SHALL follow request ownership.
- Add a browser e2e scenario for the successful partner request path and run it against built frontend, backend, and PostgreSQL.
- Keep production free of demo business records; do not modify unrelated hosting/site files.

## Review Focus

- Replayed create requests must return the original request and not create duplicates.
- A partner must not read or attach files to another partner's request or lead.
- Optional file upload failure must not erase the typed request or falsely report success.
- Flexible start date and conditional language fields must validate consistently in browser and server.
- Missing locale strings must never be replaced by machine translation or hidden behind a successful form state.

---

### Task 1: Домен заявок и хранение данных

**Files:**
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/domain/StaffingRequest.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/domain/StaffingRequestStatus.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/application/StaffingRequestStore.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/lead/application/LeadPartnerContextLookup.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/lead/application/LeadService.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/infrastructure/StaffingRequestRecord.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/infrastructure/StaffingRequestJpaRepository.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/infrastructure/JpaStaffingRequestStore.java`
- Create: `backend/src/main/resources/db/changelog/013-staffing-requests.yaml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- Test: `backend/src/test/java/com/simbirsoft/partners/staffing/domain/StaffingRequestTest.java`
- Test: `backend/src/test/java/com/simbirsoft/partners/staffing/infrastructure/StaffingRequestPersistenceTest.java`
- Test: `backend/src/test/java/com/simbirsoft/partners/architecture/ModulePersistenceArchitectureTest.java`

**Interfaces:**
- Produces immutable request requirements, `StaffingRequestStatus.NEW`, owner/profile ID, linked lead ID, request number, version, and timestamps.
- `StaffingRequestStore`: `save(StaffingRequest)`, `findOwned(UUID requestId, UUID ownerId)`, `findOwnedPage(UUID ownerId, int page, int size)`, and `findByIdempotencyKey(UUID ownerId, UUID key)`.
- `LeadPartnerContextLookup`: `findOwnedContext(UUID leadId, UUID partnerProfileId)` returns a `LeadContext(UUID id, String companyName, String requestSummary)` projection; it must not expose a JPA entity.
- `LeadService` implements this application contract using its existing owner-scoped lookup.

- [x] **Step 1: Write failing domain and repository tests** for invalid quantity, missing required skills, flexible vs fixed desired date, language-level dependency, owner-scoped lookup, and unique `(owner_id, idempotency_key)`.
- [x] **Step 2: Run the focused backend tests** with `cd backend && ./gradlew test --tests '*StaffingRequestTest' --tests '*StaffingRequestPersistenceTest'`; confirm they fail for missing types/behavior.
- [x] **Step 3: Implement the staffing aggregate, store contracts, JPA adapter, and additive Liquibase changeset** including required fields, `@Version`, unique number and idempotency constraints, and an owner-scoped index.
- [x] **Step 4: Run the focused backend tests and module architecture tests**; require all to pass and confirm no direct `lead` repository/entity dependency.

### Task 2: API партнёра и безопасные вложения

**Files:**
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/application/StaffingRequestService.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/api/PartnerStaffingRequestController.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/api/CreateStaffingRequestRequest.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/api/StaffingRequestResponse.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/api/StaffingRequestPageResponse.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/staffing/application/StaffingRequestAttachmentPort.java`
- Create or modify: `backend/src/main/java/com/simbirsoft/partners/document/**` to store, scan, list, and download staffing request attachments under owner/request scope.
- Modify: `backend/src/main/resources/db/changelog/013-staffing-requests.yaml` if attachment metadata requires a related table.
- Test: `backend/src/test/java/com/simbirsoft/partners/staffing/application/StaffingRequestServiceTest.java`
- Test: `backend/src/test/java/com/simbirsoft/partners/staffing/api/PartnerStaffingRequestControllerTest.java`
- Test: document service tests for PDF/DOCX validation and malware-scanner failure.

**Interfaces:**
- Consumes Task 1 aggregate/store and `LeadPartnerContextLookup`; request uses a single `leadId` reference because the repository has no project entity/module.
- `POST /api/v1/partner/staffing-requests` accepts JSON and an `Idempotency-Key`; partner ID is never accepted in the payload.
- `POST /{requestId}/attachments` accepts one optional-kind attachment (`sourceBrief` or `cv`) after the request is saved, so a rejected/unavailable file cannot erase the request.
- `GET /api/v1/partner/staffing-requests?page=&size=` returns only the authenticated owner's records; `GET /{id}` and attachment downloads are likewise owner-scoped.
- The controller returns request number/status and field errors through the existing API error contract.

- [x] **Step 1: Write failing service/controller tests** for create and `NEW`, idempotent replay, non-owned lead rejection, non-owned request/detail/file denial, optional separate attachments, malformed files, and validation errors.
- [x] **Step 2: Run the focused backend tests**; confirm the missing endpoint/behavior fails as expected.
- [x] **Step 3: Implement service, DTOs and documented controller**; derive owner from `PartnerPrincipal`, delegate lead ownership to the lead application interface, and keep authorization in application logic.
- [x] **Step 4: Implement separate request-scoped attachment upload/download** by reusing the document module's scanner and object storage, without exposing lead attachment records as staffing entities; attachment failure leaves the already saved request intact.
- [x] **Step 5: Run focused API/document tests, existing module-boundary tests, and migration tests**; require owner scope and rollback behavior to pass.

### Task 3: Список заявок и форма в кабинете партнёра

**Files:**
- Create: `src/partner/StaffingRequestsPage.jsx`
- Create: `src/partner/StaffingRequestsPage.css`
- Create: `src/api/staffingRequestsApi.js`
- Modify: `src/App.jsx`
- Modify: `src/styles.css` only for shared layout hooks if needed.
- Test: add component or browser assertions under `e2e/staffing-requests.spec.js` (full end-to-end scenario is Task 5).

**Interfaces:**
- Consumes Task 2 endpoints and existing partner lead board/API for selectable owned leads.
- `StaffingRequestsPage({ text })` owns list/form state and loads its own lead options from the owner-scoped lead board; parent switches between list and create view.
- `staffingRequestsApi.js` exports `getPartnerStaffingRequests`, `getPartnerStaffingRequest`, `createPartnerStaffingRequest`; mutating requests use the existing CSRF/session pattern.

- [x] **Step 1: Write browser/component assertions** for required fields, conditional language level, flexible date, preservation of input after API error, success receipt, and empty list state.
- [x] **Step 2: Run the focused frontend assertions** and confirm they fail against the empty production section.
- [x] **Step 3: Implement list and grouped single-page form** with owned lead, role/count/grade, required and optional skills, task context, workload (1–40 hours/week), work arrangement and location, permission to work outside RF, target date/flexible-date option, conditional language/level, and explicit interview/screening/CV requirements.
- [x] **Step 4: Implement API integration** with submit progress, idempotency key, file size/type handling, field errors, network retry state, and success refresh.
- [x] **Step 5: Wire only the approved production portal section to the new component**; leave `/demo` and its mock state untouched, and verify keyboard/focus/mobile layout.
- [x] **Step 6: Run focused frontend/build checks** and require assertions and production build to pass.

### Task 4: Публикация текстов формы и готовность локалей

**Files:**
- Modify: `src/App.jsx` to require staffing form keys before rendering the section.
- Modify: `e2e/support/test-data.js` to provide deterministic synthetic values for staffing keys in RU/KZ/EN.
- Modify: existing staff registration content UI only if a specific category/visibility adjustment is necessary; preserve its publication/version checks.
- Test: frontend assertions for locale readiness and incomplete-locale behavior.

**Interfaces:**
- Reuse the current published registration-content string map and admin editor, which already supports portal section keys.
- Staffing keys use the prefix `staffing.*`; missing keys follow existing untranslated/unavailable-locale handling.

- [x] **Step 1: Write failing assertions** that the staffing route renders only with the complete published locale set and never substitutes machine-generated text.
- [x] **Step 2: Run assertions and confirm failure** with staffing keys absent.
- [x] **Step 3: Add staffing key readiness checks and synthetic locale fixtures**; do not populate production translations or alter approved legal text.
- [x] **Step 4: Run locale and registration-content regression tests** for RU, KZ, EN, and incomplete locale handling.

### Task 5: Браузерный e2e и итоговые проверки

**Files:**
- Create: `e2e/staffing-requests.spec.js`
- Modify: `e2e/support/test-data.js` and `e2e/support/fixtures.js` only for reusable setup if Task 4 did not already cover it.

**Interfaces:**
- Consumes production-built frontend, real Spring backend, PostgreSQL, existing OAuth e2e adapter, and published synthetic localization in e2e configuration only.
- Does not seed business staffing requests; the test creates its request through the UI.

- [x] **Step 1: Write the browser scenario**: approve/login a synthetic partner, create a lead, create a fully populated staffing request linked to that lead, and verify number/status/list persistence after reload.
- [x] **Step 2: Add ownership and failure assertions**: a second partner cannot enumerate/read the first partner's request; invalid optional upload does not lose form values or create an extra request.
- [x] **Step 3: Run the focused e2e test** against built frontend/backend/PostgreSQL in isolated Compose and confirm expected failures before implementation.
- [x] **Step 4: Run backend unit/integration/architecture tests, frontend build/site checks, strict OpenSpec validation, and full e2e**; fix failures before delivery.
- [x] **Step 5: Commit the verified change on `codex/staffing-request` and integrate it as a separate commit on local `main`** only after the full e2e suite passes; do not push.

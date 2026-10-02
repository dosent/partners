# Partner Public Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить русскоязычный лендинг, регистрацию с офертой по типу заявителя и админский список RBAC-пользователей.

**Architecture:** Сохранить существующее React/Vite SPA и REST API на том же origin. Backend остаётся источником опубликованных оферт, согласий и RBAC-разрешений; фронтенд отображает их и отправляет версию/контрольную сумму. Работу разделить на три вертикальных подзадачи: оферты, назначение ролей, публичный вход.

**Tech Stack:** React 19, Vite 6, Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL, Liquibase.

**Spec:** `openspec/changes/partner-public-entry/` (особенно `design.md` и capabilities `partner-public-entry`, `staff-role-administration`).

## Global Constraints

- Интерфейс этой задачи только на русском языке; переключатель RU/KZ/EN отсутствует.
- Оферты хранятся раздельно для `INDIVIDUAL`, `SOLE_PROPRIETOR` и `LEGAL_ENTITY`; согласие на обработку персональных данных общее.
- Регистрация проверяет тип, версию и SHA-256 опубликованной оферты и общего согласия.
- Партнёры входят через Yandex ID; сотрудники — через LDAP; LDAP-группы не назначают permissions.
- Операции администрирования ролей требуют `staff.access.manage`; редактирование регистрационного контента требует `localization.manage`.
- Bootstrap-пользователь `ivan.neshin` не может потерять `SYSTEM_ADMIN`.
- Оферты являются простым текстом. В этом изменении нет PDF, HTML, произвольной загрузки файлов или S3.
- Production не содержит демонстрационных бизнес-данных; неполная регистрация не становится доступной.
- Сервер является источником истины для статуса заявки, согласий, ролей и разрешений.

## Review Focus

- Тип заявителя отсутствует или не входит в offer map: backend отклоняет форму без выбора произвольной оферты.
- Опубликованная оферта выбранного типа отсутствует или неполна: форма блокирует отправку, но лендинг остаётся доступным.
- Оферта изменилась между показом формы и отправкой: старая версия/hash не принимаются для новой заявки.
- Пользователь без `staff.access.manage` вызывает список или замену ролей: backend отвечает отказом независимо от UI.
- Попытка снять `SYSTEM_ADMIN` у `ivan.neshin`: backend сохраняет существующий запрет.

---

### Task 1: Раздельные оферты и согласия регистрации

**Files:**
- Modify: `backend/src/main/java/com/simbirsoft/partners/localization/domain/RegistrationContentDraft.java` — три offer value object в `Map<String, OfferDocument>`; ключи ограничены enum внутри `localization`, без зависимости на `partner`.
- Modify: `backend/src/main/java/com/simbirsoft/partners/localization/application/RegistrationContentService.java` — валидация каждого типа, канонический SHA-256, публикация только полного набора.
- Modify: `backend/src/main/java/com/simbirsoft/partners/localization/application/PublishedRegistrationContentLookup.java` — вернуть offer map и individual hashes из lookup.
- Modify: `backend/src/main/java/com/simbirsoft/partners/localization/api/RegistrationContentDraftRequest.java`, `PublicRegistrationContentResponse.java`, `RegistrationContentAdminResponse.java` — transport shape для трёх оферт.
- Modify/Create: `backend/src/main/resources/db/changelog/006-partner-type-offers.yaml`, `db.changelog-master.yaml`, localization JPA records — forward-only миграция сохраняет старые данные, но не считает единую старую оферту опубликованной для всех типов.
- Modify: `backend/src/main/java/com/simbirsoft/partners/partner/api/PartnerApplicationSubmissionRequest.java`, `partner/application/PartnerApplicationSubmission.java`, `partner/domain/LegalConsent.java`, consent persistence — принять и закрепить `offerSha256` и `privacyNoticeSha256` вместе с версиями.
- Modify: `backend/src/main/java/com/simbirsoft/partners/partner/application/PartnerApplicationSubmissionService.java` и resubmit path — сверять `partnerType`, версии и hashes с актуальной публикацией до сохранения.
- Modify: `src/api/registrationContentAdminApi.js`, `src/admin/RegistrationContentPage.jsx` — редактировать и preview трёх текстовых оферт; оставить общее согласие; убрать выбор локали в этом редакторе.
- Modify: `src/api/partnerRegistrationApi.js`, `src/registration/PartnerRegistrationPage.jsx` — показывать оферту после выбора типа и отправлять её version/hash плюс общее consent version/hash.
- Verify: `openspec validate partner-public-entry --strict`, `./gradlew compileJava` и `npm run build`.

**Interfaces:**
- `OfferDocument(String version, String title, String body)` — value object в localization domain.
- `RegistrationContentDraft.offers` — map с ключами `INDIVIDUAL`, `SOLE_PROPRIETOR`, `LEGAL_ENTITY`.
- Public content: `offers: { [partnerType]: { version, title, body, sha256 } }`; общий `privacyNotice` содержит `version`, `title`, `body`, `sha256`.
- Submission содержит `partnerType`, `offerVersion`, `offerSha256`, `privacyNoticeVersion`, `privacyNoticeSha256` и обязательные acceptance flags.

- [ ] Добавить offer map в draft/publication DTO и storage; миграция должна сохранить исторический snapshot и не приписывать старую оферту всем типам.
- [ ] Валидировать полноту трёх оферт и считать SHA-256 отдельно для канонического текста каждой оферты и общего consent.
- [ ] Проверить опубликованные type/version/hash в submit и resubmit; сохранять оба hashes в consent snapshot без изменения `MANUAL_REVIEW`.
- [ ] Обновить админ-редактор для трёх offer type, preview, draft save и publish; убрать locale picker из регистрационного контента.
- [ ] Обновить форму: после выбора partner type показывать соответствующий текст и принимать только версию/hash из public API.
- [ ] Запустить компиляцию frontend/backend и строгую OpenSpec-валидацию.

### Task 2: Список RBAC-пользователей и назначение ролей

**Files:**
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/application/StaffRoleGrantStore.java`, `StaffAccessService.java` — чтение пользователей, которым назначена хотя бы одна роль.
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/StaffRoleGrantRepository.java`, `JpaStaffRoleGrantStore.java` — сгруппировать grants по нормализованному username без чтения LDAP.
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/api/StaffRoleAdminController.java` — `GET /api/v1/admin/staff-access/users`, ответ `{ users: [{ username, roles }] }`, permission `staff.access.manage`.
- Modify: `src/api/staffAccessApi.js`, `src/admin/StaffRoleManagementPage.jsx` — searchable table, пустое состояние, добавить логин и редактировать назначение.
- Verify: `./gradlew compileJava`, `npm run build`, `openspec validate partner-public-entry --strict`.

**Interfaces:**
- `StaffAccessService.usersWithRoles(): List<StaffRoleAssignment>`; `StaffRoleAssignment(String username, List<String> roles)`.
- API `GET /api/v1/admin/staff-access/users` returns `{ users: [{ username, roles: string[] }] }`; existing per-user GET/PUT routes remain unchanged.

- [ ] Добавить application/store method, который группирует grants и сортирует username.
- [ ] Добавить admin list endpoint с серверной проверкой `staff.access.manage`.
- [ ] Загрузить список в UI, реализовать поиск и переход к редактированию.
- [ ] Оставить ручной ввод нового логина; при сохранении показать точный логин и набор ролей для подтверждения.
- [ ] Убедиться, что аудит, allow-list ролей и защита `ivan.neshin` продолжают действовать; собрать frontend/backend.

### Task 3: Публичный лендинг и переходы пользователя

**Files:**
- Create: `src/public/PublicLandingPage.jsx`, `src/public/PublicLandingPage.css` — русскоязычный лендинг без demo метрик.
- Modify: `src/App.jsx` — routing: `/` public entry, `/registration` registration flow, `/staff` internal flow, явный `/demo` только в DEV.
- Modify: `src/registration/PartnerRegistrationPage.jsx`, `src/admin/RegistrationContentPage.jsx`, `src/App.jsx` — удалить пользовательские переключатели RU/KZ/EN и хранение выбранной локали.
- Modify: `src/styles.css` — адаптивные стили и focus/keyboard states лендинга.
- Verify: `npm run build`, `openspec validate partner-public-entry --strict`.

**Interfaces:**
- `PublicLandingPage({ onRegister, onLogin })`; registration button navigates to `/registration`, login begins `/api/v1/auth/yandex/start`.
- `PartnerPortalEntry` chooses landing for anonymous `/`, registration for `/registration`, pending application/status view for existing non-approved partner, approved portal only for `APPROVED`.

- [ ] Добавить публичный лендинг с CTA регистрации и входа, фактическими текстами без коммерческих обещаний и адаптивной компоновкой.
- [ ] Подключить root/registration routes; сохранить callback return-to-registration и staff isolation.
- [ ] Перенести прежний demo dashboard на `/demo` в DEV и убрать RU/KZ/EN control из приложенческих экранов.
- [ ] Проверить маршруты и собрать frontend.

### Task 4: Согласование интеграции

**Files:** `openspec/changes/partner-public-entry/tasks.md`; frontend/backend files above.

- [ ] Сверить каждый requirement OpenSpec с фактическим маршрутом, role boundary, API payload и пустым/ошибочным состоянием.
- [ ] Выполнить `git diff --check`, `openspec validate partner-public-entry --strict`, `npm run build` и `./gradlew compileJava`.
- [ ] Зафиксировать в OpenSpec фактический статус задач и документировать, что не было проверено.

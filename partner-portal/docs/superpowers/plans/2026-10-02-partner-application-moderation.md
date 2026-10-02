# Partner Application Moderation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Подключить staff login к FreeIPA по LDAPS, реализовать внутреннюю RBAC с bootstrap-администратором `ivan.neshin`, показать ограниченную страницу без назначенных ролей и дать администратору интерфейс выдачи ролей, а модераторам API заявок.

**Architecture:** Модуль `access` проверяет credentials через LDAPS, хранит назначенные роли в своей БД и вычисляет permissions по code-owned матрице на каждом защищённом запросе. Модуль `partner` использует только application interface access для проверки permissions и менеджерских назначений; очередь читает безопасную SQL-проекцию без реквизитов.

**Tech Stack:** Java 21, Spring Boot 3, Spring Security, JNDI LDAP, Spring Data JPA, `NamedParameterJdbcTemplate`/`QueryHelper`, PostgreSQL, Liquibase, JUnit 5, MockMvc, Testcontainers, Docker Compose.

**Spec:** `openspec/changes/partner-application-moderation/{design.md,specs/staff-access/spec.md,specs/partner-application-review/spec.md,tasks.md}`

## Global Constraints

- LDAP подтверждает личность; только внутренняя БД/permission matrix выдаёт application roles/permissions.
- Использовать `LDAP_URL=ldaps://freeipa01.simbirsoft:636/`, `LDAP_USERS_DN=cn=users,cn=accounts,dc=simbirsoft,dc=dev`, `LDAP_CA_CERT_FILE=/app/certs/simbirsoft-ca.crt`.
- Доверять только предоставленному CA; CA монтировать read-only снаружи в container path, не добавлять cert в Git/image, не включать insecure fallback.
- Bootstrap `ivan.neshin -> SYSTEM_ADMIN` действует только после успешного LDAP bind; bootstrap grant нельзя снять через API.
- Успешный LDAP-вход без app-роли создаёт ограниченную `ACCESS_PENDING` сессию, а не ошибку `403`; такой пользователь не получает бизнес- или административные permissions.
- Применять изменения ролей к последующим запросам активной сессии, читая актуальные назначения из БД при authorization.
- Роли назначаются только из enum `PARTNER_MANAGER`, `RESOURCE_MANAGER`, `FINANCE_CONTROLLER`, `SYSTEM_ADMIN`; admin API не принимает произвольные permissions.
- Все API — `/api/v1`; DTO — Java records с `@Schema`; права и owner scope проверяются сервером.
- Новые DDL — отдельными Liquibase changesets; SQL-ресурсы хранятся под `src/main/resources/sql/<module>/` и регистрируются через enum `SqlQuery`.
- Ни credentials, ни CA PEM, ни реквизиты партнёров не выводятся в логи или tracked config.
- Не создавать production/demo business records кроме единственного bootstrap RBAC grant.

## Review Focus

- LDAP valid-password bind без доверенного CA всё равно должен завершиться отказом — тест TLS trust/fail-closed в Task 2.
- LDAP-группы не могут выдать права; роль сотрудника приходит только из access БД — тест login и permission matrix в Task 2/3.
- Удаление роли должно блокировать следующий запрос в уже активной сессии — тест dynamic authorization в Task 3.
- RBAC API не должен снять bootstrap `ivan.neshin` — тест invariant в Task 1/3.
- LDAP-пользователь без роли должен попасть на экран с просьбой обратиться к администратору, а не на 403-страницу — тест login/API/UI в Task 2/4.
- Параллельные решения по заявке не должны создавать две записи истории — PostgreSQL concurrency test в Task 6.

---

### Task 1: Access RBAC storage, matrix and bootstrap administrator

**Files:**
- Create: `backend/src/main/java/com/simbirsoft/partners/access/domain/StaffRoleGrant.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/application/StaffPermission.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/application/StaffPermissionMatrix.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/application/StaffAccessService.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/application/StaffRoleLookup.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/StaffRoleGrantRecord.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/StaffRoleAuditRecord.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/StaffRoleGrantRepository.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/application/StaffRole.java`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (append changeset `002-staff-rbac` after existing `001-partner-application-model`)
- Test: access domain, permission matrix and persistence test classes under `backend/src/test/java/com/simbirsoft/partners/access/`

**Interfaces:**
- Produces access-owned `StaffRoleLookup.rolesFor(String normalizedUsername)`, `permissionsFor(String normalizedUsername)`, `hasRole(String normalizedUsername, StaffRole role)` and `hasPermission(String normalizedUsername, String permission)`; `StaffAccessService` implements this contract and owns transactional role replacement.
- Normalize usernames using trim + `Locale.ROOT` lowercase before persistence and comparisons.
- RBAC permissions: `PARTNER_MANAGER` → `partner.application.read`, `partner.application.decide`, `lead.manage`; `RESOURCE_MANAGER` → `staffing.request.manage`, `candidate.manage`; `FINANCE_CONTROLLER` → `finance.manage`; `SYSTEM_ADMIN` → `staff.access.manage`, `partner.application.read`, `partner.application.decide`, `localization.manage`, `directory.manage`.
- `replaceRoles` records each grant/revoke in append-only access audit in the same transaction; it refuses changes that remove `SYSTEM_ADMIN` from `ivan.neshin`.

- [ ] **Step 1: Add tests** `normalizesUsername`, `mapsEveryStaffRoleToExpectedPermissions`, `recordsEachGrantAndRevoke`, `cannotRemoveBootstrapAdministrator`; verify role replacement does not accept unknown roles.
- [x] **Step 2: Add role assignment/history entities and repositories** in `access.infrastructure`; keep permissions code-owned, not user-supplied or LDAP-derived.
- [x] **Step 3: Add Liquibase changeset** creating current role grants and append-only audit, then seed only `ivan.neshin` with `SYSTEM_ADMIN` and a bootstrap audit event.
- [x] **Step 4: Implement** `StaffAccessService` with transactional replace and exact audit deltas.
- [ ] **Step 5: Run access unit/persistence tests** and clean-database Liquibase startup test; expected all pass, with only the bootstrap role present in fresh DB.

### Task 2: FreeIPA LDAPS authentication and CA trust

**Files:**
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/LdapProperties.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/JndiLdapStaffAuthenticator.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/LdapConnectionFactory.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/application/StaffIdentity.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/LdapStaffAuthFlowAdapter.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/StaffAuthenticationToken.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/api/StaffSessionResponse.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/LdapCaTrustSocketFactory.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `compose.yaml`
- Modify: `.env.example` (names/placeholders only; no live internal values or certificate)
- Modify: `backend/README.md`
- Test: `backend/src/test/java/com/simbirsoft/partners/access/infrastructure/JndiLdapStaffAuthenticatorTest.java`

**Interfaces:**
- Consumes Task 1 `StaffRoleLookup.rolesFor`.
- LDAP config binds the given `LDAP_URL`, `LDAP_USERS_DN`, and `LDAP_CA_CERT_FILE`; user DN is escaped `uid=<username>,<LDAP_USERS_DN>`.
- `JndiLdapStaffAuthenticator.authenticate` checks password via LDAPS, then returns roles loaded from internal RBAC; LDAP group search/role mapping is removed. Successful bind with no roles returns an authenticated `ACCESS_PENDING` identity with an empty role set, not a 403.
- CA PEM is loaded to an SSL context used by JNDI LDAPS socket; missing/unreadable CA, invalid PEM, or certificate chain mismatch fails closed. Docker host cert path is configurable as a host-only bind source; destination is `/app/certs/simbirsoft-ca.crt:ro`.

- [ ] **Step 1: Replace group mapping tests** with `bindsToFreeIpaUserDnAndLoadsApplicationRoles`, `rejectsMissingOrUntrustedCa`, `rejectsInvalidPassword`, `createsPendingStaffSessionWithoutApplicationRoles`; include LDAP group attributes that must not affect roles.
- [x] **Step 2: Implement CA trust socket factory** loading only the configured PEM CA for LDAPS and configure JNDI to use it; reject plain `ldap://` and never downgrade TLS.
- [x] **Step 3: Update LDAP properties and Compose wiring** to use the three provided values, mount the CA read-only, remove `LDAP_GROUP_*` and `LDAP_USER_DN_PATTERN` settings, and keep `.env.example` placeholder-only.
- [x] **Step 4: Update staff login flow** to first complete LDAP bind then obtain roles from `StaffAccessService`; no role creates a restricted `ACCESS_PENDING` session with no app permissions, invalid credentials return 401, directory failures return safe 503.
- [ ] **Step 5: Run** `./gradlew test --tests '*JndiLdapStaffAuthenticatorTest' --tests '*StaffLdapLoginFlowTest'`; all auth and pending-access cases pass.

### Task 3: RBAC administration and live authorization

**Files:**
- Create: access API DTOs and `StaffAccessAdminController` under `backend/src/main/java/com/simbirsoft/partners/access/api/`
- Create: `StaffAccessRefreshFilter` under `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/SecurityConfiguration.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/api/StaffAuthController.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/api/StaffSessionResponse.java`
- Test: `StaffAccessAdminApiTest` and dynamic session authorization tests under access tests.

**Interfaces:**
- Exposes `GET /api/v1/admin/staff-access/users/{username}/roles` and `PUT /api/v1/admin/staff-access/users/{username}/roles` with request Java record containing `Set<StaffRole>` only.
- Both admin routes require current permission `staff.access.manage`; authorization manager reads current RBAC grants from Task 1 service per protected request, so active sessions do not retain revoked authorities.
- Every successfully LDAP-authenticated user receives baseline `ROLE_STAFF`; `GET /api/v1/auth/staff/session` and `POST /api/v1/auth/staff/logout` require only this baseline and return current roles plus `accessState=ACTIVE|ACCESS_PENDING`.

- [ ] **Step 1: Add API tests** `nonAdminCannotReadOrChangeRoles`, `systemAdminCanReplaceRoles`, `bootstrapGrantCannotBeRevoked`, `unknownRoleRejected`, `roleRevocationAffectsExistingSessionOnNextRequest`, `rolelessStaffSessionRemainsAuthenticatedButHasNoBusinessPermission`, `rolelessStaffCannotReachAnyBusinessRoute`.
- [x] **Step 2: Implement** role GET/PUT DTOs and controller with `@Schema`, OpenAPI docs and application-service delegation; add `POST /api/v1/auth/staff/logout` to invalidate the restricted or active session.
- [x] **Step 3: Refresh authorities dynamically** from the authenticated `StaffPrincipal.username()` and latest DB grants on every request; a missing role grant removes app permissions from the session.
- [x] **Step 4: Implement `StaffAccessRefreshFilter`** to reject role-less staff from business/admin APIs while permitting staff session and logout; the UI reuses its CSRF cookie for logout.
- [x] **Step 5: Wire route rules** for baseline `ROLE_STAFF`, protect `/api/v1/admin/staff-access/**` by `staff.access.manage`, and ensure partner identities do not pass staff management checks.
- [ ] **Step 6: Run** `./gradlew test --tests '*StaffAccessAdminApiTest'`; assert standard 401/403/error envelope and updated permissions in one existing session.

### Task 4: Staff login, pending-access and role administration UI

**Files:**
- Modify: `src/App.jsx`
- Create: `src/api/staffAccessApi.js`
- Create: `src/admin/StaffLoginPage.jsx`
- Create: `src/admin/StaffAccessPendingPage.jsx`
- Create: `src/admin/StaffRoleManagementPage.jsx`
- Modify: `src/styles.css`
- Verify: `npm run build` from `partner-portal`.

**Interfaces:**
- Login page is available at `/staff/login`, obtains CSRF token and posts credentials to `/api/v1/auth/staff/login`; `200 ACTIVE` opens the permitted staff shell, `200 ACCESS_PENDING` shows the restricted waiting page.
- Pending page calls `GET /api/v1/auth/staff/session` on the user action «Проверить доступ»; it opens the permitted shell after a role is granted.
- Pending logout posts to `/api/v1/auth/staff/logout`; only staff status/logout routes remain accessible without an app role.
- Role page is available at `/staff/access/roles` only with `staff.access.manage`, looks up a typed username using GET roles API, submits a selected `Set<StaffRole>` via PUT, and displays the updated result.

- [x] **Step 1: Implement staff API wrapper** with CSRF/session cookies and sanitized API errors.
- [x] **Step 2: Implement staff login and `ACCESS_PENDING` state screen**, keeping the session active but rendering no business/admin navigation while pending.
- [x] **Step 3: Implement role management screen** with username lookup, role selection, save/error/loading states and refreshed grants after success.
- [ ] **Step 4: Run** `npm run build`; expected successful Vite production build.

### Task 5: Moderation domain and safe persistence reads

**Files:**
- Modify: `backend/src/main/java/com/simbirsoft/partners/partner/domain/PartnerApplication.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationRecord.java`
- Modify: `backend/src/main/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationStore.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationQueries.java`
- Create: `backend/src/main/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationQueryRepository.java`
- Create: `backend/src/main/resources/sql/partner/manual-review-queue.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (append changeset `003-partner-application-moderation`)
- Test: existing partner domain and persistence tests.

**Interfaces:**
- Consumes Task 1 access-owned `StaffRoleLookup.hasRole` contract; never imports access persistence/JPA types.
- Produces `PartnerApplication.assignedManagerUsername()` and immutable status-transition method; manager assignment is valid only on `APPROVED`.
- Produces `PartnerApplicationQueryRepository.findManualReviewPage(int page, int size)` and `countManualReview()`; summary row only includes UUID/submittedAt/revision number, ordered by submittedAt then UUID, no encrypted payload.
- Reason validation after trim: 1–1000 characters, no control characters for reject/request-changes.

- [ ] **Step 1: Add domain tests** `approvesAndStoresOptionalAssignee`, `rejectsAndRequestsChangesRequireTrimmedReason`, `rejectsAssigneeOutsideApproval`, and preserve resubmission to `MANUAL_REVIEW`.
- [x] **Step 2: Add assignee column** in changeset `003-partner-application-moderation`; leave existing applications unassigned.
- [x] **Step 3: Implement SQL page/count projection** using registered enum `SqlQuery` and `QueryHelper`; enforce page ≥ 0 and size 1–100 in application/API validation.
- [ ] **Step 4: Add PostgreSQL tests** for status filter, stable ordering, paging, empty results, old-row migration and no requisites in queue DTO.
- [ ] **Step 5: Run** `./gradlew test --tests '*PartnerApplicationTest' --tests '*PartnerApplicationPersistenceTest'` from `backend`.

### Task 6: Moderation application services, API and integration

**Files:**
- Create: partner application services and API record DTOs/controllers under `backend/src/main/java/com/simbirsoft/partners/partner/{application,api}/`
- Modify: `backend/src/main/java/com/simbirsoft/partners/access/infrastructure/SecurityConfiguration.java`
- Test: `PartnerApplicationModerationApiTest`, `PartnerApplicationOwnerApiTest`, and PostgreSQL concurrency tests under partner tests.
- Modify: `openspec/changes/partner-application-moderation/tasks.md` as requirements are met.

**Interfaces:**
- Admin queue/card: `GET /api/v1/admin/partner-applications[?page=0&size=20]`, `GET /api/v1/admin/partner-applications/{id}`; permissions `partner.application.read`.
- Decisions: `POST /api/v1/admin/partner-applications/{id}/approve|reject|request-changes`; permission `partner.application.decide`; `reason` required on last two; `assignedManagerUsername` optional on approve.
- Owner status: `GET /api/v1/partner-applications/me`; partner session only, resolved by own provider/subject.
- Application transaction catches optimistic locking/stale status and emits `409 CONCURRENT_MODIFICATION`; DTOs never expose JPA entities.

- [ ] **Step 1: Add application tests** for unauthorized permission, owner lookup scope, reason validation, target manager role lookup, directory/database fail-closed behavior, status history and concurrent update.
- [x] **Step 2: Implement services** for safe queue/detail, owner status, approve/reject/request-changes; use Task 1 permission/role interface and Task 5 repository.
- [x] **Step 3: Implement controllers and DTOs** with Bean Validation, `@Schema`, `@Tag`, `@Operation`, and `@ApiResponses`.
- [ ] **Step 4: Add MockMvc tests** for 401/403/404/400, permitted role, owner cannot address another UUID, no registration payload in queue, and standard 409 error code with traceId.
- [ ] **Step 5: Add Testcontainers race test** using two independent reads/transactions; exactly one decision and one status history event commit.
- [ ] **Step 6: Run** targeted partner tests, then `./gradlew test compileJava compileTestJava`; all pass. Run `openspec validate partner-application-moderation --strict` from `partner-portal`.
- [ ] **Step 7: Mark OpenSpec tasks complete** only where implementation and verification are complete; record remaining moderation queue UI, public registration API and approval-gated cabinet access as later work.

## Scope Notes

- The UI includes staff login, restricted `ACCESS_PENDING` state and role administration for `SYSTEM_ADMIN`; moderation queue UI is excluded.
- There is no public registration submit/resubmit API in the current repository. This plan supports moderation of stored applications; connecting that queue to public submissions is a later step.
- Approval records `APPROVED` but cannot enable a cabinet API that does not yet exist. Future partner business APIs must check approval status.
- Do not create commits unless the user explicitly requests committing.

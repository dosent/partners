# План реализации ручного каталога кандидатов

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** рабочий внутренний каталог с серверным сохранением профиля, CV, ставки, доступности и внешнего ключа Jira.

**Architecture:** candidate оркестрирует профиль и его общую версию; finance хранит ставку, document — проверенные версии CV. Межмодульные вызовы выполняются через application interfaces в одной транзакции PostgreSQL; S3-компенсация сохраняет прежний CV при отказе.

**Tech Stack:** React/Vite, Java 21, Spring Boot 3, PostgreSQL, Liquibase, S3, существующий MalwareScanner, JUnit/Mockito/MockMvc/Testcontainers/ArchUnit и Playwright.

**Spec:** `openspec/changes/manual-candidate-catalog/specs/manual-candidate-catalog/spec.md`; архитектурные решения — `openspec/changes/manual-candidate-catalog/design.md`; основной трекер — `openspec/changes/manual-candidate-catalog/tasks.md`.

## Global Constraints

- Изолированная рабочая копия уже существует, ветка `codex/candidate-catalog`; согласованные правки четырёх OpenSpec-документов сохраняются.
- Роль SHALL содержать от 1 до 200 символов, описание — от 1 до 10000, навыки — от 1 до 40 непустых уникальных значений длиной до 120 символов.
- Опыт SHALL задаваться в годах и месяцах с итоговым значением от 0 до 720 месяцев. Грейд SHALL быть необязательным текстом до 80 символов.
- Ставка — положительное десятичное число не более 9999999999.99, максимум два дробных знака, валюта ISO 4217; JSON представляет сумму строкой.
- externalKey — необязательная строка до 200 символов без управляющих символов; trim, пустое значение → null; уникальность заполненных значений с учётом регистра.
- Доступность: AVAILABLE/UNAVAILABLE, явный выбор при создании. Страница по умолчанию показывает AVAILABLE, по 20 строк; размер API от 1 до 100.
- CV: обязательный при создании, PDF/DOCX до 20 МБ; проверка типа, MIME, сигнатуры и антивируса до публикации файла.
- Серверная проверка `candidate.manage`, штатная CSRF-защита; партнёрам профили, ставки и CV не выдаются.
- SQL только в `backend/src/main/resources/sql/candidate/`; никаких межмодульных repository/JPA-зависимостей и хранимых процедур.
- RU/KZ/EN — только управляемые людьми опубликованные тексты; отсутствующие переводы не выдумывать и не подменять машинным переводом.
- Никаких обращений к Jira, расчёта экономики или изменения клиентской ставки в этом этапе.
- Коммит реализации и интеграция в локальную main допускаются только после обязательного e2e; push требует отдельного запроса.

## Review Focus

1. Потеря ответа и повтор исходной операции после последующего редактирования карточки: один кандидат, сравнение с исходным fingerprint, а не с изменённой карточкой (задача 5).
2. Одновременная запись одного externalKey: один успешный результат и один 409 без частичной ставки или CV (задачи 4–6).
3. Успешная загрузка S3 и последующий rollback PostgreSQL: новый объект удаляется, старый CV остаётся доступным (задачи 3, 5).
4. Отсутствующая локаль и публикация существующих текстов: каталог не показывает ключи вместо подписей, регистрация не становится недоступной из-за новых строк (задача 7).
5. Запоздавший ответ поиска, двойное нажатие «Сохранить», конфликт двух вкладок: нет устаревшего списка, дубликата и потери введённого текста (задачи 7–8).

---

### Task 1: Профиль и валидация

**Files:** создать `backend/src/main/java/com/simbirsoft/partners/candidate/domain/CandidateProfile.java`, `CandidateAvailability.java` в том же каталоге; тест `backend/src/test/java/com/simbirsoft/partners/candidate/domain/CandidateProfileTest.java`.

**Interfaces:** `CandidateProfile(UUID id, String code, String role, String grade, List<String> skills, int experienceMonths, String description, CandidateAvailability availability, String externalKey, Instant createdAt, Instant updatedAt, String createdBy, String updatedBy, long version)`; enum AVAILABLE/UNAVAILABLE. `CandidateProfile.normalizeExternalKey(String value): String` возвращает нормализованную строку либо null; остальные поля проверяются конструктором. При обновлении ID/code/createdAt/createdBy сохраняются.

- [x] **1.1 Написать падающие domain-тесты.** Для нормализатора проверить непосредственно:

```java
assertNull(CandidateProfile.normalizeExternalKey("   "));
assertEquals("JIRA-001", CandidateProfile.normalizeExternalKey(" JIRA-001 "));
assertNotEquals(CandidateProfile.normalizeExternalKey("abc"), CandidateProfile.normalizeExternalKey("ABC"));
assertThrows(IllegalArgumentException.class, () -> CandidateProfile.normalizeExternalKey("x".repeat(201)));
assertThrows(IllegalArgumentException.class, () -> CandidateProfile.normalizeExternalKey("bad\nkey"));
```

  `acceptsZeroExperience` создаёт профиль с 0 месяцев; `rejectsOutOfRangeExperience` — с -1 и 721; `requiresExplicitAvailability` — с null. `normalizesSkills` проверяет trim, удаление пустых строк и повторов без учёта регистра, сохранение порядка; граничные длины берутся из Global Constraints.
- [x] **1.2 Проверить красный результат:** `cd backend && ./gradlew test --tests '*CandidateProfileTest'` — классы ещё отсутствуют.
- [x] **1.3 Реализовать объявленные типы**, без зависимостей domain от Spring/JPA. Ошибки отдельных полей переводит application-слой в безопасные fieldErrors.
- [x] **1.4 Проверить зелёный результат** той же командой и отметить OpenSpec 1.2.

### Task 2: Часовая ставка и её публичный интерфейс

**Files:** создать `finance/domain/CandidateHourlyRate.java`, `finance/application/CandidateRates.java`, `finance/application/CandidateRateStore.java`, `finance/application/CandidateRateService.java` внутри `backend/src/main/java/com/simbirsoft/partners/`; соответствующие domain/application тесты в `backend/src/test/java/com/simbirsoft/partners/finance/`.

**Interfaces:** `CandidateHourlyRate(UUID candidateId, BigDecimal amount, String currency, Instant changedAt, String changedBy, long version)`; `CandidateRates.set(UUID candidateId, BigDecimal amount, String currency, String actor, Instant now): CandidateHourlyRate`; `CandidateRates.findAll(Set<UUID> candidateIds): Map<UUID,CandidateHourlyRate>`. Store предоставляет `findAll(Set<UUID>)` и `save(CandidateHourlyRate)`. Только candidate вызывает запись после проверки своих прав/профиля; finance не читает candidate repository.

- [x] **2.1 Написать падающие тесты** `preservesHourlyAmount`, `rejectsInvalidHourlyRate`, `rejectsInvalidCurrency`, `recordsActorAndTime`. Прямые денежные проверки:

```java
assertEquals(new BigDecimal("2500.50"), rate.amount());
assertEquals("RUB", rate.currency());
assertEquals(actor, rate.changedBy());
assertEquals(now, rate.changedAt());
```

  В тесте invalid amount значения `0`, `-1`, `1.001`, `10000000000.00` обязаны отклоняться; `9999999999.99` принимается. Для `findAll` проверить один вызов store на набор ID.
- [x] **2.2 Проверить красный результат:** `cd backend && ./gradlew test --tests '*CandidateHourlyRateTest' --tests '*CandidateRateServiceTest'`.
- [x] **2.3 Реализовать модель и сервис** с BigDecimal без округления некорректных значений, ISO Currency и стандартной REQUIRED-транзакцией.
- [x] **2.4 Выполнить те же тесты**, отметить OpenSpec 1.3; persistence для этого интерфейса добавляется задачей 4.

### Task 3: Общая проверка вложений и версии CV

**Files:** создать `document/application/DocumentValidationService.java`, `PreparedDocument.java`, `CandidateCv.java`, `document/infrastructure/CandidateCvAdapter.java`, `CandidateCvRecord.java`, `CandidateCvJpaRepository.java`; изменить `document/application/LeadDocumentService.java` и добавить/адаптировать document-тесты.

**Interfaces:** `DocumentValidationService.prepare(String name, String mime, byte[] bytes): PreparedDocument`; record `PreparedDocument(String fileName, String contentType, byte[] bytes, String sha256)` защищает массив от изменения. `CandidateCv.store(UUID candidateId, PreparedDocument file, String actor, Instant now): Metadata`; `CandidateCv.current(UUID candidateId): Optional<Metadata>`; `CandidateCv.download(UUID candidateId): Download`. Metadata содержит ID, version, fileName, contentType, size, sha256, checkedAt; Download — fileName/contentType/bytes. Сервис candidate проверяет доступ до вызова этих методов.

- [x] **3.1 Написать падающие тесты** в `DocumentValidationServiceTest` и `CandidateCvAdapterTest`: чистый PDF, DOCX, несовпадение MIME/сигнатуры, 0 и 20 МБ+1 байт, заражение, недоступный scanner/S3. `retainsPreviousCvOnRollback` проверяет сохранение старого objectKey; `removesNewObjectOnRollback` — вызов `storage.delete(newObjectKey)` после rollback.
- [x] **3.2 Проверить красный результат:** `cd backend && ./gradlew test --tests '*DocumentValidationServiceTest' --tests '*CandidateCvAdapterTest'`.
- [x] **3.3 Выделить общую проверку из LeadDocumentService**, сохранить совместимость его `prepare`/PreparedAttachment и действующих lead/staffing adapters. CandidateCvAdapter использует существующие ObjectStorage и MalwareScanner; новый ключ S3 создаётся для каждой версии, предыдущие версии сохраняются. Зарегистрировать rollback cleanup до потенциально падающей записи метаданных.
- [x] **3.4 Прогнать новые и существующие document-тесты:** `cd backend && ./gradlew test --tests 'com.simbirsoft.partners.document.*'`; отметить OpenSpec 2.1–2.2 и проверенную часть 2.4 после задачи 5.

### Task 4: PostgreSQL, поиск и уникальность

**Files:** создать `backend/src/main/resources/db/changelog/015-manual-candidate-catalog.yaml`; добавить include в `db.changelog-master.yaml`; создать candidate/infrastructure `CandidateProfileRecord`, `CandidateProfileJpaRepository`, `JpaCandidateProfileStore`, `CandidateCreationRecord`, `CandidateCreationJpaRepository`, `JpaCandidateCreationStore`; finance/infrastructure `CandidateHourlyRateRecord`, `CandidateHourlyRateJpaRepository`, `JpaCandidateRateStore`. Создать application-порты `CandidateProfileStore.java`, `CandidateCreationStore.java`; persistence-тесты новых модулей.

**Interfaces:** `CandidateProfileStore.find(UUID id): Optional<CandidateProfile>`; `saveAndFlush(CandidateProfile): CandidateProfile`; `page(String q, CandidateAvailability availability, int page, int size): Page`, где `Page(List<CandidateProfile> items,int page,int size,long total)`. `CandidateCreationStore.reserve(String actor, UUID key, String fingerprint, UUID candidateId): Reservation`, где `Reservation(UUID candidateId,String fingerprint,boolean created)`. Ключ создания уникален на actor+key. Reservation хранит исходный fingerprint независимо от последующих изменений карточки.

- [x] **4.1 Написать PostgreSQL-тесты** `allowsMultipleEmptyExternalKeys`, `rejectsDuplicateExternalKey`, `treatsExternalKeyCaseSensitively`, `pagesWithStableOrder`, `searchesLiteralPercentAndUnderscore`, `appliesAvailableFilter`. Assertions: два null проходят; одинаковый непустой ключ отклоняется; `abc` и `ABC` различны; поиск `%`/`_` не возвращает все строки; размер страницы/total соответствуют фильтру.
- [x] **4.2 Проверить красный результат:** `cd backend && ./gradlew test --tests '*Candidate*PersistenceTest'`.
- [x] **4.3 Добавить таблицы** candidate_profile, candidate_creation, candidate_hourly_rate и candidate_cv, constraints/indexes/@Version. Сумма NUMERIC(12,2); внешний ключ nullable unique; текущая ставка одна на candidateId, CV уникален на candidateId+version. Не редактировать применённые changeset.
  Reserve использует PostgreSQL INSERT ON CONFLICT DO NOTHING и последующее чтение, без перехвата нарушения уникальности внутри уже аварийной транзакции. Если нужен этот SQL, разместить его в `resources/sql/candidate/creation.sql` и вызывать через существующий QueryHelper и enum `CandidateSql`.
- [x] **4.4 Прогнать тесты** миграции на чистой/предыдущей PostgreSQL, межмодульной транзакции, уникальности и пагинации; отметить OpenSpec 1.4 и persistence-часть 3.1.

### Task 5: Оркестрация кандидата и идемпотентность

**Files:** создать `candidate/application/CandidateService.java`, `CandidateCommand.java`, `CandidateDetails.java`, `CandidatePage.java`; тесты `candidate/application/CandidateServiceTest.java` и `candidate/infrastructure/CandidateTransactionTest.java`.

**Interfaces:** `CandidateCommand(String role,String grade,List<String> skills,int experienceMonths,String description,CandidateAvailability availability,String externalKey,BigDecimal amount,String currency)`; `CandidateDetails(CandidateProfile profile,CandidateHourlyRate hourlyRate,CandidateCv.Metadata cv)`; `CandidatePage(List<CandidateDetails> items,int page,int size,long total)`.
`CandidateService.page(StaffPrincipal actor,String q,CandidateAvailability availability,int page,int size): CandidatePage`; `get(StaffPrincipal actor,UUID id): CandidateDetails`; `create(StaffPrincipal actor,UUID key,CandidateCommand command,String fileName,String contentType,byte[] bytes): CandidateDetails`; `update(StaffPrincipal actor,UUID id,long expectedVersion,CandidateCommand command): CandidateDetails`; `replaceCv(StaffPrincipal actor,UUID id,long expectedVersion,String fileName,String contentType,byte[] bytes): CandidateDetails`; `downloadCv(StaffPrincipal actor,UUID id): CandidateCv.Download`.

- [x] **5.1 Написать unit/integration-тесты** `requiresCurrentPermission`, `createsProfileRateAndCvAtomically`, `incrementsProfileVersionForRateOrCv`, `preservesIdentityWhenClearingExternalKey`, `replaysOriginalCreationAfterLaterEdit`, `rejectsChangedPayloadWithSameKey`, `concurrentCreateProducesOneProfile`, `rollsBackRateAndCvOnKeyConflict`. Для запрещённого actor проверить отсутствие обращений к storage; для повторов — один profile и один первоначальный CV; для failure — ноль частичных записей и удаление нового объекта.
- [x] **5.2 Проверить красный результат:** `cd backend && ./gradlew test --tests '*CandidateServiceTest' --tests '*CandidateTransactionTest'`.
- [x] **5.3 Реализовать сервис** через StaffRoleLookup.hasPermission(username,"candidate.manage"), профильный store, CandidateRates и CandidateCv. Стабильный fingerprint включает нормализованную команду и sha256 CV; reserve выполняется внутри транзакции. Проверка версии и повышение общей версии обязательны для profile/rate/CV. Чтение ставок — пакетное. Для внешнего ключа ограничение БД остаётся окончательной защитой; исключение преобразуется после rollback, без попытки читать аварийную транзакцию.
- [x] **5.4 Выполнить тесты**, отметить OpenSpec 2.3–2.5 и 3.1 в подтверждённом объёме.

### Task 6: REST, ошибки и границы модулей

**Files:** создать candidate/api `CandidateAdminController.java`, `CandidateRequest.java`, `CandidateResponse.java`, `CandidatePageResponse.java`; изменить `access/infrastructure/SecurityConfiguration.java`, `shared/api/FieldValidationException.java`, `ApiExceptionHandler.java`; создать `candidate/api/CandidateAdminMockMvcTest.java`; дополнить `architecture/ModulePersistenceArchitectureTest.java` и документацию `docs/candidate-catalog.md`.

**Interfaces:** маршруты `/api/v1/admin/candidates` и методы точно по design.md. CandidateRequest содержит команду с `hourlyRate: {amount: string,currency: string}`, `externalKey` и `expectedVersion` для PUT. Ответ карточки содержит id/code/role/grade/skills/experienceMonths/description/availability/externalKey/hourlyRate/cv/createdAt/updatedAt/version. PageResponse содержит items/page/size/total. Поля CV не содержат objectKey; filename безопасно кодируется в Content-Disposition.

- [x] **6.1 Написать MockMvc-тесты** 201/200, multipart, JSON-суммы строкой, nullable externalKey, анонимного/партнёра/сотрудника без прав, CSRF, пагинации и query limits. `duplicateExternalKeyHasFieldError` ожидает статус 409 и `fieldErrors[0].field == "externalKey"`; старые FieldValidationException по-прежнему дают 400.
- [x] **6.2 Проверить красный результат:** `cd backend && ./gradlew test --tests '*CandidateAdminMockMvcTest'`.
- [x] **6.3 Добавить тонкие контроллеры и Schema/OpenAPI**, security matcher candidate.manage. У FieldValidationException добавить overload с HttpStatus, сохранив существующий default BAD_REQUEST; advice использует заданный статус и безопасные поля. Обработать MaxUploadSizeExceededException как 413. Лимит запроса допускает CV 20 МБ плюс JSON/overhead. Ставки, CV и payload не логировать.
- [x] **6.4 Выполнить API/architecture-тесты** и проверить OpenAPI-примеры; отметить OpenSpec 3.2–3.4. Документировать ручную доступность, непроверенную пока связь Jira и ограничения этапа.

### Task 7: Интерфейс и управляемые тексты

**Files:** создать `src/api/staffCandidatesApi.js`, `src/admin/StaffCandidatesPage.jsx`, `CandidateForm.jsx`, `StaffCandidatesPage.css`, `candidateContent.js`; изменить `src/App.jsx`, `src/admin/RegistrationContentPage.jsx`; добавить `tests/staff-candidates-api.test.mjs`, `tests/staff-candidates.spec.js`, `tests/candidate-forms.spec.js`, `playwright.forms.config.js` при его отсутствии.

**Interfaces:** API client экспортирует `getCandidates({q,availability,page,size})`, `getCandidate(id)`, `createCandidate({profile,cv,idempotencyKey})`, `updateCandidate(id,{profile,expectedVersion})`, `replaceCandidateCv(id,{file,expectedVersion})`, `downloadCandidateCv(id)`. HTTP-ошибки несут status/code/fieldErrors. `StaffCandidatesPage({session,onBack,onLogout})`; `CandidateForm({initialValue,text,onSave,onCancel,busy,fieldErrors})`. `candidateContent.js` экспортирует список ключей `CANDIDATE_TEXT_KEYS`; staff-страница получает опубликованные строки штатным getRegistrationContent(locale).

- [x] **7.1 Написать тесты API/form/state**: amount остаётся строкой; HTTP-совместимый requestId; один POST при двойном клике; задержанный ответ старого поиска не заменяет новый; при 409 текст формы остаётся; внешний ключ можно очистить; без опубликованных строк нет сырых ключей или самодельного перевода.
- [x] **7.2 Проверить красный результат:** `node --test tests/staff-candidates-api.test.mjs`; после сборки — `npx playwright test -c playwright.frontend.config.js tests/staff-candidates.spec.js`.
- [x] **7.3 Реализовать staff-маршрут и страницу** со всеми состояниями, постраничным поиском, карточкой/формой, отображением суммы+валюты+«за час», отметкой ручной доступности и временем изменения. Формат опыта преобразуется годы+месяцы ↔ totalMonths; пустой ввод не превращается в 0 автоматически. Формы сохраняют payload/key до известного результата создания и предлагают выбрать файл заново при необходимости.
  Редактор текстов уже поддерживает дополнительные ключи; добавить именованный каталог ключей кандидатов без превращения их в обязательные строки регистрации для всех существующих публикаций. Никаких выдуманных KZ/EN. Тестовые подписи RU/KZ/EN явно искусственные; действующая публикация регистрации продолжает работать без новых ключей.
- [x] **7.4 Проверить сборку и формы**: `npm run build`; `node --test tests/*.test.mjs`; `npx playwright test -c playwright.frontend.config.js tests/staff-candidates.spec.js`; `npx playwright test -c playwright.forms.config.js`. Проверить Chromium/WebKit и Global Constraints UI; отметить OpenSpec 4.1–4.5 только по фактическим результатам.

### Task 8: Сквозные проверки и локальная интеграция

**Files:** создать `e2e/candidate-catalog.spec.js`; дополнить `e2e/support/test-data.js` и при необходимости `e2e/support/fixtures.js`; результаты записать в `openspec/changes/manual-candidate-catalog/verification.md`. Действующие CI и e2e-support использовать без смены production аутентификации.

**Interfaces:** существующие loginAsStaff/ensurePublishedTestContent и тестовые роль/LDAP adapters; реальный Candidate API и PostgreSQL. Выделенный Compose-проект задаётся `COMPOSE_PROJECT_NAME=partner-candidate-catalog-e2e`, отдельные E2E_PORT/E2E_MCP_PORT подбираются без остановки чужих окружений.

- [x] **8.1 Добавить e2e основного пути**: Resource Manager создаёт кандидата с вымышленным PDF/ставкой, перезагружает, ищет, изменяет ставку/ключ/доступность, скачивает и заменяет CV. Проверки через интерфейс:

```javascript
await expect(page.getByLabel('Внешний ключ (Jira)')).toHaveValue('JIRA-E2E-001');
await expect(page.getByRole('alert')).toBeVisible(); // отдельный сценарий дубликата/конфликта
```

  Дополнительно проверить одного кандидата после повторной отправки, сохранение ID после очистки ключа, запрет доступа без permission, конфликт двух вкладок и запрет дублирующего ключа.
- [x] **8.2 Выполнить необходимые backend-тесты и сборку**, затем `COMPOSE_PROJECT_NAME=partner-candidate-catalog-e2e npm run test:e2e` со своим портом и реальными backend/PostgreSQL/S3 в этом проекте. Ничего не интегрировать при падении; исправлять и повторять относящиеся проверки.
- [x] **8.3 Открыть локальный предпросмотр** и проверить 1280/1440/1920 px, фактический масштаб 125%, длинные данные, загрузку, пустой список и ошибки; исправить обнаруженные дефекты. Сохранить отчёт/trace/скриншоты с искусственными данными.
- [ ] **8.4 Проверить** `openspec validate manual-candidate-catalog --strict`, `git diff --check`, полный staged diff; отразить фактические проверки в verification.md и задачи OpenSpec 5.1–5.3. После успешного e2e создать feature-коммит и отдельный интеграционный коммит в main, сохранив чужие изменения; проверить историю и статус, отметить 5.4. Push не выполнять.

## Самопроверка плана

Все 9 требований и 23 сценария спецификации распределены между задачами: доступ — 5/6/8; ручной профиль — 1/4/5/7/8; внешний ключ — 1/4/6/7/8; ставка — 2/4/5/6/7; доступность/поиск — 1/4/5/7/8; CV — 3/4/5/6/7/8; атомарность/конкуренция — 4/5/6/8; локали/UI — 7/8. Все пять Review Focus имеют проверки в соответствующих задачах. Никаких функций Jira или формул экономики в план не включено.

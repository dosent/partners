# План реализации e2e-проверок и процесса разработки через worktree

> **Для исполнителей-агентов:** ОБЯЗАТЕЛЬНЫЙ ПОДНАВЫК: `superpowers:executing-plans`. Выполняйте задачи по одной в указанном порядке. Вся реализация ведётся в существующем worktree `codex/e2e-git-workflow`. Не интегрируйте изменения в `main`, пока полный применимый набор e2e не пройдёт.

**Цель:** Сделать браузерные e2e-проверки обязательными для каждой функции, покрыть ими все текущие пользовательские потоки и закрепить работу в feature-worktree с интеграцией в `main` только после успешных e2e.

**Архитектура:** Playwright проверяет настоящий frontend, Spring Boot backend и изолированный PostgreSQL. Поддельные адаптеры Yandex ID и LDAP находятся в отдельном e2e-наборе исходников/артефакте и недоступны в production-сборке. Один Compose-стек с изолированными тестовыми данными и отчётами запускается локально и в GitLab CI.

**Технологический стек:** Playwright Test, Node.js, React/Vite, Java 21/Spring Boot 3, Gradle, PostgreSQL, Docker Compose, GitLab CI, OpenSpec.

**Спецификация:** `partner-portal/docs/superpowers/specs/2026-10-04-e2e-git-workflow-design.md`

## Общие ограничения

- В production партнёры входят через Yandex ID, сотрудники — через LDAP.
- В e2e/CI запрещено использовать реальные production-аккаунты, учётные данные, персональные данные и бизнес-записи.
- Адаптеры e2e-аутентификации существуют только в отдельном e2e-наборе исходников/артефакте и не входят в production-артефакт backend.
- В e2e остаются включены настоящие проверки авторизации приложения, application services, сохранение в БД и CSRF.
- Работа начинается от `main` в ветке `codex/e2e-git-workflow`; изменения вносятся только в `/private/tmp/partners-e2e-git-workflow`.
- Нельзя интегрировать функцию в `main`, пока обязательные e2e-сценарии не пройдут. Не отправлять изменения в remote без отдельного запроса.
- Настройки production Compose и секреты должны быть отделены от тестовой Compose-конфигурации.

## Основное внимание при проверке

- Случайное включение тестовой аутентификации в production-артефакте/профиле: production не должен активировать тестовые адаптеры входа.
- Неверный пароль сотрудника или OAuth state партнёра: вход отклоняется, сессия не создаётся.
- У сотрудника нет нужной permission: UI и API запрещают операцию.
- Решение по устаревшей или конкурентно изменённой заявке: e2e получает безопасный конфликт, а не ложный успех.
- Ошибка теста, Compose или ограничения CI-runner: сохраняются отчёт, trace и журналы; GitLab runner поддерживает изолированный Docker-in-Docker.

---

### Task 1: Добавить постоянные требования OpenSpec и правила разработки

**Файлы:**
- Создать: `partner-portal/openspec/changes/e2e-worktree-delivery/proposal.md`
- Создать: `partner-portal/openspec/changes/e2e-worktree-delivery/design.md`
- Создать: `partner-portal/openspec/changes/e2e-worktree-delivery/tasks.md`
- Создать: `partner-portal/openspec/changes/e2e-worktree-delivery/specs/e2e-delivery/spec.md`
- Создать: `partner-portal/openspec/changes/e2e-worktree-delivery/specs/platform-quality-security/spec.md`
- Изменить: `partner-portal/docs/technical-specification-partner-portal.md`, разделы 13–14
- Изменить: `AGENTS.md`
- Изменить: `partner-portal/AGENTS.md`

- [ ] **Шаг 1. Описать требования OpenSpec к e2e и интеграции**

Новая возможность `e2e-delivery` SHALL требовать браузерный e2e-сценарий для каждой новой или меняющей поведение функции: основной успешный путь и важные отказы по доступу, состоянию или интеграции. Она SHALL требовать отдельную feature-ветку/worktree от актуальной `main`; коммит интеграции в `main` разрешён только после успешного обязательного e2e-набора. Изменение SHALL описать тестовую аутентификацию, изоляцию данных, запуск CI и диагностику без production-секретов.

- [ ] **Шаг 2. Проверить OpenSpec и изучить delta**

Запустить: `openspec validate e2e-worktree-delivery --strict`
Ожидаемый результат: изменение валидно; у каждого требования есть задача реализации, бизнес-поведение не меняется.

- [ ] **Шаг 3. Согласовать инструкции репозитория и техническую спецификацию**

Обновить разделы тестирования и поставки в обоих `AGENTS.md` и разделы 13–14 технической спецификации. Зафиксировать порядок worktree → проверки → e2e → коммит в `main`, отдельность push и то, что unit/API-тесты не заменяют e2e.

- [ ] **Шаг 4. Повторно проверить документацию и OpenSpec**

Запустить: `openspec validate e2e-worktree-delivery --strict` и `git diff --check`.
Ожидаемый результат: обе проверки проходят, новые требования согласованы между OpenSpec, техспецификацией и инструкциями агентов.

### Task 2: Создать изолированный e2e-backend и Compose-окружение

**Файлы:**
- Создать: `partner-portal/backend/src/e2e/java/com/simbirsoft/partners/e2e/E2eAuthenticationConfiguration.java`
- Создать: `partner-portal/backend/src/e2e/resources/application-e2e.yml`
- Изменить: `partner-portal/backend/build.gradle`
- Изменить: `partner-portal/backend/src/test/java/com/simbirsoft/partners/access/infrastructure/JndiLdapStaffAuthenticatorTest.java`
- Удалить: `partner-portal/backend/src/test/java/com/simbirsoft/partners/access/application/LdapGroupRoleMapperTest.java`
- Создать: `partner-portal/backend/Dockerfile.e2e`
- Создать: `partner-portal/compose.e2e.yaml`
- Создать: `partner-portal/e2e/support/compose.js`

**Интерфейсы:**
- e2e-набор реализует `StaffAuthenticator` и `YandexIdentityProvider`; тестовый вход принимает `ivan.neshin` и уникальные логины `e2e-<id>` с фиксированным e2e-паролем, OAuth-код партнёра соответствует искусственному subject `e2e-partner-<id>`.
- Артефакт backend с профилем `e2e` собирается отдельно и включает классы из `src/e2e`; обычный `bootJar` их не содержит.
- Compose-проект называется `partner-portal-e2e`; БД и порты изолированы от обычного Compose-проекта разработчика.

- [ ] **Шаг 1. Добавить падающую проверку изоляции production/e2e-артефактов**

Добавить Gradle-задачу `verifyE2eArtifactIsolation`: она проверяет обычный boot-артефакт и падает, если в нём есть `E2eAuthenticationConfiguration.class`; после появления e2e-артефакта проверяет, что класс есть в нём.

- [ ] **Шаг 2. Убедиться, что проверка падает до реализации**

Запустить: `cd partner-portal/backend && ./gradlew verifyE2eArtifactIsolation`
Ожидаемый результат: FAIL, потому что задачи и e2e-артефакта ещё нет.

- [ ] **Шаг 3. Создать отдельный source set и профиль e2e**

Реализовать тестовый вход только для известных шаблонов логинов и фиксированного e2e-пароля; неверные пароли отклонять. Связать e2e `YandexIdentityProvider` с искусственными OAuth-кодами. Не менять production-адаптеры и проверки CSRF/security.

Также обновить устаревший `JndiLdapStaffAuthenticatorTest`: проверять успешный LDAPS bind и загрузку ролей через app-owned `StaffAccessService`, пустой набор ролей как `ACCESS_PENDING`, отказ неверного пароля, отключённый LDAP и запрет plaintext LDAP. Удалить устаревший `LdapGroupRoleMapperTest`, потому что LDAP-группы не являются источником RBAC.

- [ ] **Шаг 4. Добавить отдельный Compose-стек и проверку готовности сервисов**

Поднимать PostgreSQL, специальный e2e-артефакт backend и frontend. Передавать только фиктивные ключи шифрования/хеширования и параметры тестового профиля. Проверить health/readiness backend и frontend, а также убедиться, что тестовая БД пуста, кроме штатного bootstrap-администратора. Браузерный smoke-сценарий создаётся в Task 3.

- [ ] **Шаг 5. Проверить изоляцию артефактов и готовность Compose-сервисов**

Запустить: `cd partner-portal/backend && ./gradlew verifyE2eArtifactIsolation`; затем `cd partner-portal && docker compose -f compose.e2e.yaml up --build --wait`, после чего `docker compose -f compose.e2e.yaml down -v`.
Ожидаемый результат: обычный артефакт не содержит e2e-классов; e2e-стек исправен и удалён без воздействия на обычные Compose-тома.

### Task 3: Настроить Playwright и управляемый жизненный цикл тестов

**Файлы:**
- Изменить: `partner-portal/package.json`
- Изменить: `partner-portal/package-lock.json`
- Создать: `partner-portal/playwright.config.js`
- Создать: `partner-portal/e2e/global-setup.js`
- Создать: `partner-portal/e2e/global-teardown.js`
- Создать: `partner-portal/e2e/support/fixtures.js`
- Создать: `partner-portal/e2e/support/test-data.js`
- Создать: `partner-portal/e2e/smoke.spec.js`

**Интерфейсы:**
- `npm run test:e2e` запускает весь набор; `npm run test:e2e -- --grep <pattern>` — выбранный сценарий.
- `global-setup.js` поднимает изолированный Compose-проект и ждёт health; `global-teardown.js` сохраняет журналы сервисов, затем останавливает только этот проект и удаляет только его тома.
- Первоначально один worker; при ошибках сохраняются Playwright trace, скриншот и HTML-отчёт.

- [ ] **Шаг 1. Написать падающий Playwright-smoke**

В `e2e/smoke.spec.js` проверить, что главная страница показывает заголовок партнёрской программы и действия регистрации/входа.

- [ ] **Шаг 2. Запустить smoke и проверить отсутствие тестовой инфраструктуры**

Запустить: `cd partner-portal && npm run test:e2e -- --grep "public landing"`.
Ожидаемый результат: FAIL, поскольку Playwright, конфигурация и скрипты ещё не добавлены.

- [ ] **Шаг 3. Добавить Playwright и Compose-хелперы**

Добавить `@playwright/test`, команды и lifecycle-интерфейс выше. Перед остановкой Compose сохранять логи в `playwright-report/e2e-services.log`. Добавить fixture, который публикует полный искусственный RU-текст согласия и оферты через настоящий staff API с CSRF. Так тесты регистрации не зависят от спрятанных в миграциях юридических текстов.

- [ ] **Шаг 4. Повторно запустить smoke**

Запустить: `cd partner-portal && npm run test:e2e -- --grep "public landing"`.
Ожидаемый результат: PASS на собранном frontend и реальном backend/PostgreSQL.

### Task 4: Покрыть регистрацию партнёра, модерацию и одобренный кабинет

**Файлы:**
- Создать: `partner-portal/e2e/partner-registration.spec.js`
- Изменить: `partner-portal/e2e/support/fixtures.js`
- Изменить: `partner-portal/e2e/support/test-data.js`

**Интерфейсы:**
- Partner-fixture перехватывает только `https://oauth.yandex.ru/authorize`, сохраняет реальную проверку OAuth state/callback и передаёт искусственный код, который понимает только e2e-адаптер.
- Для каждой проверки генерируются уникальные искусственные email, телефон, ИНН и имя; реальные персональные данные не используются.
- Staff-адаптер принимает только `ivan.neshin` и логины `e2e-[a-z0-9-]+` с фиксированным e2e-паролем; тесты прав используют уникальные логины, чтобы назначения не утекали между сценариями.

- [ ] **Шаг 1. Добавить e2e подачи заявки и статуса ручной проверки**

Сценарий `partnerCanSubmitApplicationAndSeeManualReviewStatus`: пройти от лендинга, перехватить OAuth-перенаправление, взять фактический `state` из запроса и вернуть его в callback с искусственным e2e-кодом, отправить заявку `INDIVIDUAL` с версиями/хешами опубликованных согласий, проверить `MANUAL_REVIEW` в UI и owner-status API.

- [ ] **Шаг 2. Запустить сценарий и исследовать несовпадение с ожидаемым поведением**

Запустить: `cd partner-portal && npm run test:e2e -- --grep "manual-review status"`.
Ожидаемый результат: при падении на функциональном утверждении зафиксировать дефект текущего потока; не ослаблять тест и не добавлять обход в production ради теста.

- [ ] **Шаг 3. Реализовать OAuth-перехват только для e2e и пройти весь сценарий**

Оставить реальными проверку state в callback и сохранение заявки через registration API. Не подменять отправку или хранение заявки.

- [ ] **Шаг 4. Добавить интегрированный сценарий одобрения и входа в кабинет**

Сценарий `staffApprovalMakesPartnerPortalAvailable`: подать заявку через UI, войти как `ivan.neshin`, одобрить заявку в интерфейсе модерации, снова войти тем же искусственным Yandex ID и проверить кабинет с пустыми бизнес-разделами без демоданных.

- [ ] **Шаг 5. Добавить запрос изменений и повторную подачу**

Сценарий `partnerCanResubmitAfterRequestedChanges`: модератор запрашивает изменения, владелец видит статус/причину, меняет и повторно отправляет заявку, после чего видит новую редакцию со статусом `MANUAL_REVIEW`.

- [ ] **Шаг 6. Проверить поля трёх типов партнёров**

Через форму проверить условные обязательные поля `SOLE_PROPRIETOR` и `LEGAL_ENTITY`; основной жизненный цикл уже покрывает `INDIVIDUAL`.

- [ ] **Шаг 7. Запустить e2e партнёрских потоков**

Запустить: `cd partner-portal && npm run test:e2e -- --grep "partner"`.
Ожидаемый результат: сценарии партнёра проходят на e2e-адаптере и изолированной БД.

### Task 5: Покрыть staff-доступ, RBAC, модерацию и контент

**Файлы:**
- Создать: `partner-portal/e2e/staff-access.spec.js`
- Создать: `partner-portal/e2e/staff-moderation.spec.js`
- Создать: `partner-portal/e2e/registration-content.spec.js`
- Изменить: `partner-portal/e2e/support/fixtures.js`

- [ ] **Шаг 1. Добавить проверку `ACCESS_PENDING` и защищённых маршрутов**

Сценарий `unassignedStaffSeesPendingAndCannotOpenAdminApi`: войти с действительной e2e-учёткой без ролей портала, проверить экран ожидания назначения и `403` от API управления доступом сотрудников.

- [ ] **Шаг 2. Запустить сценарий и исследовать отказ тестовой учётки**

Запустить: `cd partner-portal && npm run test:e2e -- --grep "unassigned staff"`.
Ожидаемый результат: при проблеме с e2e-учёткой устранить только тестовую настройку; не менять авторизацию приложения.

- [ ] **Шаг 3. Проверить выдачу роли и защиту bootstrap-администратора**

Сценарий `adminCanGrantRoleButCannotRemoveBootstrapAdmin`: через UI назначить роль новому синтетическому логину, войти под ним в новом контексте браузера и проверить активность роли/permissions; отдельно проверить, что `SYSTEM_ADMIN` у bootstrap-пользователя снять нельзя.

- [ ] **Шаг 4. Проверить решение модератора и запрет без permission**

Сценарий `moderatorCanRequestChangesAndUnauthorizedStaffCannotDecide`: проверить сохранение разрешённого решения в статусе/истории и запрет решения для сотрудника без соответствующей permission.

- [ ] **Шаг 5. Проверить сохранение и публикацию контента**

Сценарий `adminCanSaveDraftAndPublishCompleteRussianContent`: изменить RU-черновик, сохранить, перезагрузить и проверить сохранённые значения; опубликовать полные тестовые тексты оферты/согласия и разрешённый ключ логотипа `simbirsoft`, затем проверить опубликованный текст через публичный API/UI. Убедиться, что публикация без текста остаётся заблокирована.

- [ ] **Шаг 6. Запустить e2e staff и локализации**

Запустить: `cd partner-portal && npm run test:e2e -- --grep "staff|admin|moderation|content"`.
Ожидаемый результат: проходят сценарии ролей, модерации и контента, включая запреты по permissions.

### Task 6: Сделать e2e обязательными в GitLab CI и описать запуск

**Файлы:**
- Создать: `.gitlab-ci.yml`
- Создать: `partner-portal/scripts/verify-e2e-ci.mjs`
- Изменить: `partner-portal/package.json`
- Изменить: `partner-portal/package-lock.json`
- Изменить: `partner-portal/README.md`
- Изменить: `partner-portal/openspec/changes/e2e-worktree-delivery/tasks.md`

- [ ] **Шаг 1. Добавить проверку обязательной CI-команды**

Добавить dev-зависимость `yaml` и `verify-e2e-ci.mjs`, который разбирает `.gitlab-ci.yml` и завершается ошибкой, если job `partner-portal-e2e` не вызывает `npm run test:e2e` либо не публикует Playwright report, trace и Compose-логи при ошибке. До появления pipeline проверка должна падать.

- [ ] **Шаг 2. Настроить GitLab CI для изолированного Compose e2e**

Запускать тесты с фиктивными CI-значениями без production LDAP/Yandex-переменных через Docker-in-Docker на runner, которому это разрешено. Job должен выполняться для изменений в ветках и `main`; сохранять отчёты после успешного и неуспешного прогона.

- [ ] **Шаг 3. Описать локальный запуск и безопасное завершение**

Документировать `npm ci`, `npx playwright install --with-deps chromium`, `npm run test:e2e`, запуск выбранного теста и расположение артефактов. Указать, что обычный `compose.yaml` не является e2e-окружением.

- [ ] **Шаг 4. Проверить CI-конфигурацию и документацию**

Запустить: `cd partner-portal && npm run verify:e2e-ci && npm run test:e2e`; при наличии выполнить GitLab CI Lint; выполнить `git diff --check`.
Ожидаемый результат: обязательный job настроен, полный набор e2e пройден, диагностические артефакты доступны.

### Task 7: Проверить результат и интегрировать его только после e2e

**Файлы:**
- Проверить: все изменённые файлы этого плана

- [ ] **Шаг 1. Запустить unit-, architecture- и integration-тесты backend**

Запустить: `cd partner-portal/backend && ./gradlew test`.
Ожидаемый результат: все существующие backend-тесты проходят.

- [ ] **Шаг 2. Собрать frontend и запустить Sites-проверки**

Запустить: `cd partner-portal && npm run build && npm run test:sites`.
Ожидаемый результат: обе команды проходят.

- [ ] **Шаг 3. Запустить весь e2e-набор на чистом Compose-проекте**

Запустить: `cd partner-portal && npm run test:e2e`.
Ожидаемый результат: все Playwright-тесты проходят; отчёт, traces при ошибках и журналы сервисов сохранены.

- [ ] **Шаг 4. Проверить staged diff и закоммитить проверенную feature-ветку**

Выполнить `git diff --check`, проверить `git diff --cached`, затем закоммитить только файлы этой функции. Зафиксировать успешный e2e-прогон в теле коммита или ссылкой на CI job.

- [ ] **Шаг 5. Интегрировать и создать коммит в `main`**

Только после успешных шагов 1–3 слить проверенную feature-ветку в локальную `main` merge-коммитом с записью об успешном e2e-прогоне. Не отправлять изменения в remote без отдельного запроса.

## Самопроверка плана

- Все требования покрыты: задача 1 закрепляет e2e и worktree-процесс; задачи 2–6 реализуют изоляцию, текущее покрытие, CI и инструкции; задача 7 задаёт порядок проверок перед коммитом в `main`.
- Отказы по авторизации/state, отсутствию permission, неполной публикации, устаревшей заявке и диагностике падения тестового стека включены в соответствующие сценарии.
- Изоляция артефактов проверяет отсутствие тестовой аутентификации в обычной production-сборке.
- В e2e остаются настоящими OAuth state, CSRF, серверные permissions, application services и сохранение в БД.

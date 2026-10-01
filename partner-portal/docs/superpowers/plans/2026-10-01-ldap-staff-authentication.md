# LDAP-аутентификация сотрудников — план реализации

> **Для исполнителя:** выполнить шаги по порядку, проверяя каждый тест циклом RED/GREEN.

**Цель:** добавить сотрудникам вход через LDAP с настраиваемым сопоставлением групп на четыре разрешённые роли; не сохранять локальные пароли.

**Архитектура:** приложение получает краткоживущие credentials и LDAP identity через application port. JNDI adapter выполняет bind и чтение групп; application mapper возвращает только разрешённые роли. API-контроллер остаётся тонким, а инфраструктурный flow adapter создаёт серверную сессию и Spring Security authentication.

**Технологии:** Java 21, Spring Boot 3, Spring Security, JNDI LDAP, JUnit 5, ArchUnit.

**Спецификация:** `openspec/changes/full-partner-portal-specification/specs/back-office-administration/spec.md`, задача 2.2 и `design.md`.

## Общие ограничения

- Роли: `PARTNER_MANAGER`, `RESOURCE_MANAGER`, `FINANCE_CONTROLLER`, `SYSTEM_ADMIN`.
- Сотрудники аутентифицируются через LDAP; пароли не сохраняются и не остаются в security context.
- LDAP URL, DN-шаблон, group-search параметры и group-to-role mapping поступают из runtime environment.
- При отсутствии разрешённой роли вход запрещён; событие отказа не содержит пароль.
- Слой `access.application` не зависит от API/infrastructure; `access.api` не зависит от infrastructure.
- Документация проекта и комментарии для сопровождения пишутся по-русски.

## Файлы и ответственность

- `access.application.StaffRole`, `StaffIdentity`, `StaffAuthenticator`, `StaffLoginService`: роли и use case входа.
- `access.application.LdapGroupRoleMapper`: безопасное сопоставление LDAP group identifiers с ролями.
- `access.infrastructure.LdapProperties`, `JndiLdapStaffAuthenticator`: runtime-настройки и JNDI bind/group lookup.
- `access.infrastructure.StaffSessionService`, `StaffAuthenticationToken`: серверная сессия и Spring Security principal.
- `access.api.StaffAuthFlow` и DTO/controller: HTTP-контракт для входа сотрудника.
- `application.yml`, `.env.example`, `backend/README.md`: пример runtime-конфигурации без рабочих credentials.
- Application, infrastructure и MockMvc тесты покрывают группы, отказ, credential handling и callback/session contract.

## Review focus

- Пустой, неверный или отключённый LDAP-конфиг не допускает вход; credentials не журналируются и не сохраняются.
- LDAP-valid пользователь без разрешённых групп получает отказ, а неизвестные группы не дают роль.
- DN и group search values escaping/шаблонизация не позволяют инъекцию LDAP-фильтра.
- Новый staff principal не получает партнёрскую роль и не может использовать API только для финансового контролёра/администратора.
- Сессия меняет идентификатор после входа; пароль отсутствует в `Authentication.credentials`.

## Задачи

### 1. Роли и application login

1. [x] Сначала написать тесты: роли из спецификации сопоставляются, неизвестные группы игнорируются, пустой результат отклоняется.
2. [x] Запустить их и зафиксировать ожидаемый compile RED до реализации недостающих типов.
3. [x] Добавить enum/identity/mapper и `StaffLoginService`, который передаёт credentials provider-у только на время аутентификации.
4. [x] Повторно запустить mapper тесты.

### 2. LDAP adapter и runtime-конфигурация

1. [x] Написать тесты на mapping group DN → role и на отказ пользователя с валидной LDAP identity без разрешённой роли.
2. [x] Подтвердить RED.
3. [x] Реализовать JNDI LDAP bind + group search с read/connect timeout, DN-шаблоном и конфигурацией ролей из environment; журналировать безопасное событие отказа.
4. [x] Добавить disabled-by-default settings в `application.yml`, `.env.example` и Docker Compose.
5. [x] Запустить unit/adapter тесты и production compilation.

### 3. Staff login endpoint и сессия

1. [x] Написать MockMvc contract-тесты: разрешённый пользователь получает серверную сессию, credentials очищаются; отказ отображается как 403.
2. [x] Подтвердить RED.
3. [x] Добавить staff login flow adapter, ротацию session ID, endpoint, role authorities и CSRF bootstrap route.
4. [x] Запустить OAuth regression, staff contract и ArchUnit тесты.
5. [x] Проверить `git diff --check`, `openspec validate --strict`, Compose config и production build; полный suite остановлен из-за недоступного Docker socket.
6. [ ] Закоммитить задачу и отправить ветку; после независимого ревью fast-forward-слить её в `main` согласно пользовательскому поручению.

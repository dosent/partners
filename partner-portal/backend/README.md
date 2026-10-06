# Backend партнёрского портала

Модульный монолит на Java 21 и Spring Boot 3. Внутри `src/main/java/com/simbirsoft/partners` каждый предметный модуль разделён на `api`, `application`, `domain` и `infrastructure`.

## Сборка

Требуется JDK 21. Gradle Wrapper закреплён в `gradle/wrapper/gradle-wrapper.properties`.

```sh
./gradlew build
```

Параметры подключения к PostgreSQL поступают только из окружения (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). Реальные значения не хранятся в исходниках или Docker-образах.

Схемой управляет Liquibase через `src/main/resources/db/changelog/db.changelog-master.yaml`; Hibernate проверяет соответствие схемы (`ddl-auto: validate`). Новые изменения схемы добавляются отдельными changeset, а уже применённые не редактируются.

Для сложных чтений и массовых операций используется проектный `QueryHelper` из `shared.persistence`. Каждый модуль регистрирует свои SQL-файлы enum, реализующим `SqlQuery`; файлы лежат в `src/main/resources/sql/<module>/`. Параметры передаются через `SqlParameterSource`. SQL и параметры, полученные от пользователя, не используются как путь к ресурсу.

Интеграционный тест `PostgreSqlStartupTest` запускает чистый PostgreSQL 16 через Testcontainers, проверяет применение Liquibase и выполнение зарегистрированного SQL-запроса с именованным параметром. Для этого теста требуется доступный Docker daemon.

## Вход партнёра через Yandex ID

OAuth-приложение регистрируется на стороне Yandex ID. Redirect URI в настройках приложения должен совпадать с `YANDEX_REDIRECT_URI`. Для локального Compose в `.env` задаются `YANDEX_ID_ENABLED=true`, `YANDEX_CLIENT_ID` и `YANDEX_CLIENT_SECRET`; пример callback URL находится в `.env.example`. Портал запрашивает только `login:info`, использует идентификатор пользователя и не сохраняет OAuth-токен после входа.

`SESSION_COOKIE_SECURE` в Compose выключен только для локального HTTP. В production требуется HTTPS и `SESSION_COOKIE_SECURE=true`. `.env` не коммитится; клиентский bundle не получает OAuth credentials.

## Вход сотрудника через LDAP и управление доступом

Backend проверяет пароль сотрудника через LDAPS; LDAP-группы не назначают роли портала. Роли и permissions
хранятся во внутренней RBAC. При первом запуске миграция назначает `SYSTEM_ADMIN` пользователю `ivan.neshin`;
для входа этому сотруднику всё равно нужно пройти LDAPS.

Для окружения SimbirSoft используйте следующие настройки в локальном `.env` или secret store:

```dotenv
LDAP_ENABLED=true
LDAP_URL=ldaps://freeipa01.simbirsoft:636/
LDAP_USERS_DN=cn=users,cn=accounts,dc=simbirsoft,dc=dev
LDAP_CA_CERT_SOURCE=/secure/path/simbirsoft-ca.crt
```

`LDAP_CA_CERT_SOURCE` — путь к сертификату на хосте; Compose монтирует его read-only как
`/app/certs/simbirsoft-ca.crt`. Сертификат не хранится в репозитории или образе. Если LDAP-вход включён,
недоступный CA или ошибка проверки TLS завершают аутентификацию с отказом, без insecure fallback.
Если CA-файл не читается при включённом LDAP, приложение завершает запуск с ошибкой конфигурации.
Пользовательский DN строится как escaped `uid=<username>,<LDAP_USERS_DN>`. Для другой среды замените URL,
пользовательский DN и путь к CA в конфигурации окружения.

Сотрудник без назначенных ролей после успешного LDAP bind получает ограниченную сессию `ACCESS_PENDING`
и страницу с просьбой обратиться к администратору портала. До назначения роли доступны проверка состояния
сессии и выход; бизнес- и административные API закрыты. Назначения выполняются через
`/api/v1/admin/staff-access/users/{username}/roles` сотрудником с permission `staff.access.manage`.
Изменения ролей применяются к уже открытой сессии на следующем запросе.

Changeset `002-staff-rbac.yaml` создаёт внутренние таблицы назначения/аудита и bootstrap grant;
`003-partner-application-moderation.yaml` добавляет поле назначенного менеджера к заявке. Оба файла
подключены из master changelog после существующей модели заявок.

## Публикация backend API как MCP-инструмента

Новый endpoint по умолчанию не попадает в MCP. Сначала реализуйте обычный partner API и application use case, включая проверку аутентификации, owner scope и состояния объектов. Для публикации пометьте только безопасный backend controller method аннотацией `@McpTool`; управление API-ключами и внутренние проверки MCP не помечаются.

```java
@McpTool(
    name = "partner_profile_get",
    description = "Get the authenticated partner profile and settings",
    behavior = McpToolBehavior.READ_ONLY)
@GetMapping("/me")
public PartnerProfileResponse current(@AuthenticationPrincipal PartnerPrincipal owner) {
    return PartnerProfileResponse.from(service.current(owner));
}
```

Имя инструмента стабильно и уникально во всём OpenAPI каталоге. Описание должно объяснять назначение и границы данных на английском простым языком. `behavior` выбирайте по эффекту вызова: `READ_ONLY` только читает, `MUTATING` изменяет состояние, `DESTRUCTIVE` удаляет или необратимо меняет данные, `IDEMPOTENT` повторяемо устанавливает одно и то же состояние. Эти значения экспортируются в `x-mcp` как подсказки клиенту и не заменяют backend авторизацию.

Проверьте аннотацию и контракт командой `./gradlew test --tests '*McpTool*' --tests '*ApiDocumentationConfigTest'`, затем откройте `/v3/api-docs` и убедитесь, что нужная операция содержит `x-mcp.enabled=true`, стабильные `name`/`description`, `behavior` и hints. Неразмеченная операция не появляется в MCP `tools/list`. Для JSON DTO generic адаптер сопоставляет OpenAPI schema с фиксированными method/path; он не принимает URL, HTTP method или поля вне схемы от вызывающего клиента.

Multipart, бинарные вложения, потоковые/длительные операции и специализированное преобразование результата требуют именованного adapter в отдельном `mcp-server`. Adapter должен сохранять backend owner checks, ограничивать размер и время, безопасно отображать ошибку и не журналировать ключ, payload или PII. После изменения `x-mcp` перезапустите/пересоздайте MCP контейнер: каталог считывается при старте. Добавление нового endpoint с обычным JSON контрактом не требует изменения кода MCP.

Multipart MCP операция использует отдельный маршрут `/api/v1/partner/leads/with-attachment`, чтобы OpenAPI сохранял отдельные JSON и multipart схемы. Прежний multipart `POST /api/v1/partner/leads` сохранён как скрытый compatibility route для уже выпущенных REST клиентов и не помечен `@McpTool`.

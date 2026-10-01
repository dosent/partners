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

## Вход сотрудника через LDAP

LDAP-вход включается только после настройки каталога: `LDAP_ENABLED=true`, `LDAP_URL` со схемой `ldaps://`, DN-шаблон `LDAP_USER_DN_PATTERN` с одним placeholder `{0}`, `LDAP_GROUP_SEARCH_BASE` и фильтр `LDAP_GROUP_SEARCH_FILTER`. Незашифрованный `ldap://` отклоняется, чтобы не передавать пароль сотрудника без TLS. Сертификат LDAPS должен доверяться JVM truststore; для корпоративного CA настройте truststore контейнера при запуске. По умолчанию фильтр ищет группы `groupOfNames` по атрибуту `member`; для другого каталога его можно заменить. LDAP соединение использует bind проверяемой учётной записью; отдельная сервисная учётка и локальное хранение паролей не используются.

Укажите полный DN группы для нужных переменных `LDAP_GROUP_PARTNER_MANAGER`, `LDAP_GROUP_RESOURCE_MANAGER`, `LDAP_GROUP_FINANCE_CONTROLLER` и `LDAP_GROUP_SYSTEM_ADMIN`. Не сопоставленная группа не даёт доступ. `/api/v1/auth/staff/login` принимает username/password только для LDAP bind, создаёт серверную HttpOnly-сессию с сопоставленными ролями и не сохраняет пароль. Перед POST клиент получает CSRF token с `GET /api/v1/auth/csrf` и передаёт cookie сессии вместе с заголовком из ответа (по умолчанию `X-XSRF-TOKEN`). Все LDAP адреса и DN должны быть заданы в локальном `.env` или secret store; не коммитьте реальные значения.

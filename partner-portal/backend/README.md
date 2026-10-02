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

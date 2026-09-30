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

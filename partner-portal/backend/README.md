# Backend партнёрского портала

Модульный монолит на Java 21 и Spring Boot 3. Внутри `src/main/java/com/simbirsoft/partners` каждый предметный модуль разделён на `api`, `application`, `domain` и `infrastructure`.

## Сборка

Требуется JDK 21. Gradle Wrapper закреплён в `gradle/wrapper/gradle-wrapper.properties`.

```sh
./gradlew build
```

Параметры подключения к PostgreSQL поступают только из окружения (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). Реальные значения не хранятся в исходниках или Docker-образах.

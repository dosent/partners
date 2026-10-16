## Purpose

Определяет безопасный и повторяемый деплой тестового стенда partner-portal на vm-neshin-test.simbirsoft через GitHub Actions и Docker Compose.

## ADDED Requirements

### Requirement: Staging разворачивается только доверенным workflow

Система SHALL разворачивать staging только workflow-ом GitHub Actions, запущенным для main или вручную для commit SHA, достижимого из main, и выполняемым на self-hosted runner в корпоративной сети. Workflow SHALL сериализовать запуски и MUST NOT разворачивать pull request или feature branch.

#### Scenario: Новый коммит в main

- **WHEN** новый коммит попадает в main
- **THEN** staging workflow SHALL передать этот commit на VM, запустить Compose и дождаться готовности сервисов

#### Scenario: Ручной повтор предыдущей версии

- **WHEN** уполномоченный оператор запускает workflow вручную с commit SHA
- **THEN** workflow SHALL развернуть именно этот SHA и SHALL сообщить результат health-проверки

#### Scenario: Ручной запуск для незащищённой ветки

- **WHEN** оператор указывает commit SHA, не достижимый из main
- **THEN** deployment job SHALL завершиться до передачи релиза на VM

#### Scenario: Запуск из pull request

- **WHEN** workflow вызывается для pull request или feature branch
- **THEN** deployment job SHALL NOT запуститься

### Requirement: Релиз и runtime-конфигурация разделены

Релиз SHALL передаваться на VM отдельным архивом исходников приложения без `.env`, `.env.*`, Git metadata, зависимостей, результатов сборки и тестовых отчётов. Runtime secrets SHALL читаться Compose с защищённого файла на VM и MUST NOT попадать в Git, архив релиза, Actions logs или артефакты.

#### Scenario: Runtime env отсутствует

- **WHEN** deploy script не находит обязательный runtime env-файл или не может его прочитать
- **THEN** script SHALL завершиться до запуска или изменения контейнеров и SHALL вывести безопасное сообщение без значений секретов

#### Scenario: SSH host key не совпадает

- **WHEN** fingerprint VM отличается от pinned known_hosts из staging Environment
- **THEN** workflow SHALL остановиться до передачи релиза

### Requirement: Стенд работает в Docker Compose с сохранением данных

Staging SHALL запускать frontend, backend, MCP, PostgreSQL, MinIO и ClamAV в Docker Compose. Backend, PostgreSQL, MCP и ClamAV SHALL быть доступны только во внутренней сети Compose; MinIO SHALL NOT быть опубликован на внешнем интерфейсе. Доступный снаружи frontend SHALL иметь настраиваемый bind address и порт. PostgreSQL и MinIO data SHALL храниться в именованных томах с фиксированным Compose project name.

#### Scenario: Успешное обновление приложения

- **WHEN** релиз успешно развёрнут
- **THEN** контейнеры SHALL пройти настроенные healthcheck-и, frontend SHALL ответить на smoke-запрос, а существующие именованные тома SHALL остаться подключёнными

#### Scenario: Неуспешный healthcheck

- **WHEN** контейнер не становится healthy или frontend smoke-запрос завершается ошибкой
- **THEN** workflow SHALL завершиться с ошибкой, SHALL показать состояние сервисов и healthcheck-ов без сырых application logs и MUST NOT удалять именованные data volumes

#### Scenario: Повторный деплой

- **WHEN** повторно запускается тот же или предыдущий commit SHA
- **THEN** Compose SHALL использовать тот же project name и SHALL повторно подключить staging data volumes

### Requirement: Staging не включает внешнюю аутентификацию без HTTPS-конфигурации

Первоначальная staging-конфигурация SHALL оставлять Yandex ID и LDAP выключенными. Внешняя аутентификация MAY быть включена только после настройки HTTPS callback URL и runtime credentials вне Git; cookie security SHALL соответствовать TLS-конфигурации.

#### Scenario: TLS и credentials не настроены

- **WHEN** стенд развёрнут без HTTPS endpoint и OAuth/LDAP credentials
- **THEN** Yandex ID и LDAP SHALL оставаться выключенными, а workflow SHALL не требовать production credentials

#### Scenario: Внешняя аутентификация включена

- **WHEN** оператор включает Yandex ID или LDAP для staging
- **THEN** callback URI и endpoint SHALL использовать HTTPS, secrets SHALL поступать только из runtime env на VM, а cookie SHALL иметь Secure-флаг

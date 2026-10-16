# Design

## Context

В partner-portal уже есть единый compose.yaml с frontend/Nginx, backend, MCP, PostgreSQL, MinIO и ClamAV. Compose использует именованные тома для PostgreSQL и MinIO и healthcheck для контейнеров. GitHub Actions workflow отсутствует; GitLab CI запускает только e2e.

Проектная техническая спецификация задаёт Kubernetes для production. Этот change относится только к отдельному staging стенду, как попросил пользователь; production-развёртывание не меняется. Пользователь подтвердил, что self-hosted runner доступен в корпоративной сети.

## Goals / Non-Goals

**Goals:** повторяемый деплой staging из GitHub Actions; запуск всех сервисов в Docker Compose на VM; проверка готовности; отдельное хранение secrets; ограничение публикации портов.

**Non-Goals:** настройка production Kubernetes, установка Docker/runner на VM, публичный TLS или настройка внешнего reverse proxy, подключение реальных учётных записей Yandex ID/LDAP.

## Decisions

1. **Runner и триггеры.** Workflow использует self-hosted Linux runner с выделенной меткой staging, имеющий сетевой доступ к VM. Он запускается при push в main и вручную для выбранного commit SHA. PR и feature-ветки не разворачиваются. Одновременные деплои сериализуются.
2. **Передача релиза.** Runner формирует архив только из partner-portal, исключая .env, другие локальные env-файлы, .git, node_modules, dist и результаты тестов. Архив отправляется по SSH в release-каталог на VM. Workflow использует приватный ключ и pinned known_hosts из GitHub Environment staging; секреты не печатаются в лог.
3. **Запуск на VM.** Релиз распаковывается в отдельный каталог с именем commit SHA. Скрипт на VM проверяет наличие runtime env-файла и запускает Compose с фиксированным project name partner-portal-staging, `up --build --detach --wait`. Внешние данные приложения хранятся только в именованных томах; деплой не выполняет `down -v` и не удаляет их. Старые каталоги релизов удаляются только после успешной проверки и с ограничением числа сохраняемых релизов.
4. **Runtime-конфигурация.** Файл `~/.config/partner-portal/staging.env` создаётся администратором на VM с правами 0600. В GitHub Environment хранятся только данные SSH-доступа. Файл не отправляется с runner и не копируется из архива.
5. **Сетевые порты.** На хост публикуется только frontend, на настраиваемом bind address и порту. MCP и MinIO привязываются к loopback либо остаются только во внутренней Compose-сети; backend, PostgreSQL и ClamAV не получают внешних host ports. Bind address позволяет подключить существующий reverse proxy без изменения Docker-сети.
6. **Готовность и ошибки.** Compose должен дождаться healthcheck-ов контейнеров; затем workflow выполняет smoke-запрос к frontend на VM. При ошибке workflow выводит только состояние сервисов/healthcheck-и и завершается; сырые application logs в Actions не публикуются. Запуск с предыдущим SHA остаётся способом повторить известную версию; данные томов сохраняются.
7. **TLS и внешняя аутентификация.** TLS завершает существующий корпоративный reverse proxy или ingress, который вне рамок этого change. Первичная конфигурация оставляет Yandex ID и LDAP выключенными. Их можно включить только после настройки HTTPS, callback URI и runtime credentials; `SESSION_COOKIE_SECURE` должен соответствовать HTTPS-контуру.

## Risks / Trade-offs

- Если у runner нет SSH-доступа или на VM не установлен Docker Compose v2, workflow не дойдёт до запуска; setup-документ перечисляет эти предусловия.
- Неверный bind address может сделать frontend недоступным из reverse proxy либо открыть его напрямую; переменная явно документируется, а MCP/MinIO по умолчанию не должны быть доступны извне.
- Обновление одного Compose project может временно прервать staging; сложный blue/green rollout не нужен для тестового стенда.
- Если `.env` отсутствует или неполон, деплой остановится до `compose up`; скрипт не генерирует фиктивные runtime credentials.

## Migration Plan

1. Настроить GitHub Environment secrets, SSH known_hosts и runtime env на VM.
2. Добавить staging-настройки Compose, remote deploy script и GitHub Actions workflow.
3. Слияние workflow в main запускает первый автодеплой; проверить healthchecks и URL frontend.
4. При необходимости повторить известный commit SHA через ручной запуск workflow.

Откат выполняется повторным ручным деплоем предыдущего commit SHA. Именованные данные PostgreSQL и MinIO при этом сохраняются.

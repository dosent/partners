# Проверка ручного staging-деплоя — 2026-10-16

## Результат

Ручной деплой staging выполнен по SSH пользователем `ineshin` на `vm-neshin-test.simbirsoft`. Скрипт `deploy.sh` завершился с кодом `0` и сообщил: `Staging release ea565128ad5ad536bb4195bf404cbe6888173ec8 is ready`.

Развёрнут исходный коммит `ea565128ad5ad536bb4195bf404cbe6888173ec8`. Production Dockerfiles были собраны непосредственно на целевой VM. Каталог релизов на VM: `/opt/partner-portal/releases`.

## Проверки после запуска

- Все шесть сервисов Compose в staging имеют состояние `healthy`: `frontend`, `backend`, `mcp`, `postgres`, `object-storage` и `clamav`.
- Внешний smoke-test: `GET http://vm-neshin-test.simbirsoft:18088/` вернул HTTP `200`.
- Публикация портов: frontend доступен на `0.0.0.0:18088`; MCP — только на `127.0.0.1:18081`; MinIO — только на `127.0.0.1:19010` и `127.0.0.1:19011`.
- Для backend, PostgreSQL и ClamAV host-порты не опубликованы.
- Созданы отдельные именованные volumes `partner-portal-staging_postgres-data` и `partner-portal-staging_object-storage-data` (2026-10-16 09:48:09 UTC).
- Существующий диагностический Compose-проект `par-1-diagnostic` на порту `8088` остался здоровым и без изменений.

## Конфигурация и границы проверки

На VM создан runtime env-файл `~/.config/partner-portal/staging.env` с правами `0600` и случайными учётными данными. Значения секретов в этот документ не включены. Временные файлы передачи после деплоя удалены.

LDAP и Yandex ID оставлены выключенными; TLS не настраивался и не входил в объём этой проверки.

Деплой выполнен вручную. Push в GitHub, запуск GitHub Actions workflow и наличие/доступность credentials GitHub Environment не выполнялись и не проверялись. Поэтому этот результат подтверждает ручной запуск и smoke-проверку на VM, но не является проверкой автоматического pipeline.

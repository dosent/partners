# Tasks

## 1. Compose и конфигурация staging

- [x] 1.1 Добавить настраиваемую привязку host ports так, чтобы staging публиковал только frontend, а MCP/MinIO были ограничены loopback или Compose network.
- [x] 1.2 Добавить пример staging env и инструкцию создать защищённый runtime env-файл на VM без секретов в Git.

## 2. Доставка и запуск релиза

- [x] 2.1 Добавить скрипт передачи и запуска immutable release по commit SHA, не удаляющий data volumes.
- [x] 2.2 Добавить GitHub Actions workflow для main и ручного SHA с self-hosted runner, pinned SSH host key, сериализацией deploy и smoke-проверкой.
- [x] 2.3 Документировать runner labels, GitHub Environment variables/secrets, Docker Compose предусловия и TLS/auth ограничения staging.

## 3. Проверка

- [x] 3.1 Проверить OpenSpec, конфигурацию Compose и shell/workflow статические проверки.
- [ ] 3.2 Выполнить ручной staging-деплой и зафиксировать healthcheck и smoke-test evidence после настройки доступа к VM.

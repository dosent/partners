# Дизайн Docker-деплоя staging на VM

## Цель

Настроить повторяемый деплой тестового partner-portal на vm-neshin-test.simbirsoft. Пользователь подтвердил Docker Compose и наличие self-hosted runner в корпоративной сети. Решение применяется только к staging; production остаётся на Kubernetes.

## Решение

GitHub Actions разворачивает коммиты main автоматически и позволяет вручную выбрать commit SHA для повторного деплоя. Job запускается на self-hosted Linux runner с меткой staging, архивирует только исходники partner-portal и отправляет их по SSH на VM. Для SSH используются секретный ключ и pinned known_hosts из GitHub Environment staging.

На VM каждый SHA распаковывается в отдельный release-каталог. Удалённый deploy script проверяет runtime env-файл, выполняет Docker Compose build/up с постоянным project name и ожидает healthcheck-и. Frontend публикуется на настраиваемом адресе/порту; backend, PostgreSQL и ClamAV остаются внутри Compose network, MCP и MinIO не доступны извне. Именованные тома PostgreSQL и MinIO не удаляются при релизе или ошибке.

Файл runtime env хранится в `~/.config/partner-portal/staging.env` с правами 0600. Yandex ID и LDAP остаются выключенными до настройки HTTPS и тестовых credentials. Корпоративный TLS/reverse proxy, установка Docker и runner, а также production deployment не входят в изменение.

Полная спецификация и требования: `partner-portal/openspec/changes/staging-docker-deployment/`.

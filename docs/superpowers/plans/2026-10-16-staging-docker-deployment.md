# Staging Docker Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Настроить повторяемый Docker Compose деплой `partner-portal` на `vm-neshin-test.simbirsoft` из GitHub Actions.

**Architecture:** Self-hosted runner с меткой `staging` передаёт архив исходников по SSH на VM и вызывает скрипт релиза. На VM каждый commit SHA распаковывается в отдельный каталог и запускается отдельным фиксированным Compose project с runtime env-файлом вне Git; именованные тома данных сохраняются между релизами.

**Tech Stack:** GitHub Actions, self-hosted Linux runner, OpenSSH, Bash, Docker Compose v2, Node.js test runner, OpenSpec.

**Spec:** `partner-portal/openspec/changes/staging-docker-deployment/specs/staging-deployment/spec.md`; дизайн: `partner-portal/openspec/changes/staging-docker-deployment/design.md`.

## Global Constraints

- Развёртывание относится только к staging; production Kubernetes не изменяется.
- Секреты передаются только во время выполнения из `~/.config/partner-portal/staging.env` на VM или GitHub Environment secrets; секреты не попадают в Git, образ, build args или логи.
- Workflow запускается при push в `main` и вручную для commit SHA, достижимого из `main`; параллельные релизы сериализуются.
- Используются SSH host keys, заранее закреплённые в GitHub Environment; автоматическое доверие неизвестному ключу запрещено.
- Compose project фиксирован как `partner-portal-staging`; деплой не выполняет `down -v` и не удаляет именованные тома PostgreSQL и MinIO.
- На хосте доступен только frontend; MCP и MinIO ограничены loopback/внутренней сетью, остальные сервисы не публикуют host ports.
- Первичная конфигурация оставляет Yandex ID и LDAP выключенными; TLS/reverse proxy и VM/runner provisioning находятся вне объёма.
- При ошибке workflow сообщает безопасное состояние healthcheck без вывода application logs.

## Review Focus

- Неполный или отсутствующий runtime env-файл должен остановить релиз до `compose up`; это проверяет тест deploy-скрипта.
- Невалидный или недостижимый из `main` SHA должен быть отклонён; это проверяют workflow-условие и статическая проверка конфигурации.
- Неожиданные SSH host keys не должны приниматься; это проверяется статически по SSH аргументам workflow.
- Неуспешный healthcheck/smoke test должен завершать деплой ошибкой; предыдущий SHA остаётся доступным для ручного отката, а именованные volumes сохраняются. Это покрывается контрактной проверкой deploy-скрипта и smoke-шагом workflow.
- Очистка старых релизов не должна затрагивать текущий релиз или Compose volumes; это проверяется тестом скрипта на временных каталогах.

---

### Task 1: Безопасная конфигурация Compose для staging

**Files:**
- Modify: `partner-portal/compose.yaml`
- Create: `partner-portal/.env.staging.example`
- Create: `partner-portal/tests/staging-deploy-config.test.mjs`
- Modify: `partner-portal/package.json`

**Interfaces:**
- Produces: Compose overrides через env-переменные для frontend bind address/port и loopback bind для MCP/MinIO; тестовый контракт использует существующий `yaml` dependency и Node `node:test`.

- [ ] Добавить тесты `frontend_is_the_only_public_service`, `mcp_and_minio_bind_to_loopback`, `stateful_volumes_are_named_and_stable`, `auth_providers_are_disabled_by_staging_defaults`; тесты проверяют разобранную YAML конфигурацию и staging env defaults.
- [ ] Запустить `npm run test:staging-deploy` из `partner-portal`; подтвердить, что новые проверки обнаруживают текущие открытые MCP/MinIO порты.
- [ ] Изменить `compose.yaml`: параметризовать frontend host binding; MCP и MinIO привязать к `127.0.0.1` по умолчанию; сохранить существующие имена volumes и внутренние healthchecks.
- [ ] Добавить `.env.staging.example` с безопасными placeholder/default значениями и выключенными LDAP/Yandex ID; документировать, что файл на VM создаётся отдельно и не коммитится.
- [ ] Повторить `npm run test:staging-deploy`; ожидать PASS для перечисленных контрактов.
- [ ] Выполнить `docker compose --env-file .env.staging.example config --quiet` в `partner-portal`; ожидать код завершения 0.

### Task 2: Идемпотентный запуск релиза на VM

**Files:**
- Create: `partner-portal/deploy/staging/deploy.sh`
- Create: `partner-portal/deploy/staging/README.md`
- Create: `partner-portal/tests/staging-deploy-script.test.mjs`
- Modify: `partner-portal/package.json`

**Interfaces:**
- Consumes: release archive с `compose.yaml`, переданный SHA, runtime env-файл VM `~/.config/partner-portal/staging.env`.
- Produces: `deploy.sh <sha> <archive>`; успешный запуск активирует каталог `/opt/partner-portal/releases/<sha>` через фиксированный Compose project `partner-portal-staging`, ожидает readiness и удаляет только старые каталоги releases после успеха.

- [ ] Добавить тесты `rejects_invalid_sha`, `requires_runtime_env_before_compose`, `keeps_data_volumes`, `prunes_old_releases_only_after_success`, используя временный каталог и подменённый `docker` executable в PATH.
- [ ] Запустить `npm run test:staging-deploy-script`; подтвердить ожидаемые падения проверок на ещё отсутствующем скрипте.
- [ ] Реализовать deploy script с проверкой SHA/archive/env permissions, распаковкой в SHA-каталог, `docker compose ... up --build --detach --wait` и проверкой frontend smoke URL; не выводить env или application logs.
- [ ] После успешного deploy оставить текущий и ограниченное число предыдущих release-каталогов; volumes не чистить. При неуспехе вывести только безопасный `docker compose ps` health status и вернуть ненулевой код.
- [ ] Документировать VM prerequisites, создание env-файла с режимом `0600`, каталог релизов, порт и требования к ручной настройке reverse proxy/TLS.
- [ ] Повторить `npm run test:staging-deploy-script`; ожидать PASS, включая защиту volumes и отказ до запуска при отсутствующем env.

### Task 3: GitHub Actions доставка и документация

**Files:**
- Create: `.github/workflows/deploy-staging.yml`
- Modify: `partner-portal/deploy/staging/README.md`
- Modify: `partner-portal/README.md`
- Modify: `partner-portal/tests/staging-deploy-config.test.mjs`

**Interfaces:**
- Consumes: deploy interface `deploy.sh <sha> <archive>` и GitHub Environment `staging` с SSH private key и pinned known_hosts.
- Produces: workflow, запускаемый на push в `main` и вручную с SHA; GitHub Actions использует runner labels `self-hosted`, `linux`, `staging`, ограниченный concurrency group и environment `staging`.

- [ ] Дополнить config tests проверками триггеров `main`/manual SHA, runner labels, serialization, SSH strict host checking, отсутствия PR deploy и отсутствия команд публикации секретов/logs.
- [ ] Запустить `npm run test:staging-deploy`; подтвердить, что тесты выявляют отсутствие workflow.
- [ ] Реализовать workflow: проверить, что выбранный SHA достижим из `origin/main`, сформировать архив только из `partner-portal` без `.env*` (кроме tracked example), `.git`, `node_modules`, `dist` и тестовых артефактов, передать архив и вызвать remote deploy по SSH.
- [ ] Использовать GitHub Environment `staging`: `STAGING_HOST`, `STAGING_USER` как variables; `STAGING_SSH_PRIVATE_KEY`, `STAGING_KNOWN_HOSTS` как secrets. Маскировать ключевой материал и не включать его в shell tracing.
- [ ] Добавить smoke-проверку frontend и при сбое выводить только Compose service/health статусы; предусмотреть очистку переданного временного архива.
- [ ] Документировать runner labels, Environment settings, секреты, предусловия Docker Compose v2, первичную ручную настройку VM и ограничения TLS/auth.
- [ ] Запустить `npm run test:staging-deploy`; ожидать PASS.
- [ ] Если доступна команда `actionlint`, запустить `actionlint .github/workflows/deploy-staging.yml`; иначе отметить статическую проверку workflow как не выполненную и использовать config tests плюс проверку GitHub workflow parser в CI.

### Task 4: Сквозная проверка и обновление OpenSpec

**Files:**
- Modify: `partner-portal/openspec/changes/staging-docker-deployment/tasks.md`
- Modify if required: files from Tasks 1–3

**Interfaces:**
- Consumes: готовые Compose, deploy script и workflow.
- Produces: checked OpenSpec tasks и evidence доступных автоматических проверок; ручной staging deploy остаётся ожидающим предоставления доступа к VM.

- [ ] Запустить `npm run test:staging-deploy`, `npm run test:e2e` и `openspec validate staging-docker-deployment --strict` из каталога `partner-portal`; сохранить фактический результат и исправить регрессии до повторного прогона.
- [ ] Запустить `docker compose --env-file .env.staging.example config --quiet`; затем, если Docker доступен, выполнить локальный запуск stack без production credentials и проверить ожидаемые host bindings.
- [ ] Проверить итоговый `git diff --check`, отсутствие секретов и staged diff перед финальными коммитами.
- [ ] Обновить OpenSpec tasks: отметить завершённые локальные пункты; ручной деплой и healthcheck на VM оставить открытым до восстановления SSH-доступа.
- [ ] После доступного локального прогона запросить отдельную проверку reviewer всей ветки; не заявлять о live-деплое до успешного SSH-доступа и smoke-проверки на стенде.
- [ ] После успешных применимых проверок и обязательного браузерного e2e просмотреть staged diff и создать один feature commit с изменениями деплоя; интеграция в `main` и push выполняются отдельно по правилам проекта и явному запросу на push.

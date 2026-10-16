# Docker Compose staging на VM

Стенд `vm-neshin-test.simbirsoft` использует отдельный Docker Compose project
`partner-portal-staging`. Именованные тома
`partner-portal-staging_postgres-data` и
`partner-portal-staging_object-storage-data` сохраняются при обновлении и откате.

## Подготовка VM

Нужны Linux, Bash, Python 3, curl, Docker Engine и Docker Compose v2 с поддержкой
`up --wait` и `ps --format`. Пользователь деплоя должен иметь доступ к Docker,
возможность собирать образы и скачивать базовые образы из реестров.
Администратор заранее создаёт каталог релизов и передаёт его пользователю деплоя:

```bash
sudo install -d -o <deploy-user> -g <deploy-group> -m 0750 /opt/partner-portal/releases
```

Runtime-конфигурация создаётся **на VM** под тем же пользователем, который запускает
деплой. Значения берутся из `.env.staging.example`, но credentials заменяются
отдельными staging-секретами через защищённый редактор или secret store:

```bash
install -d -m 0700 ~/.config/partner-portal
(umask 077; touch ~/.config/partner-portal/staging.env)
chmod 0600 ~/.config/partner-portal/staging.env
${EDITOR:-vi} ~/.config/partner-portal/staging.env
```

Обязательны `DB_PASSWORD`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`; остальные параметры
задаются по примеру и требованиям используемых функций. Для регистрации нужны
собственные ключи `REGISTRATION_ENCRYPTION_KEY` и `REGISTRATION_HASH_KEY`.
Файл должен быть обычным файлом без symlink, принадлежать пользователю деплоя
и иметь ровно `0600`. Не помещайте runtime env в архив, Git, аргументы команд
или журналы. Неполную Compose-конфигурацию скрипт отклоняет до изменения контейнеров.

## Запуск и повтор релиза

Архив содержит исходники `partner-portal` с `compose.yaml` **в корне**.
Пример входного интерфейса (SHA — полный 40-символьный commit SHA в нижнем регистре):

```bash
bash deploy/staging/deploy.sh <sha> /tmp/partner-portal-release.tar.gz
```

Передача архива, pinned SSH host keys и проверка достижимости SHA из `main`
выполняются доверенным workflow. Скрипт проверяет формат SHA, env и архив,
распаковывает новый SHA в `/opt/partner-portal/releases/<sha>` и запускает:

```text
docker compose --project-name partner-portal-staging --env-file ~/.config/partner-portal/staging.env --file /opt/partner-portal/releases/<sha>/compose.yaml up --build --detach --wait
```

Затем скрипт определяет опубликованный порт frontend через Compose и проверяет
HTTP `/` с ограниченным ожиданием. Build output и application logs не печатаются;
при сбое readiness/smoke выводятся только service/state/health из `compose ps`.
Код завершения ненулевой. Неуспешный запуск может уже изменить контейнеры;
автоматического отката нет. Повторите известный предыдущий SHA для ручного отката.
Архив существующего SHA не распаковывается повторно: каталог считается неизменяемым.

После успеха остаются текущий SHA и до трёх предыдущих успешных релизов. Отметки
успешной активации хранятся отдельно в `releases/.successful-releases/`; время
распаковки неуспешных попыток не влияет на сохранение версий для отката. Каталоги
неуспешных попыток удаляются при следующем успешном запуске. При ошибке каталоги
не очищаются. Чужие каталоги, symlink и
Docker volumes очистка не затрагивает. Не выполняйте `down -v`, `volume prune`
или ручное удаление этих томов при деплое. Скрипт не удаляет входной архив:
за его уборку отвечает вызывающий workflow.

Одновременные вызовы защищены каталогом `.deploy-lock` в releases. Если процесс
был принудительно завершён через SIGKILL или VM перезагрузилась, оператор должен
проверить отсутствие активного деплоя и удалить оставшийся пустой lock-каталог.
`STAGING_RELEASES_DIR` позволяет явно выбрать другой абсолютный каталог релизов
(в том числе для локальных контрактных тестов); project name и runtime env остаются
фиксированными. Не меняйте каталог у работающего стенда без переноса release-файлов.

## Порт, TLS и аутентификация

По умолчанию frontend доступен на `0.0.0.0:8088` через `PORTAL_BIND_ADDRESS` и
`PORTAL_PORT`. Для reverse proxy на этой же VM задайте `PORTAL_BIND_ADDRESS=127.0.0.1`.
Доступ к порту снаружи согласуйте с сетевыми правилами стенда. Backend, PostgreSQL и
ClamAV находятся во внутренней сети Compose; MCP и MinIO публикуются только на
`127.0.0.1`. Наличие frontend smoke-ответа не проверяет корпоративную маршрутизацию.

Установка корпоративного reverse proxy и настройка сертификата/TLS выполняются
администратором вручную. Первоначально оставьте `YANDEX_ID_ENABLED=false` и
`LDAP_ENABLED=false`. Включайте их после настройки HTTPS endpoint, callback URI,
тестовых credentials и CA для LDAPS; для HTTPS задайте `SESSION_COOKIE_SECURE=true`.
Для `LDAP_CA_CERT_SOURCE` используйте абсолютный путь на VM, чтобы смена SHA
не меняла источник сертификата. Production использует отдельную инфраструктуру.

## GitHub Actions и Environment `staging`

Workflow находится в `.github/workflows/deploy-staging.yml` от корня репозитория.
Он запускается при push в `main` или через **Run workflow** с выбранной веткой
`main` и обязательным полным 40-символьным lowercase commit SHA. SHA должен быть
достижим из `origin/main`; feature commits и PR не допускаются. Для ручного отката
укажите прежний успешный SHA, который уже содержал `deploy/staging/deploy.sh` и
staging Compose-конфигурацию. Данные и миграции базы должны быть совместимы с ним.

Создайте GitHub Environment **`staging`** и ограничьте его deployment branches
веткой `main`. Настройте следующие значения в этом Environment:

| Тип | Имя | Содержимое |
| --- | --- | --- |
| Variable | `STAGING_HOST` | `vm-neshin-test.simbirsoft` или согласованный DNS hostname/IPv4 без схемы и порта |
| Variable | `STAGING_USER` | Linux-пользователь деплоя с SSH-доступом и доступом к Docker |
| Secret | `STAGING_SSH_PRIVATE_KEY` | Отдельный SSH private key для staging без passphrase, переданный через защищённый интерфейс GitHub |
| Secret | `STAGING_KNOWN_HOSTS` | Готовые записи known_hosts с ключом VM, проверенным администратором по доверенному каналу |

Публичную часть deployment key администратор добавляет в `authorized_keys`
пользователя VM. Workflow использует стандартный SSH port 22. Host key должен
соответствовать ровно имени из `STAGING_HOST`; hashed known_hosts допустим.
Не получайте ключ автоматически во время deploy. Смена host key требует проверки
и явного обновления Environment secret. Несовпадение ключа останавливает SSH/scp;
используются `StrictHostKeyChecking=yes`, отдельный `UserKnownHostsFile`,
`GlobalKnownHostsFile=/dev/null` и `IdentitiesOnly=yes`.

Нужен доверенный self-hosted Linux runner со всеми метками **`self-hosted`,
`linux`, `staging`**, доступом к GitHub и SSH VM из корпоративной сети. На runner
нужны Bash, Git, Python 3 и OpenSSH (`ssh`, `scp`); Docker и Node.js для самого
workflow не требуются: образы собираются на VM. Настройка runner, Docker Engine,
Compose v2 и VM выполняется администратором вручную до первого деплоя. Runner
нельзя предоставлять недоверенным PR jobs; ограничьте доступ к его группе.

Workflow читает всю историю `main`, проверяет SHA, затем архивирует только
committed subtree `partner-portal` выбранного SHA. Локальные файлы runner не
попадают в релиз. `.env*` удаляются, кроме tracked `.env.example` и
`.env.*.example`; `.git`, `node_modules`, `dist`, `build`, `.gradle`, coverage,
Playwright reports и прочие перечисленные в workflow тестовые артефакты удаляются
на любой глубине. `compose.yaml` находится в корне архива. Bootstrap `deploy.sh`
тоже берётся из выбранного SHA. Runtime env остаётся только на VM.

Группа concurrency `partner-portal-staging` сериализует workflow;
`cancel-in-progress: false` сохраняет активный деплой. GitHub может заменить уже
ожидающий запуск более новым; это не очередь всех commit-ов. Timeout job — 45
минут. SSH key и known_hosts записываются с режимом `0600` во временный каталог
runner, shell tracing отключён, ключ маскируется. Trap удаляет credentials и
временный transfer-каталог VM при обычном завершении, ошибка передачи тоже
запускает cleanup. Финальный `always()` шаг удаляет локальный архив. Удаление
каталога VM также выполняется внутри SSH-сессии после завершения deploy script.
При потере связи или принудительном завершении runner оператор проверяет и
удаляет оставшиеся `/tmp/partner-portal-deploy.*` после исключения активного деплоя;
workflow сообщает о неудачной попытке cleanup.

Readiness и frontend smoke выполняет переданный `deploy.sh`: это тот же интерфейс,
что при ручном запуске. Неуспех возвращает ошибку Actions job с безопасными
service/state/health статусами. Workflow не публикует application logs, runtime
config, SSH credentials или deployment archive как artifacts. Smoke выполняется
с VM и не проверяет доступ к frontend из пользовательской сети. TLS, reverse
proxy, firewall и включение Yandex ID/LDAP остаются ручными предусловиями,
описанными выше.

## Проверка конфигурации перед публикацией

```bash
# Из partner-portal:
npm run test:staging-deploy
npm run test:staging-deploy-script
# Из корня репозитория, если actionlint установлен:
actionlint .github/workflows/deploy-staging.yml
```

Config tests разбирают YAML, проверяют ограничения workflow, выполняют shell
проверки SHA/архива на временном Git-репозитории и проверяют уборку credentials
при ошибке SSH. Shell шаги проверяются через `bash -n`. Если `actionlint`
недоступен, эта статическая проверка остаётся невыполненной; GitHub проверит
workflow parser при публикации в репозиторий. Успех локальных проверок не
подтверждает GitHub Environment, runner, SSH-доступ или реальный деплой на VM.

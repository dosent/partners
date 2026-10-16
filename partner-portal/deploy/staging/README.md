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

После успеха остаются текущий SHA и три предыдущих каталога по времени последнего
успешного запуска. При ошибке каталоги не очищаются. Чужие каталоги, symlink и
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

# Деплой partner-portal на staging VM через Docker Compose

## Problem

В репозитории есть Docker Compose для локального запуска и GitLab CI для e2e, но нет процесса доставки приложения на тестовый стенд после переноса репозитория в GitHub. Ручной запуск Compose на VM не даёт повторяемого и проверяемого обновления.

## Outcome

Добавить GitHub Actions workflow, который разворачивает приложение на vm-neshin-test.simbirsoft через SSH из self-hosted runner в корпоративной сети. Деплой запускает весь существующий стек Docker Compose, проверяет готовность контейнеров и доступность frontend.

## Scope

- Автодеплой коммитов main и ручной запуск выбранного коммита.
- Передача исходников на VM без локальных файлов и секретов.
- Хранение runtime-конфигурации отдельно от checkout и GitHub Actions логов.
- Ограничение доступных с хоста портов порталом; внутренние компоненты остаются в Docker-сети.
- Сохранение именованных томов PostgreSQL и MinIO при обновлениях.

## Non-goals

- Изменение production-платформы: production остаётся на Kubernetes.
- Создание VM, установка Docker или регистрация self-hosted runner.
- Настройка корпоративного DNS, TLS-сертификата или reverse proxy.
- Включение Yandex ID или LDAP без адреса HTTPS callback и тестовых credentials.
- Перенос production данных или добавление демонстрационных бизнес-данных.

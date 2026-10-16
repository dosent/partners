# Партнёрский портал SimbirSoft

Интерактивный frontend-прототип выбранного варианта 1. React + Vite, оригинальные Mont и логотип с simbirsoft.com, иконки Phosphor.

## Запуск

```sh
npm install
npm run dev -- --host 127.0.0.1 --port 4173
```

## Сквозные тесты

Для локального E2E прогона установите зависимости проекта и браузер Playwright:

```sh
npm ci
npx playwright install chromium
npm run test:e2e
```

E2E builds the backend and MCP jars on the host with JDK 21 and Gradle Wrapper. It first uses Gradle's offline cache, then retries online if dependencies are missing. Docker runs the resulting jars with PostgreSQL; Java 21 must be installed on the host.

В Linux при отсутствии системных библиотек браузера используйте `npx playwright install --with-deps chromium`.

Чтобы запустить один сценарий или отфильтровать тесты по названию:

```sh
npm run test:e2e -- e2e/staff-moderation.spec.js
npm run test:e2e -- --grep "approved partner"
```

E2E-набор автоматически поднимает изолированный стек из `compose.e2e.yaml` с отдельной базой данных, тестовыми учётными данными, выключенным LDAP и фиктивной конфигурацией Yandex ID. После прогона стек и его тома удаляются; для тестов используются только эти значения. Обычный `compose.yaml` предназначен для локального запуска приложения и не является E2E-окружением.

Отчёты Playwright сохраняются в `playwright-report/html/index.html`, а traces и screenshots неуспешных тестов — в `test-results/`. Снимок логов сервисов Compose записывается в `playwright-report/e2e-services.log`. В GitLab CI эти каталоги публикуются как artifacts и сохраняются как при успехе, так и при ошибке. CI использует Docker-in-Docker, поэтому runner должен быть настроен с разрешённым privileged режимом; секреты production LDAP/Yandex ID для этого job не требуются.

## Staging на Docker Compose

Доставка на `vm-neshin-test.simbirsoft` настроена в GitHub Actions:
push в `main` или ручной запуск с полным SHA, достижимым из `main`. Нужны
self-hosted Linux runner с меткой `staging`, GitHub Environment `staging`
с SSH-доступом и заранее подготовленная VM с Docker Compose v2.

[Инструкция по подготовке VM, runner, Environment и запуску/откату](deploy/staging/README.md)
описывает runtime env вне Git, pinned SSH host keys, frontend порт, сохранение
именованных томов и ограничения TLS/auth. Первый реальный деплой выполняется
после настройки доступа; успешные локальные тесты не подтверждают доступность VM.

## Возможности

Обзор, создание и фильтрация лидов, карточки лидов, создание заявок на специалистов, фильтрация общего пула анонимизированных кандидатов, запрос их назначения с ожиданием подтверждения SimbirSoft, а также изменение статуса предложенных кандидатов, финансовая сводка и экспорт CSV, просмотр и демонстрационное согласование акта, профиль, уведомления. Мобильная адаптация и клавиатурное управление модальными окнами.

Данные демонстрационные, хранятся только в памяти текущей страницы. CRM, серверное хранение запросов на назначение, очередь и решение менеджера SimbirSoft, авторизация, загрузка файлов на сервер, ЭДО и переводы KZ/EN не подключены. Поток выбора одинаков для юридических и физических лиц. Согласование акта не является подписью. Экспорт акта доступен в TXT, не PDF. Публичная страница и внутренние роли в этот проход не входят.

Визуальная основа: `design/reference.png`. Brand assets получены с https://www.simbirsoft.com/ (исходный SVG-логотип, mont-400/500/600/700.woff). Фон сводки сгенерирован отдельно.

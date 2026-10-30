<comet-ambient-resume>
<!-- Managed by Comet. Edits inside this block may be replaced by comet init/update. -->
<!-- Contract: comet.resume_probe.v2 -->

## Comet Ambient Resume

In this repository, before starting work that may need code changes or investigation, pass the current user request to the read-only probe when a Comet workflow may already be active: `comet resume-probe . --stdin --json`.

- If the user explicitly invokes any Comet Skill through the host (for example, `@comet`, `/comet`, `@comet-native`, or `/comet-hotfix`), that explicit invocation takes precedence over this resume protocol; do not run the resume probe, and enter the invoked Skill directly.
- If the user explicitly invokes a non-Comet skill or slash command through the host, the task intent is already explicit in that invocation: do not run the resume probe, and execute the invoked skill directly.
- If you are already inside a Comet flow (including while waiting for the user to answer a question you asked in that flow), do not run the resume probe; treat replies such as option picks as continuation of the current change and proceed directly with the chosen option.
- Trust only the returned `workflow`, `skill`, and `entrySource`; project configuration or the no-config compatibility fallback alone selects them. Do not scan or switch to the other workflow.
- If the probe returns `auto_resume`, briefly state the selected active change and enter the permanent entry in `nextCommand`. Do not treat a state command as the resume entry or advance it blindly.
- If the probe returns `ask_user`, ask one short question and wait.
- If the current request did not explicitly invoke a Comet Skill and the probe returns `out_of_scope` or `none`, do not enter the Comet workflow.
- An `out_of_scope` or `none` result only means do not enter the Comet workflow for this new request; it never pauses or exits a Comet flow that is already in progress.
- If configuration or state is invalid and `nextCommand` is absent, stop and report the reason; do not guess another workflow.
- Never attach unrelated work merely because an active change exists. The Native entry inspects uncommitted work; the probe does not attribute it automatically.
</comet-ambient-resume>

## Рабочие правила проекта

### Git

- Перед изменениями проверяй `git status` и сохраняй все существующие пользовательские изменения.
- Для отдельной задачи используй ветку `codex/<краткое-имя>`; не начинай работу с очистки или сброса рабочей копии.
- Для новой или меняющей поведение функции создавай отдельный worktree и ветку `codex/<краткое-имя>` от актуальной `main`; изменения и проверки выполняй в feature worktree.
- Порядок интеграции: применимые unit/API/integration-проверки → обязательный браузерный e2e-набор → коммит проверенной feature-ветки → интеграционный коммит в `main`. При падении e2e исправь проблему и повтори прогон в feature worktree; коммит интеграции в `main` до успешного e2e запрещён. Push в remote выполняй отдельно.
- Не выполняй `git reset --hard`, `git clean`, force-push, удаление веток или переписывание опубликованной истории без прямого указания пользователя.
- Перед коммитом просматривай staged diff. Не добавляй секреты, локальные настройки, зависимости, сборки и посторонние файлы.
- Коммить и отправляй изменения только при явном поручении пользователя или действующей постоянной инструкции проекта. Для `partner-portal` такая инструкция требует после успешных e2e создавать коммит в feature-ветке и интеграционный коммит в локальном `main`; push всё равно требует отдельного явного запроса. Не включай несвязанные изменения в коммит.

### Пароли и секреты

- Не проси пользователя вставлять пароли, токены или приватные ключи в чат и не воспроизводи их в ответах.
- Не помещай реальные секреты в исходники, документацию, командные аргументы, Docker-образы, frontend bundle, логи или Git.
- Для локальной разработки используй `.env`, исключённый из Git; в репозитории допускай только `.env.example` с пустыми или фиктивными значениями. Для окружений используй secret store.
- Не печатай значения секретов из файлов или переменных окружения. Маскируй их при диагностике.
- Для партнёрского портала используй Yandex ID для партнёров и LDAP для сотрудников. Не добавляй локальные пароли партнёров без отдельного утверждённого требования.

### Docker

- Каждое развёртываемое приложение упаковывай в Docker-образ.
- Для локального запуска и интеграционных проверок используй Docker Compose.
- Для e2e используй отдельный Compose-проект и искусственные данные; реальные production credentials и данные в тестовом окружении запрещены.
- Передавай секреты контейнерам только во время выполнения через переменные окружения или secret store; не встраивай их в Dockerfile, build args, слои или образы.

### Язык документации

- Проектную документацию и спецификации пиши на русском языке. Идентификаторы кода, имена API, команды и термины протоколов оставляй в исходном виде.

### Проверка поведения

- Для каждой новой или изменённой функции добавляй браузерный e2e-сценарий основного пути и существенных отказов по доступу, состоянию или интеграции. Unit-, API-, компонентные и Sites-проверки не заменяют обязательный e2e.
- CI должен запускать обязательный e2e-набор и сохранять диагностику неуспешных прогонов без секретов и персональных данных.

## Единые правила UI

Для всего пользовательского интерфейса обязательны правила `partner-portal/docs/ui-guidelines.md`. Приоритет рабочего кабинета — ПК. Заголовки и содержимое разделов имеют общий левый край; основные действия всегда целиком в окне; текст, поля и кнопки не перекрываются. Используй общие UI-переменные и размеры кнопок. Длинные подписи переносятся, высота блока растёт; иконки не сжимаются. Горизонтальная прокрутка допускается только внутри таблицы/канбана, её нельзя скрывать на body. Проверяй 1280/1440/1920 px, масштаб 125%, длинные тексты и пустые/заполненные/error состояния. Общие правила применяются к новым и изменяемым экранам публичной части, регистрации, кабинета и сотрудников.

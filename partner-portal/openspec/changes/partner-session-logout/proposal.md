# Proposal

## Why

Одобренный партнёр может работать в кабинете через сессию Yandex ID, но интерфейс не предоставляет способ завершить эту сессию. Кнопка выхода и серверное завершение сессии дадут партнёру явный контроль над доступом к кабинету.

## What Changes

- Добавить защищённую CSRF операцию выхода партнёра в API аутентификации Yandex ID.
- Добавить кнопку «Выйти» в правом верхнем углу шапки одобренного кабинета.
- После выхода вернуть пользователя на публичную страницу; при ошибке показать сообщение и оставить кабинет доступным до успешного завершения запроса.

## Capabilities

### New Capabilities

- `partner-session`: завершение авторизованной партнёрской web-сессии.

### Modified Capabilities

- Нет.

## Impact

- Backend: модуль `access`, маршрут `/api/v1/auth/yandex/logout`, CSRF и инвалидирование HTTP-сессии.
- Frontend: клиент `src/api/partnerRegistrationApi.js` и шапка `ApprovedPartnerPortal` в `src/App.jsx`.
- Проверки: backend API/authentication tests, e2e успешного выхода, OpenSpec validation.

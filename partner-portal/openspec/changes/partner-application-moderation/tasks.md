## 1. Внутренняя RBAC в access

- [x] 1.1 Сохранить назначение staff username/roles в access persistence и добавить append-only историю выдачи/отзыва с актёром и временем.
- [x] 1.2 Добавить приложение-owned `StaffPermissionMatrix` для всех четырёх ролей и динамическую проверку актуальных permissions на каждом защищённом запросе.
- [x] 1.3 Добавить bootstrap grant `ivan.neshin -> SYSTEM_ADMIN` в отдельный Liquibase changeset; запретить снимать его через API.
- [x] 1.4 Реализовать `GET/PUT /api/v1/admin/staff-access/users/{username}/roles`, запрет неизвестных ролей и RBAC проверку `staff.access.manage`.
- [x] 1.5 Проверить, что выдача/отзыв роли вступает в силу для активной сессии и каждое изменение аудируется.

## 2. LDAP/FreeIPA authentication

- [x] 2.1 Настроить LDAP параметры `LDAP_URL`, `LDAP_USERS_DN`, `LDAP_CA_CERT_FILE` заданными значениями; DN формировать из escaped `uid` и users DN.
- [x] 2.2 Проверять LDAPS server certificate пользовательским CA `/app/certs/simbirsoft-ca.crt`; сертификат монтировать read-only снаружи, без включения в Git/image и без insecure fallback.
- [x] 2.3 Удалить зависимость выдачи ролей от LDAP group mapping; после bind загружать роли из внутренней RBAC, а при пустом назначении возвращать `200 ACCESS_PENDING` и создавать ограниченную сессию.
- [ ] 2.4 Проверить корректный/ошибочный bind, неверный/недоступный CA, неизвестного LDAP-пользователя и успешный вход `ivan.neshin` с bootstrap role.

## 3. Staff access UI

- [x] 3.1 Показывать `ACCESS_PENDING` страницу с просьбой обратиться к администратору и кнопкой повторной проверки сессии.
- [x] 3.2 Добавить экран управления ролями по username для пользователей с `staff.access.manage`; роли назначаются только через RBAC API.
- [ ] 3.3 Проверить переход pending→active после назначения и запрет открытия admin UI без permissions.

## 4. API и application слой модерации

- [x] 4.1 Добавить paginated query/projection `MANUAL_REVIEW` без расшифрованных реквизитов и с устойчивой сортировкой.
- [x] 4.2 Реализовать API DTO, контроллер карточки и очереди по `/api/v1/admin/partner-applications`.
- [x] 4.3 Реализовать `GET /api/v1/partner-applications/me` с выборкой только по аутентифицированному provider/subject.
- [x] 4.4 Реализовать application service решений approve/reject/request-changes с проверкой permission, статуса, причины и транзакцией.
- [x] 4.5 Добавить межмодульный application interface `access` для проверки наличия внутреннего назначения `PARTNER_MANAGER`.
- [x] 4.6 Добавить необязательное назначение менеджера в агрегат/persistence и Liquibase changeset.

## 5. Безопасность и конкурентность

- [x] 5.1 Ограничить административные маршруты permissions `partner.application.read`/`partner.application.decide` и `staff.access.manage`.
- [x] 5.2 Преобразовать optimistic locking и повторное решение в единый `409 CONCURRENT_MODIFICATION`.
- [x] 5.3 Проверить безопасные DTO/логи: реквизиты не попадают в очередь, причины не принимают управляющие символы и ограничены 1000 символами.
- [x] 5.4 Проверить неизменяемость историй RBAC/заявки и атомарность статуса с назначением менеджера.

## 6. Проверка

- [ ] 6.1 Добавить unit/API тесты RBAC matrix, bootstrap администратора, grant/revoke, LDAP TLS trust, очереди, карточки, owner scope, решений и конкурентного изменения.
- [ ] 6.2 Проверить Liquibase migrations на чистой базе и на предыдущей поддерживаемой схеме.
- [ ] 6.3 Проверить OpenAPI и запрет прямого доступа между модулями.
- [x] 6.4 Выполнить `openspec validate partner-application-moderation --strict`.

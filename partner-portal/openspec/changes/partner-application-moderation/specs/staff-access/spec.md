## Purpose

Аутентифицировать сотрудников через корпоративный LDAPS и хранить полномочия сотрудников в RBAC-модели самого портала.

## ADDED Requirements

### Requirement: LDAP подтверждает личность сотрудника
Портал SHALL аутентифицировать сотрудников через `LDAP_URL=ldaps://freeipa01.simbirsoft:636/` и базу пользователей `LDAP_USERS_DN=cn=users,cn=accounts,dc=simbirsoft,dc=dev`. TLS SHALL доверять CA из `LDAP_CA_CERT_FILE=/app/certs/simbirsoft-ca.crt`; недоступный CA или ошибка проверки сертификата MUST NOT приводить к insecure fallback. Успешная LDAP-аутентификация SHALL NOT сама назначать роль портала.

#### Scenario: Сотрудник входит с действительными LDAP credentials
- **WHEN** сотрудник проходит LDAP bind и имеет роли, назначенные порталом
- **THEN** портал SHALL создать staff-сессию с текущими приложенческими permissions

#### Scenario: Пароль, CA или LDAP endpoint не проходят проверку
- **WHEN** bind отклонён, CA недоступен/недоверен или LDAPS endpoint недоступен
- **THEN** портал SHALL отказать во входе и SHALL NOT создавать staff-сессию

#### Scenario: Нет назначения роли в портале
- **WHEN** LDAP подтвердил сотрудника, но RBAC портала не назначил ему роли
- **THEN** портал SHALL вернуть успешный login response `200` с `accessState=ACCESS_PENDING`, создать ограниченную staff-сессию без business permissions и SHALL NOT выдавать права из LDAP-групп

#### Scenario: Сотрудник без роли проверяет доступ
- **WHEN** сотрудник в состоянии `ACCESS_PENDING` открывает портал или повторно запрашивает `/api/v1/auth/staff/session`
- **THEN** портал SHALL показать страницу с просьбой обратиться к администратору за назначением роли и SHALL разрешить только `GET /api/v1/auth/staff/session` и `POST /api/v1/auth/staff/logout`

#### Scenario: Ограниченная сессия вызывает бизнес API
- **WHEN** сотрудник с `ACCESS_PENDING` вызывает любой business/admin API
- **THEN** портал SHALL вернуть `403` без раскрытия данных и без изменения состояния

### Requirement: Права сотрудников назначаются внутренней RBAC
Портал SHALL хранить назначения `username -> role` в собственной БД. Permission matrix SHALL определяться приложением для известных ролей `PARTNER_MANAGER`, `RESOURCE_MANAGER`, `FINANCE_CONTROLLER`, `SYSTEM_ADMIN`; LDAP-группы MUST NOT быть источником permissions. Матрица SHALL включать: `PARTNER_MANAGER` — `partner.application.read`, `partner.application.decide`, `lead.manage`; `RESOURCE_MANAGER` — `staffing.request.manage`, `candidate.manage`; `FINANCE_CONTROLLER` — `finance.manage`; `SYSTEM_ADMIN` — `staff.access.manage`, `partner.application.read`, `partner.application.decide`, `localization.manage`, `directory.manage`. Изменения назначений SHALL применяться к последующим запросам, включая уже активную staff-сессию.

#### Scenario: Матрица разрешает операцию по назначенной роли
- **WHEN** сотрудник вызывает API и его внутренняя роль разрешает требуемую permission
- **THEN** портал SHALL разрешить запрос согласно permission matrix

#### Scenario: Роль отозвана при активной сессии
- **WHEN** системный администратор отзывает роль сотрудника
- **THEN** следующий защищённый запрос SHALL оцениваться по обновлённым назначениям и SHALL быть запрещён, если permission больше не выдана

### Requirement: Системный администратор управляет назначениями ролей
Только сотрудник с permission `staff.access.manage` SHALL иметь доступ к `GET /api/v1/admin/staff-access/users/{username}/roles` и `PUT /api/v1/admin/staff-access/users/{username}/roles`. API SHALL принимать только известные роли и SHALL записывать каждую выдачу/отзыв в append-only журнал с целевым username, ролью, актёром и временем. Начальное назначение `SYSTEM_ADMIN` SHALL быть выдано username `ivan.neshin`; этот пользователь всё равно MUST пройти LDAPS-аутентификацию, а bootstrap назначение MUST NOT сниматься через API.

#### Scenario: Первый системный администратор входит
- **WHEN** username `ivan.neshin` успешно проходит LDAPS-аутентификацию после начального Liquibase seed
- **THEN** портал SHALL выдать ему permission `staff.access.manage`

#### Scenario: Администратор назначает или отзывает роль
- **WHEN** пользователь с `staff.access.manage` изменяет набор ролей сотрудника
- **THEN** портал SHALL атомарно сохранить актуальный набор назначений и append-only события аудита

#### Scenario: Администратор выдаёт роль через интерфейс
- **WHEN** системный администратор вводит username сотрудника и сохраняет выбранные роли в back-office
- **THEN** интерфейс SHALL вызвать защищённый RBAC API, показать результат и обновить список ролей сотрудника

#### Scenario: Обычный менеджер управляет ролями
- **WHEN** сотрудник без `staff.access.manage` вызывает API управления ролями
- **THEN** портал SHALL вернуть `403` и SHALL NOT изменить назначение

#### Scenario: Попытка снять bootstrap grant
- **WHEN** любой администратор пытается отозвать `SYSTEM_ADMIN` у `ivan.neshin`
- **THEN** портал SHALL отклонить изменение и SHALL сохранить bootstrap grant

### Requirement: Изменение роли сотрудника аудируется
История выдачи/отзыва ролей SHALL быть неизменяемой и недоступной для изменения пользователем, чьё назначение меняется.

#### Scenario: Системный администратор меняет роли
- **WHEN** системный администратор выдаёт или отзывает роль
- **THEN** портал SHALL записать актёра, целевой username, роль, действие и время в append-only историю

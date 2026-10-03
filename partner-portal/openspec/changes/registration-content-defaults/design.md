## Контекст

Экран `src/admin/RegistrationContentPage.jsx` создаёт пустую форму, а backend при публикации проверяет обязательные подписи, метаданные трёх оферт, метаданные согласия и настроенный ключ логотипа. Администратор видит список отсутствующих полей и вынужден заполнять даже стандартные подписи интерфейса вручную.

## Решение

Добавить на frontend единый набор русских начальных значений. Функция нормализации черновика будет применять их при создании формы, загрузке сохранённого черновика и импорте JSON. Для каждой известной строки и метаданных значение по умолчанию подставляется, только если текущее значение отсутствует или пустое после `trim`. Непустые значения и дополнительные строки сохраняются.

Начальное значение ключа логотипа — `simbirsoft`. Каждая из трёх оферт получает версию `1.0` и заголовок, соответствующий типу заявителя. Согласие получает версию `1.0` и стандартный заголовок.

Начальные значения находятся только в редактируемом черновике frontend. Они не публикуются автоматически и не добавляются в базу до действия «Сохранить черновик». Для публикации администратор по-прежнему отдельно сохраняет черновик и нажимает «Опубликовать».

## Значения по умолчанию

| Ключ | Значение |
| --- | --- |
| `registration.title` | Регистрация партнёра |
| `registration.types.individual` | Физическое лицо |
| `registration.types.legalEntity` | Юридическое лицо |
| `registration.types.soleProprietor` | Индивидуальный предприниматель |
| `registration.fields.fullName` | Фамилия, имя и отчество |
| `registration.fields.email` | Электронная почта |
| `registration.fields.phone` | Телефон |
| `registration.fields.country` | Страна |
| `registration.fields.region` | Регион |
| `registration.fields.companyName` | Наименование организации |
| `registration.fields.taxIdentifier` | ИНН / БИН |
| `registration.consent.offer` | Я принимаю условия партнёрской оферты |
| `registration.consent.privacy` | Я согласен на обработку персональных данных |
| `registration.actions.submit` | Отправить заявку |
| `registration.countries.RU` | Россия |
| `registration.countries.KZ` | Казахстан |
| `registration.status.manualReview` | Заявка будет рассмотрена вручную |
| `registration.ui.loading` | Загружаем форму регистрации… |
| `registration.auth.title` | Войдите через Яндекс ID |
| `registration.auth.info` | Для подачи заявки войдите через Яндекс ID. Форма станет доступна после загрузки опубликованных документов. |
| `registration.auth.action` | Войти через Яндекс ID |
| `registration.auth.back` | Вернуться на страницу партнёрской программы |
| `registration.errors.conflict` | Документы обновились. Проверьте актуальную редакцию и подтвердите согласия ещё раз. |
| `registration.errors.unavailable` | Регистрация временно недоступна. Попробуйте позже. |
| `registration.errors.sessionExpired` | Сессия входа завершилась. Войдите через Яндекс ID ещё раз. |
| `registration.errors.generic` | Не удалось загрузить форму. Попробуйте ещё раз. |
| `registration.errors.taxIdentifier` | Проверьте ИНН / БИН. |
| `registration.status.PENDING_REVIEW` | Заявка ожидает проверки |
| `registration.status.MANUAL_REVIEW` | Заявка на ручной проверке |
| `registration.status.CHANGES_REQUESTED` | Требуются изменения в заявке |
| `registration.status.REJECTED` | Заявка отклонена |
| `registration.status.APPROVED` | Заявка одобрена |
| `registration.status.approvedInfo` | Заявка одобрена. Доступ в кабинет партнёра открыт. |
| `registration.status.pendingInfo` | Заявка отправлена и ожидает проверки. |
| `registration.status.resubmitInfo` | Обновите данные заявки и отправьте её повторно. |
| `registration.actions.resubmit` | Отправить повторно |
| `registration.actions.retry` | Повторить |
| `registration.actions.refresh` | Обновить статус |
| `registration.actions.submitProgress` | Отправляем заявку… |
| `registration.documents.published` | Опубликовано |
| `registration.documents.revision` | Редакция |
| `portal.title` | Кабинет партнёра |
| `portal.status.approved` | Доступ одобрен |
| `portal.section.overview` | Обзор |
| `portal.section.leads` | Лиды |
| `portal.section.vacancies` | Заявки на специалистов |
| `portal.section.finance` | Финансы |
| `portal.section.documents` | Документы |
| `portal.welcome` | Добро пожаловать в партнёрский портал SimbirSoft. |
| `portal.emptySection` | В этом разделе пока нет данных. |
| `offers.INDIVIDUAL.version` | 1.0 |
| `offers.INDIVIDUAL.title` | Партнёрская оферта для физического лица |
| `offers.SOLE_PROPRIETOR.version` | 1.0 |
| `offers.SOLE_PROPRIETOR.title` | Партнёрская оферта для индивидуального предпринимателя |
| `offers.LEGAL_ENTITY.version` | 1.0 |
| `offers.LEGAL_ENTITY.title` | Партнёрская оферта для юридического лица |
| `privacyNoticeVersion` | 1.0 |
| `privacyNoticeTitle` | Согласие на обработку персональных данных |
| `logoAssetKey` | simbirsoft |

## Правовые тексты и публикация

Значения `offers.*.body` и `privacyNoticeBody` не входят в список запрошенных значений по умолчанию. Их содержимое остаётся без изменений; если утверждённый текст отсутствует, серверная проверка продолжает блокировать публикацию. Изменение не создаёт production seed-данных и не ослабляет проверку настроенного/разрешённого логотипа.

## Границы и совместимость

- Изменяется только редактируемый русский черновик, используемый текущим экраном администрирования.
- Уже сохранённые непустые подписи, метаданные, тексты документов, логотип и дополнительные ключи не затираются.
- API, публикации и consent snapshots остаются без изменений.
- Действия сохранения и публикации остаются раздельными.

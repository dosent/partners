import { randomInt, randomUUID } from 'node:crypto';

export function uniqueFixtureId() {
  return `e2e-${randomUUID().replaceAll('-', '')}`;
}

function checksumDigit(value, weights) {
  const sum = [...value].reduce((total, digit, index) => total + Number(digit) * weights[index], 0);
  return String((sum % 11) % 10);
}

function syntheticRussianInn(type) {
  const digits = String(randomInt(1_000_000_000, 9_999_999_999)).padStart(10, '0');
  if (type === 'LEGAL_ENTITY') {
    const base = digits.slice(0, 9);
    return base + checksumDigit(base, [2, 4, 10, 3, 5, 9, 4, 6, 8]);
  }
  const firstCheck = checksumDigit(digits, [7, 2, 4, 10, 3, 5, 9, 4, 6, 8]);
  const secondBase = digits + firstCheck;
  const secondCheck = checksumDigit(secondBase, [3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8]);
  return secondBase + secondCheck;
}

export function isValidRussianInn(value, type) {
  if (type === 'LEGAL_ENTITY') {
    return /^\d{10}$/.test(value)
      && checksumDigit(value.slice(0, 9), [2, 4, 10, 3, 5, 9, 4, 6, 8]) === value[9];
  }
  if (type === 'SOLE_PROPRIETOR') {
    if (!/^\d{12}$/.test(value)) return false;
    const first = checksumDigit(value.slice(0, 10), [7, 2, 4, 10, 3, 5, 9, 4, 6, 8]);
    const second = checksumDigit(value.slice(0, 11), [3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8]);
    return first === value[10] && second === value[11];
  }
  return false;
}

export function syntheticPartnerData() {
  const id = uniqueFixtureId();
  const phoneDigits = String(randomInt(1_000_000_000, 9_999_999_999));
  return {
    id,
    subject: id,
    fullName: `Тестовый партнёр ${id.slice(-8)}`,
    email: `${id}@example.test`,
    phone: `+7${phoneDigits}`,
    region: `Тестовый регион ${id.slice(-6)}`,
    companyName: `Тестовая организация ${id.slice(-8)}`,
    individualProprietorInn: syntheticRussianInn('SOLE_PROPRIETOR'),
    legalEntityInn: syntheticRussianInn('LEGAL_ENTITY'),
  };
}

export function registrationContentFixture(locale = 'RU') {
  const strings = {
    'registration.title': 'Заявка на партнёрство',
    'registration.types.individual': 'Физическое лицо',
    'registration.types.legalEntity': 'Юридическое лицо',
    'registration.types.soleProprietor': 'Индивидуальный предприниматель',
    'registration.fields.fullName': 'ФИО',
    'registration.fields.email': 'Электронная почта',
    'registration.fields.phone': 'Телефон',
    'registration.fields.country': 'Страна',
    'registration.fields.region': 'Регион',
    'registration.fields.companyName': 'Название организации',
    'registration.fields.taxIdentifier': 'ИНН',
    'registration.consent.offer': 'Принимаю условия оферты',
    'registration.consent.privacy': 'Согласен на обработку персональных данных',
    'registration.actions.submit': 'Отправить заявку',
    'registration.countries.RU': 'Россия',
    'registration.countries.KZ': 'Казахстан',
    'registration.status.manualReview': 'Заявка передана на ручную проверку',
    'registration.ui.loading': 'Загрузка',
    'registration.auth.title': 'Войдите через Яндекс ID',
    'registration.auth.info': 'Для продолжения войдите в свою учётную запись.',
    'registration.auth.action': 'Войти через Яндекс ID',
    'registration.auth.back': 'Вернуться на главную',
    'registration.errors.conflict': 'Данные изменились. Обновите страницу.',
    'registration.errors.unavailable': 'Регистрация временно недоступна. Попробуйте позже.',
    'registration.errors.sessionExpired': 'Сессия истекла. Войдите снова.',
    'registration.errors.generic': 'Не удалось отправить заявку. Попробуйте ещё раз.',
    'registration.errors.taxIdentifier': 'Проверьте ИНН.',
    'registration.status.PENDING_REVIEW': 'На проверке',
    'registration.status.MANUAL_REVIEW': 'Ручная проверка',
    'registration.status.CHANGES_REQUESTED': 'Нужны изменения',
    'registration.status.REJECTED': 'Отклонена',
    'registration.status.APPROVED': 'Одобрена',
    'registration.status.approvedInfo': 'Заявка одобрена.',
    'registration.status.pendingInfo': 'Заявка ожидает проверки.',
    'registration.status.resubmitInfo': 'Внесите изменения и отправьте заявку повторно.',
    'registration.actions.resubmit': 'Отправить повторно',
    'registration.actions.retry': 'Повторить',
    'registration.actions.refresh': 'Обновить статус',
    'registration.actions.submitProgress': 'Отправляем заявку…',
    'registration.documents.published': 'Опубликованные документы',
    'registration.documents.revision': 'Редакция документов',
    'portal.title': 'Партнёрский портал',
    'portal.status.approved': 'Партнёр подтверждён',
    'portal.section.overview': 'Обзор',
    'portal.section.leads': 'Мои лиды',
    'portal.section.vacancies': 'Заявки на специалистов',
    'portal.section.finance': 'Вознаграждения',
    'portal.section.documents': 'Документы',
    'portal.welcome': 'Добро пожаловать в партнёрский портал.',
    'portal.emptySection': 'Здесь пока нет данных.',
  };

  const offers = Object.fromEntries(['INDIVIDUAL', 'SOLE_PROPRIETOR', 'LEGAL_ENTITY'].map(type => [type, {
    version: 'e2e-1',
    title: `Тестовая оферта ${type}`,
    body: `Искусственный текст оферты для автоматизированной проверки. Тип заявителя: ${type}. Документ не является юридической офертой.`,
  }]));

  const profileValues = Object.fromEntries([
    'navigation', 'title', 'description', 'settings.title', 'locale.label', 'locale.RU', 'locale.KZ', 'locale.EN',
    'notifications.title', 'notifications.email', 'notifications.inApp', 'actions.save', 'actions.saving',
    'status.saved', 'errors.save', 'loading', 'errors.load', 'errors.localeUnavailable',
  ].map(key => [`portal.profile.${key}`, `E2E ${locale} ${key}`]));
  Object.assign(strings, profileValues);
  if (locale !== 'RU') {
    strings['portal.title'] = `E2E ${locale} Partner Portal`;
    strings['portal.status.approved'] = `E2E ${locale} Approved`;
    strings['portal.section.overview'] = `E2E ${locale} Overview`;
    strings['portal.section.leads'] = `E2E ${locale} Leads`;
    strings['portal.section.vacancies'] = `E2E ${locale} Specialist requests`;
    strings['portal.section.finance'] = `E2E ${locale} Rewards`;
    strings['portal.section.documents'] = `E2E ${locale} Documents`;
    strings['portal.welcome'] = `E2E ${locale} Welcome`;
    strings['portal.emptySection'] = `E2E ${locale} No data`;
    strings['registration.fields.fullName'] = `E2E ${locale} Full name`;
    strings['registration.fields.email'] = `E2E ${locale} Email`;
    strings['registration.fields.phone'] = `E2E ${locale} Phone`;
    strings['registration.fields.country'] = `E2E ${locale} Country`;
    strings['registration.countries.RU'] = `E2E ${locale} Russia`;
    strings['registration.countries.KZ'] = `E2E ${locale} Kazakhstan`;
    strings['registration.fields.region'] = `E2E ${locale} Region`;
  }

  return {
    strings,
    offers,
    privacyNoticeVersion: 'e2e-1',
    privacyNoticeTitle: 'Тестовое согласие на обработку персональных данных',
    privacyNoticeBody: 'Искусственный текст согласия для автоматизированной проверки. Не содержит персональных данных и не предназначен для реального использования.',
    logoAssetKey: locale === 'KZ' ? '17soft' : 'simbirsoft',
  };
}

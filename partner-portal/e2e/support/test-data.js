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
    'portal.actions.logout': `E2E ${locale} portal.actions.logout`,
    'portal.actions.loggingOut': `E2E ${locale} portal.actions.loggingOut`,
    'portal.errors.logout': `E2E ${locale} portal.errors.logout`,
  };

  const staffingCopy = {
    RU: ['Подбор команды','Ваши заявки','Создавайте заявки и отслеживайте их статус.','Создать заявку','Загружаем заявки…','Пока нет заявок','Опишите роль, навыки и условия работы — команда поможет с подбором.','Обновить список','Новая',
      'К списку','Новая потребность','Заявка на специалиста','Заполните требования к роли и условия работы. Файлы можно добавить отдельно.','Контекст заявки','Проект / лид','Выберите лид вашей компании','Доступны только ваши лиды. Если нужного проекта нет, сначала создайте лид.','Позиция и требования','Должность / роль','Количество специалистов','Уровень','Выберите уровень','Обязательные навыки и стек','Перечислите навыки через запятую.','Будет плюсом','Задачи и ожидания','Условия работы','Загрузка, часов в неделю','Формат работы','Удалённо','Гибрид','Офис','Город / страна','Можно работать за пределами РФ','Дата подключения гибкая','Ожидаемая дата подключения','Отбор кандидата','Знание языка','Не требуется / не задано','Уровень языка','Выберите уровень языка','Интервью с заказчиком','Технический скрининг','Нужно предоставить CV','Файлы','необязательно','Описание проекта','Пример / шаблон CV','PDF или DOCX, до 20 МБ','Отмена','Отправить заявку','Сохраняем…','Принимаются PDF или DOCX размером до 20 МБ. Введённые данные сохранены.','Заявка сохранена','Статус','К списку заявок','Новая заявка','Заявка сохранена, но не все файлы загрузились:','Повторить загрузку файлов','Повторить','Срок пока не определён','Английский','Русский','Казахский','Проверьте обязательные поля и введённые значения.','Сервис временно недоступен. Попробуйте позже.','Не удалось создать заявку. Данные формы сохранены.','Не удалось загрузить файл. Повторите попытку.','Показать ещё','Например, Backend-разработчик','Родной язык','Проверьте это поле.','Создана','Вложения','Продолжить без файла'],
    EN: ['Team staffing','Your requests','Create requests and track their status.','Create request','Loading requests…','No requests yet','Describe the role, skills, and working conditions — our team will help with staffing.','Refresh list','New',
      'Back to requests','New staffing need','Staffing request','Describe the role and working conditions. Files can be added separately.','Request context','Project / lead','Choose your company lead','Only your leads are available. Create a lead first if the project is missing.','Role and requirements','Position / role','Number of specialists','Seniority','Choose seniority','Required skills and stack','List skills separated by commas.','Nice to have','Tasks and expectations','Working conditions','Hours per week','Work arrangement','Remote','Hybrid','Office','City / country','Work outside Russia is allowed','Start date is flexible','Expected start date','Candidate selection','Language requirement','Not required / unspecified','Language proficiency','Choose proficiency','Client interview','Technical screening','CV required','Files','optional','Project brief','CV example / template','PDF or DOCX, up to 20 MB','Cancel','Submit request','Saving…','PDF or DOCX files up to 20 MB are accepted. Your entries are saved.','Request saved','Status','Back to requests','New request','Request saved, but some files did not upload:','Retry file upload','Retry','Date not set','English','Russian','Kazakh','Check required fields and entered values.','The service is temporarily unavailable. Try again later.','Could not submit the request. Your entries are saved.','Could not upload the file. Please try again.','Show more','e.g. Backend developer','Native language','Check this field.','Submitted','Attachments','Continue without file'],
    KZ: ['Мамандарды іріктеу','Өтінімдеріңіз','Өтінім жасап, мәртебесін бақылаңыз.','Өтінім жасау','Өтінімдер жүктелуде…','Әзірге өтінім жоқ','Рөлді, дағдыларды және жұмыс шарттарын сипаттаңыз — команда маман табуға көмектеседі.','Тізімді жаңарту','Жаңа',
      'Өтінімдерге оралу','Жаңа қажеттілік','Маманға өтінім','Рөл мен жұмыс шарттарын сипаттаңыз. Файлдарды бөлек қосуға болады.','Өтінім мәнмәтіні','Жоба / лид','Компанияңыздың лидін таңдаңыз','Тек өз лидтеріңіз көрсетіледі. Жоба болмаса, алдымен лид жасаңыз.','Рөл және талаптар','Лауазым / рөл','Мамандар саны','Деңгей','Деңгейді таңдаңыз','Міндетті дағдылар мен стек','Дағдыларды үтірмен бөліңіз.','Қосымша артықшылық','Міндеттер мен күтілімдер','Жұмыс шарттары','Аптасына сағат саны','Жұмыс форматы','Қашықтан','Аралас','Кеңседе','Қала / ел','Ресейден тыс жерде жұмыс істеуге болады','Басталу күні икемді','Күтілетін басталу күні','Кандидатты іріктеу','Тіл талабы','Талап етілмейді / көрсетілмеген','Тілді меңгеру деңгейі','Деңгейді таңдаңыз','Тапсырыс берушімен сұхбат','Техникалық скрининг','CV ұсыну қажет','Файлдар','міндетті емес','Жоба сипаттамасы','CV үлгісі','PDF немесе DOCX, 20 МБ дейін','Болдырмау','Өтінімді жіберу','Сақталуда…','PDF немесе DOCX, 20 МБ дейін қабылданады. Енгізілген деректер сақталды.','Өтінім сақталды','Мәртебе','Өтінімдер тізімі','Жаңа өтінім','Өтінім сақталды, бірақ кей файлдар жүктелмеді:','Файлдарды қайта жүктеу','Қайталау','Мерзімі анықталмаған','Ағылшын тілі','Орыс тілі','Қазақ тілі','Міндетті өрістер мен енгізілген деректерді тексеріңіз.','Қызмет уақытша қолжетімсіз. Кейінірек қайталап көріңіз.','Өтінімді жіберу мүмкін болмады. Деректеріңіз сақталды.','Файл жүктелмеді. Қайталап көріңіз.','Тағы көрсету','Мысалы, Backend әзірлеуші','Ана тілі','Өрісті тексеріңіз.','Жасалған күні','Тіркемелер','Файлсыз жалғастыру'],
  };
  const staffingKeys = ['list.eyebrow','list.title','list.description','list.create','list.loading','list.emptyTitle','list.emptyDescription','list.refresh','status.NEW',
    'form.back','form.eyebrow','form.title','form.description','form.context','form.project','form.projectPlaceholder','form.projectHelp','form.role','form.position','form.headcount','form.grade','form.chooseGrade','form.requiredSkills','form.skillsHelp','form.preferredSkills','form.tasks',
    'form.work','form.hours','form.workMode','form.remote','form.hybrid','form.office','form.location','form.outsideRussia','form.flexibleDate','form.desiredDate','form.selection','form.language','form.noLanguage','form.languageLevel','form.chooseLanguageLevel',
    'form.interview','form.screening','form.cv','form.files','form.optional','form.brief','form.cvExample','form.fileHelp','form.cancel','form.submit','form.saving','form.invalidFile',
    'success.eyebrow','success.status','success.list','success.new','success.uploadFailed','success.retryUpload','errors.retry','list.flexibleDate','form.languageEnglish','form.languageRussian','form.languageKazakh','errors.validation','errors.unavailable','errors.submit','errors.upload','list.more','form.positionPlaceholder','form.nativeLanguage','errors.field','list.submitted','list.files','form.continueWithoutFile'];
  Object.assign(strings, Object.fromEntries(staffingKeys.map((key, index) => [`staffing.${key}`, staffingCopy[locale][index]])));

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
  Object.assign(strings, Object.fromEntries([
    'navigation', 'title', 'description', 'empty', 'name.label', 'actions.create', 'actions.creating',
    'actions.revoke', 'actions.cancel', 'status.created', 'status.revoked', 'loading', 'errors.load',
    'errors.create', 'errors.revoke', 'secret.title', 'secret.description', 'revoke.confirm',
  ].map(key => [`portal.apiKeys.${key}`, `E2E ${locale} apiKeys ${key}`])));
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

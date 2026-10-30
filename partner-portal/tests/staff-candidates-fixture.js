// Искусственные подписи только для проверок; не являются утверждёнными переводами.
export const candidateTestCopy = {
  title: 'Кандидаты', create: 'Добавить кандидата', loading: 'Загружаем кандидатов…', empty: 'Кандидатов пока нет',
  refresh: 'Обновить', search: 'Поиск кандидатов', filter: 'Доступность', all: 'Все',
  available: 'Доступен', unavailable: 'Недоступен', manual: 'Доступность указана сотрудником',
  previous: 'Назад', next: 'Далее', page: 'Страница', total: 'Всего', open: 'Открыть карточку',
  edit: 'Редактировать', back: 'К списку', home: 'Вернуться в кабинет', logout: 'Выйти', locale: 'Язык интерфейса',
  role: 'Роль / направление', grade: 'Грейд', externalKey: 'Внешний ключ (Jira)', externalHelp: 'Ключ для будущей привязки. Связь с Jira не проверена.',
  skills: 'Навыки', skillsHelp: 'Перечислите навыки через запятую.', years: 'Опыт, лет', months: 'Опыт, месяцев',
  description: 'Описание', amount: 'Ставка кандидата за час', currency: 'Валюта', perHour: 'за час',
  choose: 'Выберите значение', cv: 'CV', fileHelp: 'PDF или DOCX, до 20 МБ', save: 'Сохранить', saving: 'Сохраняем…', cancel: 'Отмена',
  download: 'Скачать CV', replace: 'Заменить CV', updatedAt: 'Обновлено', rateChangedAt: 'Ставка изменена', rateChangedBy: 'Изменил ставку',
  saved: 'Кандидат сохранён', error: 'Не удалось выполнить действие. Введённые данные сохранены.',
  conflict: 'Карточка изменена. Введённые данные сохранены для сверки.', invalid: 'Проверьте обязательные поля.',
  invalidFile: 'Выберите PDF или DOCX до 20 МБ.', forbidden: 'Недостаточно прав',
  retry: 'Повторить', retryCreate: 'Повторить создание', retryHelp: 'Результат создания неизвестен. Повторите исходный запрос с тем же файлом.',
  serviceUnavailable: 'Сервис временно недоступен. Повторите попытку.', rejectedFile: 'CV отклонён проверкой. Введённые данные сохранены.', externalKeyConflict: 'Внешний ключ уже используется. Введённые данные сохранены.',
  missingFile: 'Выберите исходный файл заново.',
};
export const candidateItem = {
  id: '11111111-1111-4111-8111-111111111111', code: 'CND-001', role: 'Java synthetic', grade: 'Middle',
  skills: ['Java', 'SQL'], experienceMonths: 37, description: 'Описание синтетического кандидата',
  availability: 'AVAILABLE', externalKey: 'JIRA-TEST-001', hourlyRate: { amount: '2500.50', currency: 'RUB', changedAt: '2026-10-30T10:00:00Z', changedBy: 'synthetic-rm' },
  cv: { id: 'cv', version: 1, fileName: 'synthetic.pdf', size: 20, contentType: 'application/pdf' },
  createdAt: '2026-10-30T10:00:00Z', updatedAt: '2026-10-30T10:00:00Z', version: 1,
};
export async function mockCandidates(page, { items = [candidateItem], missingText = false, permission = true, handler } = {}) {
  const strings = Object.fromEntries(Object.entries(candidateTestCopy).map(([key, value]) => [`candidates.${key}`, value]));
  if (missingText) delete strings['candidates.create'];
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const json = (body, status = 200) => route.fulfill({ status, json: body });
    if (url.pathname === '/api/v1/auth/staff/session') return json({ username: 'synthetic-rm', accessState: 'ACTIVE', permissions: permission ? ['candidate.manage'] : [] });
    if (url.pathname === '/api/v1/auth/csrf') return json({ token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    if (url.pathname.includes('/public/registration-content/')) return json({ locale: 'RU', strings: { ...strings, 'portal.profile.errors.localeUnavailable': 'Содержимое выбранной локали недоступно.' } });
    if (handler && await handler({ route, request, url, json })) return;
    if (url.pathname === '/api/v1/admin/candidates') return json({ items, page: 0, size: 20, total: items.length });
    if (url.pathname === `/api/v1/admin/candidates/${candidateItem.id}`) return json(candidateItem);
    return json({}, 404);
  });
  await page.goto('/staff/candidates');
}
export async function fillCandidate(page) {
  await page.getByLabel('Роль / направление', { exact: true }).fill('Java synthetic');
  await page.getByLabel('Навыки', { exact: true }).fill('Java, SQL');
  await page.getByLabel('Опыт, лет', { exact: true }).fill('0');
  await page.getByLabel('Опыт, месяцев', { exact: true }).fill('0');
  await page.getByLabel('Описание', { exact: true }).fill('Synthetic description');
  await page.getByLabel('Ставка кандидата за час', { exact: true }).fill('2500.50');
  await page.getByLabel('Валюта', { exact: true }).fill('RUB');
  await page.getByLabel('Доступность', { exact: true }).selectOption('AVAILABLE');
  await page.getByLabel('CV', { exact: true }).setInputFiles({ name: 'synthetic.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 synthetic') });
}

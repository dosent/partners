export const partnerProfileContentDefaults = {
  'portal.actions.logout': 'Выйти',
  'portal.actions.loggingOut': 'Выходим…',
  'portal.errors.logout': 'Не удалось выйти из кабинета. Попробуйте ещё раз.',
  'portal.profile.navigation': 'Профиль',
  'portal.profile.title': 'Профиль',
  'portal.profile.description': 'Контактные данные и настройки вашего аккаунта.',
  'portal.profile.settings.title': 'Контактные данные и настройки',
  'portal.profile.locale.label': 'Язык интерфейса',
  'portal.profile.locale.RU': 'Русский',
  'portal.profile.locale.KZ': 'Қазақша',
  'portal.profile.locale.EN': 'English',
  'portal.profile.notifications.title': 'Уведомления',
  'portal.profile.notifications.email': 'По электронной почте',
  'portal.profile.notifications.inApp': 'В кабинете',
  'portal.profile.actions.save': 'Сохранить изменения',
  'portal.profile.actions.saving': 'Сохранение…',
  'portal.profile.status.saved': 'Изменения сохранены.',
  'portal.profile.errors.save': 'Не удалось сохранить настройки. Попробуйте ещё раз.',
  'portal.profile.loading': 'Загружаем профиль…',
  'portal.profile.errors.load': 'Не удалось загрузить профиль. Попробуйте ещё раз.',
  'portal.profile.errors.localeUnavailable': 'Для выбранного языка пока нет опубликованного содержимого.',
};

export function enrichLegacyRussianRegistrationContent(content, requestedLocale) {
  if (requestedLocale?.toUpperCase() !== 'RU' || content?.locale?.toUpperCase() !== 'RU') return content;

  return {
    ...content,
    strings: { ...partnerProfileContentDefaults, ...(content.strings ?? {}) },
  };
}

import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowClockwise, ArrowDown, ArrowUp, FloppyDisk, Globe, UploadSimple } from '@phosphor-icons/react';
import { getRegistrationContentAdmin, publishRegistrationContent, registrationContentLocale, saveRegistrationContentDraft } from '../api/registrationContentAdminApi';
import './RegistrationContentPage.css';

const offerTypes = [
  { id: 'INDIVIDUAL', label: 'Физическое лицо' },
  { id: 'SOLE_PROPRIETOR', label: 'ИП' },
  { id: 'LEGAL_ENTITY', label: 'Юридическое лицо' },
];
const emptyOffer = () => ({ version: '', title: '', body: '' });
const requiredKeys = [
  'registration.title', 'registration.types.individual', 'registration.types.legalEntity',
  'registration.types.soleProprietor', 'registration.fields.fullName', 'registration.fields.email',
  'registration.fields.phone', 'registration.fields.country', 'registration.countries.RU', 'registration.countries.KZ', 'registration.fields.region',
  'registration.fields.companyName', 'registration.fields.taxIdentifier', 'registration.consent.offer',
  'registration.consent.privacy', 'registration.actions.submit', 'registration.status.manualReview',
  'registration.ui.loading', 'registration.auth.title', 'registration.auth.info', 'registration.auth.action',
  'registration.auth.back', 'registration.errors.conflict', 'registration.errors.unavailable',
  'registration.errors.sessionExpired', 'registration.errors.generic', 'registration.errors.taxIdentifier',
  'registration.status.PENDING_REVIEW', 'registration.status.MANUAL_REVIEW',
  'registration.status.CHANGES_REQUESTED', 'registration.status.REJECTED', 'registration.status.APPROVED',
  'registration.status.approvedInfo', 'registration.status.pendingInfo', 'registration.status.resubmitInfo',
  'registration.actions.resubmit', 'registration.actions.retry', 'registration.actions.refresh',
  'registration.actions.submitProgress', 'registration.documents.published', 'registration.documents.revision',
  'portal.title', 'portal.status.approved', 'portal.section.overview', 'portal.section.leads',
  'portal.section.vacancies', 'portal.section.finance', 'portal.section.documents',
  'portal.welcome', 'portal.emptySection',
];
const labels = {
  'registration.title': 'Заголовок регистрации',
  'registration.types.individual': 'Тип партнёра: физическое лицо',
  'registration.types.legalEntity': 'Тип партнёра: юридическое лицо',
  'registration.types.soleProprietor': 'Тип партнёра: индивидуальный предприниматель',
  'registration.fields.fullName': 'Поле: ФИО', 'registration.fields.email': 'Поле: email',
  'registration.fields.phone': 'Поле: телефон', 'registration.fields.country': 'Поле: страна',
  'registration.countries.RU': 'Название страны: Россия', 'registration.countries.KZ': 'Название страны: Казахстан',
  'registration.fields.region': 'Поле: регион', 'registration.fields.companyName': 'Поле: организация',
  'registration.fields.taxIdentifier': 'Поле: ИНН / БИН', 'registration.consent.offer': 'Подпись согласия с офертой',
  'registration.consent.privacy': 'Подпись согласия на обработку данных',
  'registration.actions.submit': 'Кнопка отправки', 'registration.status.manualReview': 'Статус ручной проверки',
  'registration.ui.loading': 'Состояние загрузки',
  'registration.auth.title': 'Заголовок предложения войти',
  'registration.auth.info': 'Описание входа',
  'registration.auth.action': 'Кнопка входа',
  'registration.auth.back': 'Кнопка возврата после входа',
  'registration.errors.conflict': 'Ошибка: конфликт изменения заявки',
  'registration.errors.unavailable': 'Ошибка: контент регистрации недоступен',
  'registration.errors.sessionExpired': 'Ошибка: сессия истекла',
  'registration.errors.generic': 'Общая ошибка формы',
  'registration.errors.taxIdentifier': 'Ошибка формата ИНН / БИН',
  'registration.status.PENDING_REVIEW': 'Статус: ожидает проверки',
  'registration.status.MANUAL_REVIEW': 'Статус: ручная проверка',
  'registration.status.CHANGES_REQUESTED': 'Статус: запрошены изменения',
  'registration.status.REJECTED': 'Статус: отклонено',
  'registration.status.APPROVED': 'Статус: одобрено',
  'registration.status.approvedInfo': 'Описание статуса одобрения',
  'registration.status.pendingInfo': 'Описание ожидания проверки',
  'registration.status.resubmitInfo': 'Инструкция повторной отправки',
  'registration.actions.resubmit': 'Кнопка повторной отправки',
  'registration.actions.retry': 'Кнопка повтора',
  'registration.actions.refresh': 'Кнопка обновления',
  'registration.actions.submitProgress': 'Подпись отправки в процессе',
  'registration.documents.published': 'Метка версии опубликованных документов',
  'registration.documents.revision': 'Метка редакции документов',
  'portal.title': 'Название личного кабинета', 'portal.status.approved': 'Метка одобренного доступа',
  'portal.section.overview': 'Раздел кабинета: обзор', 'portal.section.leads': 'Раздел кабинета: лиды',
  'portal.section.vacancies': 'Раздел кабинета: заявки на специалистов', 'portal.section.finance': 'Раздел кабинета: финансы',
  'portal.section.documents': 'Раздел кабинета: документы', 'portal.welcome': 'Пустое состояние обзора',
  'portal.emptySection': 'Пустое состояние раздела',
};
const defaultStrings = {
  'registration.title': 'Регистрация партнёра',
  'registration.types.individual': 'Физическое лицо',
  'registration.types.legalEntity': 'Юридическое лицо',
  'registration.types.soleProprietor': 'Индивидуальный предприниматель',
  'registration.fields.fullName': 'Фамилия, имя и отчество',
  'registration.fields.email': 'Электронная почта',
  'registration.fields.phone': 'Телефон',
  'registration.fields.country': 'Страна',
  'registration.fields.region': 'Регион',
  'registration.fields.companyName': 'Наименование организации',
  'registration.fields.taxIdentifier': 'ИНН / БИН',
  'registration.consent.offer': 'Я принимаю условия партнёрской оферты',
  'registration.consent.privacy': 'Я согласен на обработку персональных данных',
  'registration.actions.submit': 'Отправить заявку',
  'registration.countries.RU': 'Россия',
  'registration.countries.KZ': 'Казахстан',
  'registration.status.manualReview': 'Заявка будет рассмотрена вручную',
  'registration.ui.loading': 'Загружаем форму регистрации…',
  'registration.auth.title': 'Войдите через Яндекс ID',
  'registration.auth.info': 'Для подачи заявки войдите через Яндекс ID. Форма станет доступна после загрузки опубликованных документов.',
  'registration.auth.action': 'Войти через Яндекс ID',
  'registration.auth.back': 'Вернуться на страницу партнёрской программы',
  'registration.errors.conflict': 'Документы обновились. Проверьте актуальную редакцию и подтвердите согласия ещё раз.',
  'registration.errors.unavailable': 'Регистрация временно недоступна. Попробуйте позже.',
  'registration.errors.sessionExpired': 'Сессия входа завершилась. Войдите через Яндекс ID ещё раз.',
  'registration.errors.generic': 'Не удалось загрузить форму. Попробуйте ещё раз.',
  'registration.errors.taxIdentifier': 'Проверьте ИНН / БИН.',
  'registration.status.PENDING_REVIEW': 'Заявка ожидает проверки',
  'registration.status.MANUAL_REVIEW': 'Заявка на ручной проверке',
  'registration.status.CHANGES_REQUESTED': 'Требуются изменения в заявке',
  'registration.status.REJECTED': 'Заявка отклонена',
  'registration.status.APPROVED': 'Заявка одобрена',
  'registration.status.approvedInfo': 'Заявка одобрена. Доступ в кабинет партнёра открыт.',
  'registration.status.pendingInfo': 'Заявка отправлена и ожидает проверки.',
  'registration.status.resubmitInfo': 'Обновите данные заявки и отправьте её повторно.',
  'registration.actions.resubmit': 'Отправить повторно',
  'registration.actions.retry': 'Повторить',
  'registration.actions.refresh': 'Обновить статус',
  'registration.actions.submitProgress': 'Отправляем заявку…',
  'registration.documents.published': 'Опубликовано',
  'registration.documents.revision': 'Редакция',
  'portal.title': 'Кабинет партнёра',
  'portal.status.approved': 'Доступ одобрен',
  'portal.section.overview': 'Обзор',
  'portal.section.leads': 'Лиды',
  'portal.section.vacancies': 'Заявки на специалистов',
  'portal.section.finance': 'Финансы',
  'portal.section.documents': 'Документы',
  'portal.welcome': 'Добро пожаловать в партнёрский портал SimbirSoft.',
  'portal.emptySection': 'В этом разделе пока нет данных.',
};
const defaultOffers = {
  INDIVIDUAL: { version: '1.0', title: 'Партнёрская оферта для физического лица' },
  SOLE_PROPRIETOR: { version: '1.0', title: 'Партнёрская оферта для индивидуального предпринимателя' },
  LEGAL_ENTITY: { version: '1.0', title: 'Партнёрская оферта для юридического лица' },
};
const defaultMetadata = { privacyNoticeVersion: '1.0', privacyNoticeTitle: 'Согласие на обработку персональных данных', logoAssetKey: 'simbirsoft' };
const emptyValue = () => ({ strings: Object.fromEntries(requiredKeys.map(key => [key, ''])), offers: Object.fromEntries(offerTypes.map(type => [type.id, emptyOffer()])), privacyNoticeVersion: '', privacyNoticeTitle: '', privacyNoticeBody: '', logoAssetKey: '' });
const useDefaultWhenEmpty = (value, fallback) => value == null || (typeof value === 'string' && !value.trim()) ? fallback : value;
function normalizeDraft(value = {}) {
  value ??= {};
  const empty = emptyValue();
  const strings = { ...empty.strings, ...(value.strings ?? {}) };
  for (const [key, fallback] of Object.entries(defaultStrings)) strings[key] = useDefaultWhenEmpty(strings[key], fallback);

  const offers = { ...empty.offers, ...(value.offers ?? {}) };
  for (const [type, defaults] of Object.entries(defaultOffers)) {
    const offer = { ...emptyOffer(), ...(offers[type] ?? {}) };
    for (const [field, fallback] of Object.entries(defaults)) offer[field] = useDefaultWhenEmpty(offer[field], fallback);
    offers[type] = offer;
  }

  const draft = { ...empty, ...value, strings, offers };
  for (const [field, fallback] of Object.entries(defaultMetadata)) draft[field] = useDefaultWhenEmpty(draft[field], fallback);
  return draft;
}
const unwrap = result => result?.data ?? result;

export function RegistrationContentPage({ session, onBack, onLogout }) {
  const locale = registrationContentLocale;
  const [state, setState] = useState(null);
  const [draft, setDraft] = useState(() => normalizeDraft());
  const [mode, setMode] = useState('edit');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [missing, setMissing] = useState([]);
  const importRef = useRef(null);
  const canManage = (session?.permissions ?? []).includes('localization.manage');
  const localeInfo = { id: 'ru', label: 'Русский', brand: 'SimbirSoft' };
  const published = state?.published ?? null;
  const extraKeys = useMemo(() => Object.keys(draft.strings ?? {}).filter(key => !requiredKeys.includes(key)).sort(), [draft.strings]);

  useEffect(() => { loadLocale(locale); }, [locale]);

  async function loadLocale(target, successMessage = '') {
    setBusy(true); setError(''); setNotice(successMessage); setMissing([]);
    try {
      const response = unwrap(await getRegistrationContentAdmin(target));
      setState(response);
      setDraft(normalizeDraft(response.draft));
      setMode('edit');
    } catch (requestError) {
      setState(null); setError(requestError.message);
    } finally { setBusy(false); }
  }

  function updateField(field, value) { setDraft(current => ({ ...current, [field]: value })); }
  function updateOffer(type, field, value) { setDraft(current => ({ ...current, offers: { ...current.offers, [type]: { ...current.offers[type], [field]: value } } })); }
  function updateString(key, value) { setDraft(current => ({ ...current, strings: { ...current.strings, [key]: value } })); }

  async function saveDraft(event) {
    event?.preventDefault();
    setBusy(true); setError(''); setNotice(''); setMissing([]);
    try {
      const normalizedDraft = normalizeDraft(draft);
      const response = unwrap(await saveRegistrationContentDraft(locale, {
        expectedDraftVersion: state?.draftVersion ?? 0,
        strings: normalizedDraft.strings,
        offers: normalizedDraft.offers,
        privacyNoticeVersion: normalizedDraft.privacyNoticeVersion,
        privacyNoticeTitle: normalizedDraft.privacyNoticeTitle,
        privacyNoticeBody: normalizedDraft.privacyNoticeBody,
        logoAssetKey: normalizedDraft.logoAssetKey,
      }));
      setState(response);
      setDraft(normalizeDraft(response.draft));
      setNotice('Черновик сохранён.');
    } catch (requestError) {
      setError(requestError.status === 409 ? 'Черновик изменён другим администратором. Обновите данные перед сохранением.' : requestError.message);
    } finally { setBusy(false); }
  }

  async function publish() {
    setBusy(true); setError(''); setNotice(''); setMissing([]);
    try {
      await publishRegistrationContent(locale, state?.draftVersion ?? 0);
      await loadLocale(locale, 'Новая версия опубликована.');
      setMode('published');
    } catch (requestError) {
      if (requestError.status === 422) {
        const fieldErrors = Array.isArray(requestError.fields) ? requestError.fields.map(item => item.field ?? item.name ?? item) : [];
        setMissing(fieldErrors);
        setError(fieldErrors.length ? 'Публикация невозможна: заполните обязательные материалы ниже.' : requestError.message);
      } else if (requestError.status === 409) setError('Черновик изменён другим администратором. Обновите данные перед публикацией.');
      else setError(requestError.message);
    } finally { setBusy(false); }
  }

  function exportJson(kind) {
    const content = kind === 'published' ? published?.content : draft;
    if (!content) return;
    const output = { locale, ...(kind === 'draft' ? { expectedDraftVersion: state?.draftVersion ?? 0 } : { contentVersion: published.contentVersion, contentSha256: published.contentSha256 }), content };
    const blob = new Blob([`${JSON.stringify(output, null, 2)}\n`], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `registration-${locale}-${kind}.json`; anchor.click(); URL.revokeObjectURL(url);
  }

  async function importJson(event) {
    const file = event.target.files?.[0]; event.target.value = '';
    if (!file) return;
    setError(''); setNotice('');
    try {
      const parsed = JSON.parse(await file.text());
      const content = parsed.content ?? parsed;
      if (parsed.locale && parsed.locale !== locale) throw new Error(`Файл содержит локаль ${parsed.locale}, сейчас выбрана ${locale}.`);
      if (!content || typeof content !== 'object' || !content.strings || typeof content.strings !== 'object') throw new Error('В JSON не найден объект content.strings.');
      setDraft(normalizeDraft(content));
      setNotice('Данные загружены в форму. Сохраните черновик, чтобы применить изменения.');
      setMode('edit');
    } catch (parseError) { setError(parseError instanceof SyntaxError ? 'Не удалось прочитать JSON-файл.' : parseError.message); }
  }

  if (!canManage) return <div className="registration-content-shell"><section className="registration-content-card"><p className="registration-content-kicker">Администрирование</p><h1>Недостаточно прав</h1><p>Для управления локализованным контентом требуется разрешение localization.manage.</p><button type="button" className="rc-button rc-button-secondary" onClick={onBack}>Вернуться в кабинет</button></section></div>;

  return <div className="registration-content-shell">
    <header className="registration-content-header"><div><p className="registration-content-kicker">Настройки портала</p><h1>Контент регистрации</h1><p>Редактируйте русские подписи и тексты оферт для каждого типа заявителя.</p></div><div className="rc-header-actions"><button type="button" className="rc-button rc-button-secondary" onClick={onLogout}>Выйти</button></div></header>
    <div className="registration-content-toolbar">
      <div className="rc-view-switch"><button type="button" className={mode === 'edit' ? 'is-active' : ''} onClick={() => setMode('edit')}>Черновик</button><button type="button" className={mode === 'published' ? 'is-active' : ''} onClick={() => setMode('published')} disabled={!published}>Опубликовано</button></div>
    </div>
    {error && <div className="rc-alert rc-alert-error" role="alert"><strong>{error}</strong>{missing.length > 0 && <ul>{missing.map(item => <li key={item}>{item}</li>)}</ul>}</div>}
    {notice && <p className="rc-alert rc-alert-success" role="status">{notice}</p>}
    {busy && !state ? <div className="rc-loading">Загружаем содержимое…</div> : <>
      <section className="rc-publication-bar"><div><span className={`rc-status-dot ${published ? 'is-published' : ''}`}/><strong>{published ? `Опубликована версия ${published.contentVersion}` : 'Пока не опубликовано'}</strong>{published?.publishedAt && <small>{new Date(published.publishedAt).toLocaleString()}</small>}</div><button type="button" className="rc-button rc-button-secondary" onClick={() => loadLocale(locale)} disabled={busy}><ArrowClockwise size={17}/>Обновить</button></section>
      {mode === 'published' ? <PublishedView published={published} localeInfo={localeInfo} onExport={() => exportJson('published')}/> : <form onSubmit={saveDraft}>
        <section className="rc-panel"><div className="rc-panel-heading"><div><span className="rc-step">01</span><h2>Интерфейс и бренд</h2></div><small>Локаль {locale.toUpperCase()} · бренд {localeInfo.brand}</small></div>
          <label className="rc-field">Ключ логотипа<select value={draft.logoAssetKey} onChange={event => updateField('logoAssetKey', event.target.value)}><option value="">Не выбран — публикация недоступна</option><option value="simbirsoft">simbirsoft</option><option value="17soft">17soft</option></select><small>Здесь задаётся только ключ уже настроенного сервером ассета. Картинки загружать нельзя.</small></label>
          <div className="rc-fields-grid">{requiredKeys.map(key => <label className="rc-field" key={key}><span>{labels[key]} <code>{key}</code></span><input value={draft.strings?.[key] ?? ''} onChange={event => updateString(key, event.target.value)} maxLength={12000} /></label>)}</div>
          {extraKeys.length > 0 && <details className="rc-extra-keys"><summary>Дополнительные ключи ({extraKeys.length})</summary><div className="rc-fields-grid">{extraKeys.map(key => <label className="rc-field" key={key}><span><code>{key}</code></span><textarea value={draft.strings[key] ?? ''} onChange={event => updateString(key, event.target.value)} maxLength={12000} rows={2}/></label>)}</div></details>}
          <AddStringField onAdd={(key) => updateString(key, '')} existingKeys={Object.keys(draft.strings ?? {})}/>
        </section>
        <section className="rc-panel"><div className="rc-panel-heading"><div><span className="rc-step">02</span><h2>Оферты по типу заявителя</h2></div><small>Все три текста обязательны для публикации</small></div>{offerTypes.map(type => <div key={type.id}><h3>{type.label}</h3><div className="rc-fields-grid rc-document-meta"><label className="rc-field">Версия<input value={draft.offers?.[type.id]?.version ?? ''} onChange={event => updateOffer(type.id, 'version', event.target.value)} maxLength={100}/></label><label className="rc-field">Заголовок<input value={draft.offers?.[type.id]?.title ?? ''} onChange={event => updateOffer(type.id, 'title', event.target.value)} maxLength={1000}/></label></div><label className="rc-field">Текст утверждённой оферты<textarea value={draft.offers?.[type.id]?.body ?? ''} onChange={event => updateOffer(type.id, 'body', event.target.value)} rows={12} maxLength={500000} placeholder="Вставьте предоставленный и утверждённый юридический текст"/></label></div>)}</section>
        <section className="rc-panel"><div className="rc-panel-heading"><div><span className="rc-step">03</span><h2>Согласие на обработку персональных данных</h2></div><small>Вводит и утверждает уполномоченный сотрудник</small></div><div className="rc-fields-grid rc-document-meta"><label className="rc-field">Версия<input value={draft.privacyNoticeVersion ?? ''} onChange={event => updateField('privacyNoticeVersion', event.target.value)} maxLength={100}/></label><label className="rc-field">Заголовок<input value={draft.privacyNoticeTitle ?? ''} onChange={event => updateField('privacyNoticeTitle', event.target.value)} maxLength={1000}/></label></div><label className="rc-field">Текст утверждённого согласия<textarea value={draft.privacyNoticeBody ?? ''} onChange={event => updateField('privacyNoticeBody', event.target.value)} rows={12} maxLength={500000} placeholder="Вставьте предоставленный и утверждённый юридический текст"/></label></section>
        <footer className="rc-editor-footer"><div className="rc-footer-meta">Черновик v{state?.draftVersion ?? 0}{state?.draftUpdatedBy && <span> · изменён {state.draftUpdatedBy}</span>}</div><div className="rc-actions"><input ref={importRef} type="file" accept="application/json,.json" hidden onChange={importJson}/><button type="button" className="rc-button rc-button-secondary" onClick={() => importRef.current?.click()} disabled={busy}><ArrowUp size={17}/>Импорт JSON</button><button type="button" className="rc-button rc-button-secondary" onClick={() => exportJson('draft')} disabled={busy}><ArrowDown size={17}/>Экспорт JSON</button><button type="submit" className="rc-button rc-button-primary" disabled={busy}><FloppyDisk size={18}/>{busy ? 'Сохраняем…' : 'Сохранить черновик'}</button><button type="button" className="rc-button rc-button-publish" onClick={publish} disabled={busy}><UploadSimple size={18}/>{busy ? 'Обработка…' : 'Опубликовать'}</button></div></footer>
      </form>}
    </>}
    <button type="button" className="rc-back-link" onClick={onBack}>← Вернуться в кабинет</button>
  </div>;
}

function AddStringField({ onAdd, existingKeys }) {
  const [key, setKey] = useState('');
  function add(event) { event.preventDefault(); const value = key.trim(); if (!value || existingKeys.includes(value)) return; onAdd(value); setKey(''); }
  return <form className="rc-add-string" onSubmit={add}><label htmlFor="rc-new-key">Добавить ключ интерфейса</label><div><input id="rc-new-key" value={key} onChange={event => setKey(event.target.value)} maxLength={120} placeholder="например, registration.help.contact"/><button type="submit" className="rc-button rc-button-secondary" disabled={!key.trim() || existingKeys.includes(key.trim())}>Добавить строку</button></div></form>;
}

function PublishedView({ published, localeInfo, onExport }) {
  if (!published) return <section className="rc-panel rc-empty-published"><span className="rc-step"><Globe size={20}/></span><h2>Публикации пока нет</h2><p>Заполните и опубликуйте черновик. Пока у локали нет полной опубликованной версии, регистрация для неё останется закрыта.</p></section>;
  const content = published.content ?? {};
  return <section className="rc-panel rc-published-view"><div className="rc-panel-heading"><div><span className="rc-step">v{published.contentVersion}</span><h2>Опубликованный снимок</h2></div><button className="rc-button rc-button-secondary" type="button" onClick={onExport}><ArrowDown size={17}/>Экспорт JSON</button></div><dl className="rc-snapshot-meta"><div><dt>Локаль</dt><dd>{localeInfo.label} ({localeInfo.id.toUpperCase()})</dd></div><div><dt>Бренд</dt><dd>{localeInfo.brand}</dd></div><div><dt>Версия согласия</dt><dd>{content.privacyNoticeVersion || '—'}</dd></div><div><dt>Опубликовано</dt><dd>{published.publishedAt ? new Date(published.publishedAt).toLocaleString() : '—'}</dd></div><div><dt>Опубликовал</dt><dd>{published.publishedBy || '—'}</dd></div><div className="rc-checksum"><dt>SHA-256</dt><dd>{published.contentSha256 || '—'}</dd></div></dl><h3>Подписи интерфейса</h3><div className="rc-snapshot-strings">{Object.entries(content.strings ?? {}).map(([key, value]) => <div key={key}><code>{key}</code><span>{value || '—'}</span></div>)}</div>{offerTypes.map(type => <div key={type.id}><h3>Оферта: {type.label} · {content.offers?.[type.id]?.version || '—'}</h3><pre className="rc-legal-preview">{content.offers?.[type.id]?.body || 'Текст не заполнен'}</pre></div>)}<h3>Согласие · {content.privacyNoticeTitle || 'без заголовка'}</h3><pre className="rc-legal-preview">{content.privacyNoticeBody || 'Текст не заполнен'}</pre></section>;
}

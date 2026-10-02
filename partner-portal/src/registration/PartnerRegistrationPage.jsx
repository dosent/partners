import { useCallback, useEffect, useState } from 'react';
import { ArrowRight, Check, ShieldCheck } from '@phosphor-icons/react';
import { Field } from '../components';
import { getMyPartnerApplication, getPartnerSession, getRegistrationContent, submitPartnerApplication } from '../api/partnerRegistrationApi';

const registrationLocale = 'RU';
const fallbackStrings = {
  'registration.ui.loading': 'Загружаем форму регистрации…',
  'registration.auth.title': 'Войдите через Яндекс ID',
  'registration.auth.info': 'Для подачи заявки войдите через Яндекс ID. Форма станет доступна после загрузки опубликованных документов.',
  'registration.auth.action': 'Войти через Яндекс ID',
  'registration.auth.back': 'Вернуться на страницу партнёрской программы',
  'registration.errors.conflict': 'Документы обновились. Проверьте актуальную редакцию и подтвердите согласия ещё раз.',
  'registration.errors.unavailable': 'Регистрация временно недоступна. Попробуйте позже.',
  'registration.errors.sessionExpired': 'Сессия входа завершилась. Войдите через Яндекс ID ещё раз.',
  'registration.errors.generic': 'Не удалось загрузить форму. Попробуйте ещё раз.',
  'registration.actions.retry': 'Повторить',
};
const logoAssets = {
  simbirsoft: { src: '/assets/logo.svg', alt: 'SimbirSoft' },
  '17soft': { src: '/assets/17soft.svg', alt: '17Soft' },
};
const requiredRegistrationKeys = [
  'registration.title', 'registration.types.individual', 'registration.types.legalEntity',
  'registration.types.soleProprietor', 'registration.fields.fullName', 'registration.fields.email',
  'registration.fields.phone', 'registration.fields.country', 'registration.fields.region',
  'registration.fields.companyName', 'registration.fields.taxIdentifier', 'registration.consent.offer',
  'registration.consent.privacy', 'registration.actions.submit', 'registration.status.manualReview',
  'registration.countries.RU', 'registration.countries.KZ',
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

function getErrorKey(error) {
  if (error.status === 409) return 'registration.errors.conflict';
  if (error.status === 503) return 'registration.errors.unavailable';
  if (error.status === 401) return 'registration.errors.sessionExpired';
  if (error.code === 'INVALID_TAX_IDENTIFIER') return 'registration.errors.taxIdentifier';
  return 'registration.errors.generic';
}

function readCachedStrings(locale) {
  try {
    const parsed = JSON.parse(localStorage.getItem(`partnerRegistrationStrings:${locale}`) ?? 'null');
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function missingKeys(strings) {
  return requiredRegistrationKeys.filter(key => typeof strings?.[key] !== 'string' || !strings[key].trim());
}

export function PartnerRegistrationPage({ onBack }) {
  const locale = registrationLocale;
  const [session, setSession] = useState(null);
  const [content, setContent] = useState(null);
  const [application, setApplication] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorKey, setErrorKey] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [partnerType, setPartnerType] = useState('INDIVIDUAL');
  const reload = useCallback(async () => {
    setLoading(true);
    setErrorKey('');
    setFieldErrors({});
    setContent(null);
    try {
      let currentSession = null;
      try {
        currentSession = await getPartnerSession();
      } catch (requestError) {
        if (requestError.status !== 401) throw requestError;
      }
      setSession(currentSession);
      const ownApplication = currentSession ? await getMyPartnerApplication().catch(requestError => {
        if (requestError.status === 404) return null;
        throw requestError;
      }) : null;
      const published = await getRegistrationContent(locale);
      setContent(published);
      localStorage.setItem(`partnerRegistrationStrings:${locale}`, JSON.stringify(published.strings ?? {}));
      setApplication(ownApplication);
    } catch (requestError) {
      setErrorKey(getErrorKey(requestError));
    } finally {
      setLoading(false);
    }
  }, [locale]);

  useEffect(() => { reload(); }, [reload]);

  const submit = async event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const selectedOffer = content?.offers?.[partnerType];
    if (!selectedOffer?.version || !selectedOffer?.body || !selectedOffer?.sha256 || !content?.privacyNotice?.sha256) {
      setErrorKey('registration.errors.unavailable');
      return;
    }
    const hasBusinessData = partnerType !== 'INDIVIDUAL';
    const payload = {
      partnerType,
      fullName: String(form.get('fullName')).trim(),
      email: String(form.get('email')).trim(),
      phone: String(form.get('phone')).trim(),
      countryCode: String(form.get('countryCode')),
      region: String(form.get('region')).trim(),
      companyName: partnerType === 'LEGAL_ENTITY' ? String(form.get('companyName') ?? '').trim() : null,
      taxIdentifier: hasBusinessData ? String(form.get('taxIdentifier') ?? '').trim() : null,
      locale: content.locale,
      contentVersion: content.contentVersion,
      offerVersion: selectedOffer.version,
      offerSha256: selectedOffer.sha256,
      offerAccepted: form.get('offerAccepted') === 'on',
      privacyNoticeVersion: content.privacyNotice.version,
      privacyNoticeSha256: content.privacyNotice.sha256,
      privacyAccepted: form.get('privacyAccepted') === 'on',
    };
    setSubmitting(true);
    setErrorKey('');
    setFieldErrors({});
    try {
      const result = await submitPartnerApplication(payload, Boolean(application));
      setApplication(result);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (requestError) {
      setFieldErrors(Object.fromEntries((requestError.fields ?? []).map(item => [item.field, item.message])));
      setErrorKey(getErrorKey(requestError));
      if (requestError.status === 409 || requestError.status === 503) {
        try {
          const freshContent = await getRegistrationContent(locale);
          setContent(freshContent);
          localStorage.setItem(`partnerRegistrationStrings:${locale}`, JSON.stringify(freshContent.strings ?? {}));
        } catch { /* keep the localized error and current published text */ }
      }
    } finally {
      setSubmitting(false);
    }
  };

  const activeStrings = content?.strings ?? readCachedStrings(locale);
  const text = key => typeof activeStrings?.[key] === 'string' && activeStrings[key].trim()
    ? activeStrings[key]
    : fallbackStrings[key] ?? '';
  const translatedError = text(errorKey);
  if (loading) return <section className="registration-state"><p role="status">{text('registration.ui.loading')}</p></section>;
  if (content && missingKeys(content.strings).length) return <section className="registration-state"><p role="alert">Регистрация временно недоступна. Попробуйте позже.</p></section>;
  if (!session) return <section className="registration-state">
    <span className="attention-icon blue"><ShieldCheck size={27}/></span>
    {text('registration.auth.title') && <h2>{text('registration.auth.title')}</h2>}
    {text('registration.auth.info') && <p>{text('registration.auth.info')}</p>}
    {translatedError && <p className="registration-error" role="alert">{translatedError}</p>}
    {text('registration.auth.action') && <a className="primary registration-login" href="/api/v1/auth/yandex/start" onClick={() => sessionStorage.setItem('partnerRegistrationAfterLogin', 'true')}>{text('registration.auth.action')} <ArrowRight size={18}/></a>}
    {onBack && text('registration.auth.back') && <button className="text-button" onClick={onBack}>{text('registration.auth.back')}</button>}
  </section>;

  if (!content && errorKey) return <section className="registration-state">
    {translatedError && <p role="alert">{translatedError}</p>}
    {text('registration.actions.retry') && <button className="secondary" onClick={reload}>{text('registration.actions.retry')}</button>}
  </section>;

  if (application && !['CHANGES_REQUESTED', 'REJECTED'].includes(application.status)) {
    return <section className="registration-success">
    <span className="attention-icon green"><Check size={28}/></span>
    <h2>{text(`registration.status.${application.status}`)}</h2>
    <p>{application.latestDecisionReason || text(application.status === 'APPROVED' ? 'registration.status.approvedInfo' : 'registration.status.pendingInfo')}</p>
    {translatedError && <p className="registration-error" role="alert">{translatedError}</p>}
    {text('registration.actions.refresh') && <button className="secondary" onClick={reload}>{text('registration.actions.refresh')}</button>}
  </section>;
  }

  if (!content) return <section className="registration-state">{translatedError && <p role="alert">{translatedError}</p>}{text('registration.actions.retry') && <button className="secondary" onClick={reload}>{text('registration.actions.retry')}</button>}</section>;

  const strings = content.strings ?? {};
  if (missingKeys(strings).length) return <section className="registration-state"><p role="alert">Регистрация временно недоступна. Попробуйте позже.</p></section>;
  const selectedOffer = content.offers?.[partnerType];
  if (!selectedOffer?.version || !selectedOffer?.body || !selectedOffer?.sha256 || !content.privacyNotice?.sha256) return <section className="registration-state"><p role="alert">Регистрация временно недоступна. Тексты документов ещё не опубликованы.</p></section>;
  const label = key => strings[key];
  const canResubmit = Boolean(application);
  const brand = logoAssets[content.logoAssetKey];
  return <>
    {errorKey && translatedError && <p className="registration-error" role="alert">{translatedError}</p>}
    {application && <div className="registration-decision" role="status"><strong>{text(`registration.status.${application.status}`)}</strong>
      {application.latestDecisionReason && <p>{application.latestDecisionReason}</p>}
      <p>{text('registration.status.resubmitInfo')}</p></div>}
    {brand && <div className="registration-brand"><img src={brand.src} alt={brand.alt}/></div>}
    <div className="registration-layout">
      <section className="registration-card">
        <form onSubmit={submit}>
          <h2>{label('registration.title')}</h2>
          <div className="partner-types" role="group" aria-label={label('registration.title')}>
            {[
              ['INDIVIDUAL', 'registration.types.individual'],
              ['LEGAL_ENTITY', 'registration.types.legalEntity'],
              ['SOLE_PROPRIETOR', 'registration.types.soleProprietor'],
            ].map(([value, key]) => <button type="button" key={value} className={`partner-type ${partnerType === value ? 'selected' : ''}`} aria-pressed={partnerType === value} onClick={() => setPartnerType(value)}>{label(key)}</button>)}
          </div>
          <div className="form-grid registration-fields">
            <Field label={`${label('registration.fields.fullName')} *`}><input name="fullName" autoComplete="name" required maxLength={120}/></Field>
            <Field label={`${label('registration.fields.email')} *`}><input name="email" type="email" autoComplete="email" required maxLength={255}/></Field>
            <Field label={`${label('registration.fields.phone')} *`}><input name="phone" type="tel" autoComplete="tel" required maxLength={64}/></Field>
            <Field label={`${label('registration.fields.country')} *`}><select name="countryCode" defaultValue="RU" required><option value="RU">{label('registration.countries.RU')}</option><option value="KZ">{label('registration.countries.KZ')}</option></select></Field>
            <Field label={`${label('registration.fields.region')} *`}><input name="region" autoComplete="address-level1" required maxLength={100}/></Field>
            {partnerType !== 'INDIVIDUAL' && <>
              {partnerType === 'LEGAL_ENTITY' && <Field label={`${label('registration.fields.companyName')} *`}><input name="companyName" autoComplete="organization" required maxLength={160}/></Field>}
              <Field label={`${label('registration.fields.taxIdentifier')} *`}><input name="taxIdentifier" inputMode="numeric" required maxLength={64} aria-invalid={Boolean(fieldErrors.taxIdentifier)} aria-describedby={fieldErrors.taxIdentifier ? 'taxIdentifier-error' : undefined}/>{fieldErrors.taxIdentifier && <span id="taxIdentifier-error" className="field-error" role="alert">{label('registration.errors.taxIdentifier')}</span>}</Field>
            </>}
          </div>
          <h2 className="registration-section-title">{selectedOffer.title}</h2>
          <label className="consent"><input key={`${partnerType}:${selectedOffer.sha256}`} name="offerAccepted" type="checkbox" required/><span>{label('registration.consent.offer')}</span></label>
          <label className="consent"><input key={content.privacyNotice.sha256} name="privacyAccepted" type="checkbox" required/><span>{label('registration.consent.privacy')}</span></label>
          <button className="primary registration-submit" type="submit" disabled={submitting}>{submitting ? label('registration.actions.submitProgress') : canResubmit ? label('registration.actions.resubmit') : label('registration.actions.submit')}</button>
          <p className="form-note">{label('registration.status.manualReview')} · {label('registration.documents.revision')}: {content.contentVersion}.</p>
        </form>
      </section>
      <section className="offer-card" id="offer">
        <div className="offer-heading"><div><span className="eyebrow">{selectedOffer.version}</span><h2>{selectedOffer.title}</h2></div><span className="badge">{label('registration.documents.published')}</span></div>
        <article className="offer-content">{selectedOffer.body}</article>
        <div className="offer-heading privacy-heading"><div><span className="eyebrow">{content.privacyNotice.version}</span><h2>{content.privacyNotice.title}</h2></div></div>
        <article className="offer-content">{content.privacyNotice.body}</article>
      </section>
    </div>
  </>;
}

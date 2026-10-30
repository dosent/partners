import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, ArrowClockwise, Plus } from '@phosphor-icons/react';
import { getRegistrationContent } from '../api/partnerRegistrationApi';
import { createCandidate, downloadCandidateCv, getCandidate, getCandidates, replaceCandidateCv, updateCandidate } from '../api/staffCandidatesApi.js';
import { createRequestId } from '../utils/requestId.js';
import { CandidateForm, validCandidateFile } from './CandidateForm';
import { candidateText } from './candidateContent.js';
import { StaffFrame } from './StaffLoginPage';
import './StaffCandidatesPage.css';

export function CandidateNavigation({ onOpen }) {
  const [title, setTitle] = useState('');
  useEffect(() => {
    let active = true;
    getRegistrationContent('RU').then(content => { if (active) setTitle(content.strings?.['candidates.title'] || ''); }).catch(() => {});
    return () => { active = false; };
  }, []);
  return title ? <button className="primary" onClick={onOpen}>{title}<ArrowRight size={18}/></button> : null;
}

function pendingKey(username) { return `candidate-create:${username}`; }
function readPending(username) {
  try {
    const value = JSON.parse(sessionStorage.getItem(pendingKey(username)));
    return value?.profile && value?.fileMetadata && /^[0-9a-f-]{36}$/.test(value.idempotencyKey) ? value : null;
  } catch { return null; }
}

export function StaffCandidatesPage({ session, onBack, onLogout }) {
  const canManage = (session.permissions ?? []).includes('candidate.manage');
  const [locale, setLocale] = useState('RU');
  const [content, setContent] = useState(null);
  const [contentLoading, setContentLoading] = useState(true);
  const [contentFailed, setContentFailed] = useState(false);
  const [contentAttempt, setContentAttempt] = useState(0);
  const text = candidateText(content);
  const [pending, setPending] = useState(() => readPending(session.username));
  const [query, setQuery] = useState('');
  const [availability, setAvailability] = useState('AVAILABLE');
  const [page, setPage] = useState(0);
  const [queue, setQueue] = useState(null);
  const [refresh, setRefresh] = useState(0);
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState(pending ? 'create' : 'list');
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState([]);
  const [notice, setNotice] = useState('');
  const [uncertain, setUncertain] = useState(Boolean(pending));
  const [replacement, setReplacement] = useState(null);
  const requestId = useRef(0);
  const detailsRequest = useRef(0);
  const mutationLock = useRef(false);
  const createAttempt = useRef(pending);
  const currentText = useRef(text);
  currentText.current = text;

  useEffect(() => {
    let active = true;
    setContentLoading(true); setContentFailed(false); setContent(null);
    getRegistrationContent(locale).then(value => { if (active) setContent(value); })
      .catch(() => { if (active) setContentFailed(true); })
      .finally(() => { if (active) setContentLoading(false); });
    return () => { active = false; };
  }, [locale, contentAttempt]);

  const ready = Boolean(text);
  useEffect(() => {
    if (!canManage || !ready || view !== 'list') return;
    const id = ++requestId.current;
    setLoading(true); setError('');
    const timer = setTimeout(async () => {
      try {
        const result = await getCandidates({ q: query, availability, page, size: 20 });
        if (id === requestId.current) setQueue(result);
      } catch { if (id === requestId.current) setError(currentText.current.error); }
      finally { if (id === requestId.current) setLoading(false); }
    }, query ? 180 : 0);
    return () => { clearTimeout(timer); requestId.current++; };
  }, [query, availability, page, refresh, canManage, ready, view]);

  function failureMessage(failure) {
    if (failure.code === 'CANDIDATE_CREATE_REJECTED' && failure.status !== 422) return text.invalidFile;
    if (failure.code === 'EXTERNAL_KEY_CONFLICT') return text.externalKeyConflict;
    if (failure.status === 409) return text.conflict;
    if (failure.status === 413) return text.invalidFile;
    if (failure.status === 422) return text.rejectedFile;
    if (failure.status === 503) return text.serviceUnavailable;
    if (failure.status === 401 || failure.status === 403) return text.forbidden;
    return text.error;
  }
  function clearFeedback() { setError(''); setNotice(''); setFieldErrors([]); }
  function backToList() { clearFeedback(); setView('list'); setSelected(null); setReplacement(null); setRefresh(value => value + 1); }
  async function openCandidate(id) {
    const token = ++detailsRequest.current;
    clearFeedback(); setBusy(true);
    try {
      const value = await getCandidate(id);
      if (token === detailsRequest.current) { setSelected(value); setView('details'); setReplacement(null); }
    } catch { if (token === detailsRequest.current) setError(text.error); }
    finally { if (token === detailsRequest.current) setBusy(false); }
  }
  function newCandidate() {
    clearFeedback(); createAttempt.current = null; setUncertain(false); setSelected(null); setView('create');
  }
  async function saveCandidate(command) {
    if (mutationLock.current) return;
    const creating = view === 'create';
    if (creating && createAttempt.current && !createAttempt.current.cv) {
      const { fileMetadata } = createAttempt.current;
      if (!validCandidateFile(command.cv) || command.cv.name !== fileMetadata.name || command.cv.size !== fileMetadata.size) {
        setError(`${text.missingFile} ${fileMetadata.name}`); return;
      }
      createAttempt.current.cv = command.cv;
    }
    mutationLock.current = true; setBusy(true); clearFeedback();
    if (creating && !createAttempt.current) {
      createAttempt.current = { ...command, idempotencyKey: createRequestId(), fileMetadata: { name: command.cv.name, size: command.cv.size } };
      try {
        const { profile, idempotencyKey, fileMetadata } = createAttempt.current;
        sessionStorage.setItem(pendingKey(session.username), JSON.stringify({ profile, idempotencyKey, fileMetadata }));
      } catch { /* Live retry still retains the original request when browser storage is unavailable. */ }
    }
    try {
      const result = creating ? await createCandidate(createAttempt.current)
        : await updateCandidate(selected.id, { profile: command.profile, expectedVersion: selected.version });
      createAttempt.current = null; setUncertain(false); setPending(null);
      try { sessionStorage.removeItem(pendingKey(session.username)); } catch {}
      setSelected(result); setView('details'); setNotice(text.saved);
    } catch (failure) {
      setFieldErrors(failure.fieldErrors ?? []);
      setError(failureMessage(failure));
      if (creating) {
        if (failure.code === 'CANDIDATE_CREATE_REJECTED') {
          // The server proves this exact reservation rolled back; the retained fields may safely be edited.
          createAttempt.current = null; setUncertain(false); setPending(null);
          try { sessionStorage.removeItem(pendingKey(session.username)); } catch {}
        } else if (uncertain && failure.status === 409 && failure.code !== 'EXTERNAL_KEY_CONFLICT') {
          // A different reselected file must never turn an unresolved original create into a new create.
          createAttempt.current.cv = null; setUncertain(true);
          setError(`${text.missingFile} ${createAttempt.current.fileMetadata.name}`);
        } else if (uncertain || !failure.status || failure.status >= 500) setUncertain(true);
        else {
          createAttempt.current = null; setUncertain(false);
          try { sessionStorage.removeItem(pendingKey(session.username)); } catch {}
        }
      }
    } finally { mutationLock.current = false; setBusy(false); }
  }
  async function replaceCv(event) {
    event.preventDefault();
    if (mutationLock.current) return;
    clearFeedback();
    if (!validCandidateFile(replacement)) { setError(text.invalidFile); return; }
    mutationLock.current = true; setBusy(true);
    try {
      setSelected(await replaceCandidateCv(selected.id, { file: replacement, expectedVersion: selected.version }));
      setReplacement(null); setNotice(text.saved);
    } catch (failure) { setError(failureMessage(failure)); }
    finally { mutationLock.current = false; setBusy(false); }
  }
  async function download() {
    if (mutationLock.current) return;
    mutationLock.current = true; setBusy(true); clearFeedback();
    try {
      const blob = await downloadCandidateCv(selected.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = selected.cv.fileName;
      document.body.appendChild(anchor); anchor.click(); anchor.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { setError(text.error); }
    finally { mutationLock.current = false; setBusy(false); }
  }
  const date = value => value ? new Intl.DateTimeFormat(({ RU: 'ru-RU', KZ: 'kk-KZ', EN: 'en-US' })[locale], { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—';
  const label = state => state === 'AVAILABLE' ? text.available : text.unavailable;
  function rate(value) { return `${value.amount} ${value.currency} ${text.perHour}`; }

  if (contentLoading) return <StaffFrame><div className="staff-candidates" role="status" aria-busy="true"/></StaffFrame>;
  if (contentFailed || !text) return <StaffFrame><section className="staff-card staff-state-card">
    <p role="alert">{content?.strings?.['portal.profile.errors.localeUnavailable'] || 'Для выбранного языка нет опубликованного содержимого.'}</p>
    <label className="candidate-locale">{content?.strings?.['portal.profile.locale.label'] || 'RU / KZ / EN'}<select value={locale} onChange={event => setLocale(event.target.value)}><option value="RU">RU</option><option value="KZ">KZ</option><option value="EN">EN</option></select></label>
    <div className="candidate-actions"><button className="secondary" onClick={() => setContentAttempt(value => value + 1)}>{content?.strings?.['registration.actions.retry'] || 'Повторить'}</button>
      <button className="secondary" onClick={onBack}>Вернуться в кабинет</button></div>
  </section></StaffFrame>;
  if (!canManage) return <StaffFrame><section className="staff-card staff-state-card"><h1>{text.forbidden}</h1><button className="secondary" onClick={onBack}>{text.home}</button></section></StaffFrame>;

  return <StaffFrame><section className="staff-candidates">
    <header className="candidate-header"><h1>{view === 'create' ? text.create : view === 'edit' ? text.edit : text.title}</h1>
      <div className="candidate-actions">
        {view === 'list' && <button className="primary" onClick={newCandidate} disabled={busy}><Plus size={18}/>{text.create}</button>}
        <label className="candidate-locale">{text.locale}<select value={locale} disabled={view !== 'list' || busy} onChange={event => { setLocale(event.target.value); setPage(0); clearFeedback(); }}><option value="RU">RU</option><option value="KZ">KZ</option><option value="EN">EN</option></select></label>
        <button className="secondary" onClick={onLogout} disabled={busy}>{text.logout}</button>
      </div>
    </header>
    {error && <p className="staff-error" role="alert">{error}</p>}
    {notice && <p className="staff-success" role="status">{notice}</p>}
    {(view === 'create' || view === 'edit') && <CandidateForm key={view === 'create' ? 'create' : selected.id}
      initialValue={view === 'edit' ? selected : pending?.profile} text={text} busy={busy} fieldErrors={fieldErrors} uncertain={uncertain} retryNeedsFile={uncertain && !createAttempt.current?.cv}
      onSave={saveCandidate} onCancel={view === 'edit' ? () => { clearFeedback(); setView('details'); } : backToList}/>}
    {view === 'list' && <>
      <div className="candidate-toolbar"><label className="candidate-search">{text.search}<input type="search" name="q" value={query} maxLength={200} onChange={event => { setQuery(event.target.value); setPage(0); }}/></label>
        <label>{text.filter}<select value={availability} onChange={event => { setAvailability(event.target.value); setPage(0); }}><option value="AVAILABLE">{text.available}</option><option value="UNAVAILABLE">{text.unavailable}</option><option value="">{text.all}</option></select></label>
        <button className="secondary" onClick={() => setRefresh(value => value + 1)} disabled={loading || busy}><ArrowClockwise size={17}/>{text.refresh}</button></div>
      <p className="candidate-note">{text.manual}</p>
      {loading && <p className="candidate-state" role="status">{text.loading}</p>}
      {!loading && !error && queue?.items.length === 0 && <p className="candidate-state">{text.empty}</p>}
      {!loading && queue && !error && <>
        <div className="candidate-list">{queue.items.map(item => <article className="candidate-row" key={item.id}>
          <div className="candidate-summary"><span className="staff-eyebrow">{item.code}</span><h2>{item.role}</h2>
            {item.grade && <p>{item.grade}</p>}<p>{item.skills.join(', ')}</p>
            <p>{text.years}: {Math.floor(item.experienceMonths / 12)} · {text.months}: {item.experienceMonths % 12}</p>
            <p className="candidate-availability">{label(item.availability)}</p><strong>{rate(item.hourlyRate)}</strong>
          </div><button className="secondary" onClick={() => openCandidate(item.id)} disabled={busy}>{text.open}</button>
        </article>)}</div>
        <div className="candidate-pagination"><span>{text.total}: {queue.total} · {text.page}: {page + 1} / {Math.max(1, Math.ceil(queue.total / queue.size))}</span>
          <div className="candidate-actions"><button className="secondary" onClick={() => setPage(value => value - 1)} disabled={loading || page === 0}><ArrowLeft size={17}/>{text.previous}</button>
            <button className="secondary" onClick={() => setPage(value => value + 1)} disabled={loading || (page + 1) * queue.size >= queue.total}>{text.next}<ArrowRight size={17}/></button></div></div>
      </>}
    </>}
    {view === 'details' && selected && <article className="candidate-details">
      <div className="candidate-detail-header"><div><p className="staff-eyebrow">{selected.code}</p><h2>{selected.role}</h2></div>
        <button className="primary" onClick={() => { clearFeedback(); setView('edit'); }} disabled={busy}>{text.edit}</button></div>
      <dl className="candidate-detail-grid">
        <div><dt>{text.grade}</dt><dd>{selected.grade || '—'}</dd></div><div><dt>{text.externalKey}</dt><dd>{selected.externalKey || '—'}</dd><small>{text.externalHelp}</small></div>
        <div><dt>{text.filter}</dt><dd>{label(selected.availability)}</dd><small>{text.manual}</small></div>
        <div><dt>{text.years} / {text.months}</dt><dd>{Math.floor(selected.experienceMonths / 12)} / {selected.experienceMonths % 12}</dd></div>
        <div className="candidate-wide"><dt>{text.skills}</dt><dd>{selected.skills.join(', ')}</dd></div>
        <div className="candidate-wide"><dt>{text.description}</dt><dd className="candidate-description">{selected.description}</dd></div>
        <div><dt>{text.amount}</dt><dd>{rate(selected.hourlyRate)}</dd></div>
        <div><dt>{text.rateChangedAt}</dt><dd>{date(selected.hourlyRate.changedAt)}</dd><small>{text.rateChangedBy}: {selected.hourlyRate.changedBy}</small></div>
        <div><dt>{text.updatedAt}</dt><dd>{date(selected.updatedAt)}</dd></div>
        <div><dt>{text.cv}</dt><dd>{selected.cv?.fileName || '—'}</dd></div>
      </dl>
      {selected.cv && <button className="secondary" onClick={download} disabled={busy}>{text.download}</button>}
      <form className="candidate-cv-form" onSubmit={replaceCv} key={`${selected.id}-${selected.version}`}>
        <label htmlFor="candidate-replace-cv">{text.cv}</label><input id="candidate-replace-cv" name="cv" type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          onChange={event => setReplacement(event.target.files?.[0] ?? null)} disabled={busy}/>
        <small>{text.fileHelp}</small><button className="secondary" type="submit" disabled={busy || !replacement}>{text.replace}</button>
      </form>
    </article>}
    <footer className="candidate-footer">{view === 'details' && <button className="secondary" onClick={backToList} disabled={busy}>{text.back}</button>}
      <button className="secondary" onClick={onBack} disabled={busy || uncertain}>{text.home}</button></footer>
  </section></StaffFrame>;
}

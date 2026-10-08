import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, Plus, ArrowsClockwise } from '@phosphor-icons/react';
import { getAllPartnerLeads } from '../api/partnerLeadsApi';
import { createPartnerStaffingRequest, getPartnerStaffingRequest, getPartnerStaffingRequests, uploadPartnerStaffingAttachment } from '../api/staffingRequestsApi';
import './StaffingRequestsPage.css';

const initial = () => ({ leadId: '', title: '', headcount: '1', grade: '', requiredSkills: '', preferredSkills: '',
  taskDescription: '', hoursPerWeek: '40', mode: 'REMOTE', location: '', workOutsideRussiaAllowed: false,
  startDateFlexible: false, desiredStartDate: '', language: '', languageLevel: '', interviewRequired: true,
  screeningRequired: false, cvRequired: true });
const splitSkills = value => value.split(/[,\n]/).map(skill => skill.trim()).filter(Boolean);
const dateLabel = (value, locale) => value ? new Intl.DateTimeFormat(({ RU: 'ru-RU', KZ: 'kk-KZ', EN: 'en-US' })[locale] ?? 'ru-RU', { dateStyle: 'medium' }).format(new Date(value)) : null;
export function StaffingRequestsPage({ text = key => key, locale = 'RU' }) {
  const t = key => text(`staffing.${key}`);
  const [items, setItems] = useState([]);
  const [leads, setLeads] = useState([]);
  const [form, setForm] = useState(initial);
  const [view, setView] = useState('list');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [attachments, setAttachments] = useState({});
  const [attachmentErrors, setAttachmentErrors] = useState({});
  const [receipt, setReceipt] = useState(null);
  const [files, setFiles] = useState({ sourceBrief: null, cv: null });
  const [rejectedFiles, setRejectedFiles] = useState({ sourceBrief: false, cv: false });
  const [failedFiles, setFailedFiles] = useState([]);
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const result = await getPartnerStaffingRequests({ page: 0 });
      setItems(result.items ?? []);
      setPage(0);
      setHasMore((result.items ?? []).length === 20);
    } catch { setError(t('errors.unavailable')); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { reload(); getAllPartnerLeads().then(setLeads).catch(() => setLeads([])); }, [reload]);

  const update = (name, value) => setForm(current => ({ ...current, [name]: value }));
  const loadMore = async () => {
    setLoading(true); setError('');
    try {
      const next = await getPartnerStaffingRequests({ page: page + 1 });
      setItems(current => [...current, ...(next.items ?? [])]);
      setPage(page + 1);
      setHasMore((next.items ?? []).length === 20);
    } catch { setError(t('errors.unavailable')); }
    finally { setLoading(false); }
  };
  const submit = async event => {
    event.preventDefault(); setSaving(true); setError(''); setFieldErrors({});
    if (Object.values(rejectedFiles).some(Boolean)) { setError(t('form.invalidFile')); setSaving(false); return; }
    try {
      const request = await createPartnerStaffingRequest({
        leadId: form.leadId,
        role: { title: form.title, headcount: Number(form.headcount), grade: form.grade,
          requiredSkills: splitSkills(form.requiredSkills), preferredSkills: splitSkills(form.preferredSkills), taskDescription: form.taskDescription },
        work: { hoursPerWeek: Number(form.hoursPerWeek), mode: form.mode, location: form.location,
          workOutsideRussiaAllowed: form.workOutsideRussiaAllowed, desiredStartDate: form.startDateFlexible ? null : form.desiredStartDate,
          startDateFlexible: form.startDateFlexible },
        selection: { language: form.language || null, languageLevel: form.language ? form.languageLevel : null,
          interviewRequired: form.interviewRequired, screeningRequired: form.screeningRequired, cvRequired: form.cvRequired },
      }, idempotencyKey);
      const uploadFailures = [];
      for (const [kind, file] of Object.entries(files)) {
        if (!file) continue;
        try { await uploadPartnerStaffingAttachment(request.id, kind, file); }
        catch { uploadFailures.push({ kind, file, message: t('errors.upload') }); }
      }
      setFailedFiles(uploadFailures);
      setReceipt({ ...request });
      setFiles(uploadFailures.reduce((next, item) => ({ ...next, [item.kind]: item.file }), { sourceBrief: null, cv: null }));
      setForm(initial()); setView('success'); await reload();
    } catch (failure) {
      const next = Object.fromEntries((failure.fields ?? []).map(item => [item.field.replace(/\[\d+\]/g, ''), true]));
      setFieldErrors(next);
      setError(failure.status === 400 ? t('errors.validation') : t('errors.submit'));
    }
    finally { setSaving(false); }
  };
  const beginCreate = () => { setError(''); setReceipt(null); setFailedFiles([]); setIdempotencyKey(crypto.randomUUID()); setForm(initial()); setFiles({ sourceBrief: null, cv: null }); setRejectedFiles({ sourceBrief: false, cv: false }); setView('form'); };
  const retryFailedFiles = async () => {
    setSaving(true);
    const stillFailed = [];
    for (const item of failedFiles) {
      try { await uploadPartnerStaffingAttachment(receipt.id, item.kind, item.file); }
      catch { stillFailed.push({ ...item, message: t('errors.upload') }); }
    }
    setFailedFiles(stillFailed); setSaving(false);
  };
  const setFile = (kind, file, input) => {
    if (file && (file.size > 20 * 1024 * 1024 || !['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'].includes(file.type))) {
      setFiles(current => ({ ...current, [kind]: null })); setRejectedFiles(current => ({ ...current, [kind]: true }));
      if (input) input.value = ''; setError(t('form.invalidFile')); return;
    }
    setError(''); setRejectedFiles(current => ({ ...current, [kind]: false })); setFiles(current => ({ ...current, [kind]: file }));
  };

  if (view === 'success' && receipt) return <section className="staffing-page">
    <div className="staffing-receipt"><span className="staffing-receipt-mark" aria-hidden="true">✓</span><p className="staffing-eyebrow">{t('success.eyebrow')}</p>
      <h2>{receipt.number}</h2><p>{t('success.status')}: {t(`status.${receipt.status}`)}</p>
      {failedFiles.length > 0 && <div className="staffing-error" role="alert"><p>{t('success.uploadFailed')}</p><ul>{failedFiles.map(item => <li key={item.kind}>{item.file.name}: {item.message}</li>)}</ul><button className="staffing-inline" disabled={saving} onClick={retryFailedFiles}>{t('success.retryUpload')}</button></div>}
      <div className="staffing-actions"><button className="staffing-secondary" onClick={() => setView('list')}>{t('success.list')}</button><button className="staffing-primary" onClick={beginCreate}><Plus size={18}/>{t('success.new')}</button></div>
    </div></section>;

  if (view === 'form') return <section className="staffing-page">
    <button className="staffing-back" type="button" onClick={() => setView('list')}><ArrowLeft size={17}/> {t('form.back')}</button>
    <div className="staffing-form-heading"><p className="staffing-eyebrow">{t('form.eyebrow')}</p><h2>{t('form.title')}</h2><p>{t('form.description')}</p></div>
    <form className="staffing-form" onSubmit={submit}>
      <fieldset><legend><span>01</span> {t('form.context')}</legend>
        <label className="staffing-wide">{t('form.project')} <b>*</b><select required value={form.leadId} onChange={e => update('leadId', e.target.value)}><option value="">{t('form.projectPlaceholder')}</option>{leads.map(lead => <option key={lead.id} value={lead.id}>{lead.companyName} — {lead.requestSummary}</option>)}</select>{fieldErrors.leadId&&<small className="staffing-field-error">{t('errors.field')}</small>}<small>{t('form.projectHelp')}</small></label>
      </fieldset>
      <fieldset><legend><span>02</span> {t('form.role')}</legend>
        <label>{t('form.position')} <b>*</b><input required maxLength="200" value={form.title} onChange={e => update('title', e.target.value)} placeholder={t('form.positionPlaceholder')}/>{fieldErrors['role.title']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label>{t('form.headcount')} <b>*</b><input required type="number" min="1" max="100" value={form.headcount} onChange={e => update('headcount', e.target.value)}/>{fieldErrors['role.headcount']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label>{t('form.grade')} <b>*</b><select required value={form.grade} onChange={e => update('grade', e.target.value)}><option value="">{t('form.chooseGrade')}</option>{['Junior','Junior+','Middle','Middle+','Senior','Lead'].map(item => <option key={item}>{item}</option>)}</select>{fieldErrors['role.grade']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label>{t('form.requiredSkills')} <b>*</b><input required maxLength="120" value={form.requiredSkills} onChange={e => update('requiredSkills', e.target.value)} placeholder="Java, Kotlin, Spring Boot"/><small>{fieldErrors['role.requiredSkills']?t('errors.field'):t('form.skillsHelp')}</small></label>
        <label className="staffing-wide">{t('form.preferredSkills')}<input value={form.preferredSkills} onChange={e => update('preferredSkills', e.target.value)} placeholder="Kafka, AWS"/>{fieldErrors['role.preferredSkills']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label className="staffing-wide">{t('form.tasks')} <b>*</b><textarea required rows="5" maxLength="10000" value={form.taskDescription} onChange={e => update('taskDescription', e.target.value)} placeholder={t('form.tasks')}/>{fieldErrors['role.taskDescription']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
      </fieldset>
      <fieldset><legend><span>03</span> {t('form.work')}</legend>
        <label>{t('form.hours')} <b>*</b><input required type="number" min="1" max="40" value={form.hoursPerWeek} onChange={e => update('hoursPerWeek', e.target.value)}/>{fieldErrors['work.hoursPerWeek']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label>{t('form.workMode')} <b>*</b><select value={form.mode} onChange={e => update('mode', e.target.value)}><option value="REMOTE">{t('form.remote')}</option><option value="HYBRID">{t('form.hybrid')}</option><option value="OFFICE">{t('form.office')}</option></select>{fieldErrors['work.mode']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label>{t('form.location')} <b>*</b><input required maxLength="200" value={form.location} onChange={e => update('location', e.target.value)}/>{fieldErrors['work.location']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label className="staffing-check staffing-wide"><input type="checkbox" checked={form.workOutsideRussiaAllowed} onChange={e => update('workOutsideRussiaAllowed', e.target.checked)}/> {t('form.outsideRussia')}</label>
        <div className="staffing-wide"><label className="staffing-check"><input type="checkbox" checked={form.startDateFlexible} onChange={e => update('startDateFlexible', e.target.checked)}/> {t('form.flexibleDate')}</label>
          {!form.startDateFlexible && <label className="staffing-date">{t('form.desiredDate')} <b>*</b><input required type="date" value={form.desiredStartDate} onChange={e => update('desiredStartDate', e.target.value)}/>{fieldErrors['work.desiredStartDate']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>}</div>
      </fieldset>
      <fieldset><legend><span>04</span> {t('form.selection')}</legend>
        <label>{t('form.language')}<select value={form.language} onChange={e => { update('language', e.target.value); if (!e.target.value) update('languageLevel', ''); }}><option value="">{t('form.noLanguage')}</option><option>{t('form.languageEnglish')}</option><option>{t('form.languageRussian')}</option><option>{t('form.languageKazakh')}</option></select></label>
        {form.language && <label>{t('form.languageLevel')} <b>*</b><select required value={form.languageLevel} onChange={e => update('languageLevel', e.target.value)}><option value="">{t('form.chooseLanguageLevel')}</option>{['A1','A2','B1','B2','C1','C2'].map(level => <option key={level}>{level}</option>)}<option value="NATIVE">{t('form.nativeLanguage')}</option></select>{fieldErrors['selection.languageLevel']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>}
        <label className="staffing-check"><input type="checkbox" checked={form.interviewRequired} onChange={e => update('interviewRequired', e.target.checked)}/> {t('form.interview')}{fieldErrors['selection.interviewRequired']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label className="staffing-check"><input type="checkbox" checked={form.screeningRequired} onChange={e => update('screeningRequired', e.target.checked)}/> {t('form.screening')}{fieldErrors['selection.screeningRequired']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
        <label className="staffing-check"><input type="checkbox" checked={form.cvRequired} onChange={e => update('cvRequired', e.target.checked)}/> {t('form.cv')}{fieldErrors['selection.cvRequired']&&<small className="staffing-field-error">{t('errors.field')}</small>}</label>
      </fieldset>
      <fieldset><legend><span>05</span> {t('form.files')} <em>{t('form.optional')}</em></legend>
        <label>{t('form.brief')}<input type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={e => setFile('sourceBrief', e.target.files[0] ?? null, e.target)}/><small>{files.sourceBrief?.name || t('form.fileHelp')}</small>{rejectedFiles.sourceBrief&&<button type="button" className="staffing-inline" onClick={()=>setRejectedFiles(current=>({...current,sourceBrief:false}))}>{t('form.continueWithoutFile')}</button>}</label>
        <label>{t('form.cvExample')}<input type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={e => setFile('cv', e.target.files[0] ?? null, e.target)}/><small>{files.cv?.name || t('form.fileHelp')}</small>{rejectedFiles.cv&&<button type="button" className="staffing-inline" onClick={()=>setRejectedFiles(current=>({...current,cv:false}))}>{t('form.continueWithoutFile')}</button>}</label>
      </fieldset>
      {error && <p className="staffing-error staffing-wide" role="alert">{error}</p>}
      <div className="staffing-actions staffing-wide"><button className="staffing-secondary" type="button" onClick={() => setView('list')}>{t('form.cancel')}</button><button className="staffing-primary" type="submit" disabled={saving}>{saving?t('form.saving'):t('form.submit')}<ArrowRight size={18}/></button></div>
    </form>
  </section>;

  return <section className="staffing-page">
    <div className="staffing-list-heading"><div><p className="staffing-eyebrow">{t('list.eyebrow')}</p><h2>{t('list.title')}</h2><p>{t('list.description')}</p></div><button className="staffing-primary" onClick={beginCreate}><Plus size={18}/>{t('list.create')}</button></div>
    {error && <p className="staffing-error" role="alert">{error}<button className="staffing-inline" onClick={reload}>{t('errors.retry')}</button></p>}
    {loading ? <div className="staffing-empty" role="status">{t('list.loading')}</div> : items.length ? <div className="staffing-list">{items.map(item => <article className="staffing-card" key={item.id}><div className="staffing-card-top"><span>{item.number}</span><span className="staffing-status">{t(`status.${item.status}`)}</span></div><h3>{item.role?.title}</h3><p>{item.role?.grade} · {item.role?.headcount} · {item.work?.hoursPerWeek}</p><div className="staffing-card-bottom"><span>{item.role?.requiredSkills?.join(' · ')}</span><time>{t('list.submitted')}: {dateLabel(item.createdAt, locale)} · {item.work?.location}</time></div><button className="staffing-inline" onClick={async()=>{setAttachmentErrors(current=>({...current,[item.id]:false}));try{const result=await getPartnerStaffingRequest(item.id);setAttachments(current=>({...current,[item.id]:result.attachments??[]}));}catch{setAttachmentErrors(current=>({...current,[item.id]:true}));}}}>{t('list.files')}</button>{attachmentErrors[item.id]&&<p className="staffing-error" role="alert">{t('errors.unavailable')}</p>}{(attachments[item.id]??[]).map(file=><a className="staffing-file-link" key={file.id} href={`/api/v1/partner/staffing-requests/${item.id}/attachments/${file.id}`}>{file.fileName}</a>)}</article>)}</div> : <div className="staffing-empty"><span className="staffing-empty-icon"><Plus size={24}/></span><h3>{t('list.emptyTitle')}</h3><p>{t('list.emptyDescription')}</p><button className="staffing-primary" onClick={beginCreate}>{t('list.create')}<ArrowRight size={18}/></button></div>}
    {!loading&&items.length>0&&<div className="staffing-list-actions">{hasMore&&<button className="staffing-secondary" onClick={loadMore}>{t('list.more')}</button>}<button className="staffing-refresh" onClick={reload}><ArrowsClockwise size={16}/>{t('list.refresh')}</button></div>}
  </section>;
}

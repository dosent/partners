import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowCounterClockwise, ArrowRight, CalendarBlank, CaretDown, CaretLeft, CaretRight, ChatCircleText, Clock, MagnifyingGlass, Plus, X } from '@phosphor-icons/react';
import { createPartnerLead, getPartnerLead, getPartnerLeadAttachmentUrl, getPartnerLeadBoard, sendPartnerLeadMessage } from '../api/partnerLeadsApi';
import './PartnerLeadBoard.css';
import { PartnerPageHeading } from './PartnerPageHeading';

const LANES = [
  { id: 'NEW', title: 'Новый', tone: 'blue' },
  { id: 'QUALIFICATION', title: 'Квалификация', tone: 'purple' },
  { id: 'DUPLICATE_REVIEW', title: 'Проверка совпадения', tone: 'amber' },
  { id: 'IN_WORK', title: 'В работе', tone: 'cyan' },
  { id: 'WON', title: 'Успешно', tone: 'green' },
  { id: 'CLOSED', title: 'Закрыт', tone: 'gray' },
];
const STATUS_LABELS = {
  NEW: 'Новый', QUALIFICATION: 'Квалификация', DUPLICATE_REVIEW: 'Проверка совпадения',
  IN_WORK: 'В работе', WON: 'Успешно', LOST: 'Закрыт · не реализован', REJECTED: 'Закрыт · отклонён',
};
const STATUS_DESCRIPTIONS = {
  NEW: 'Заявка получена и ожидает первичной проверки.',
  QUALIFICATION: 'Менеджер уточняет потребность и детали запроса.',
  DUPLICATE_REVIEW: 'Менеджер проверяет возможность совпадения.',
  IN_WORK: 'Команда работает с запросом.', WON: 'Работа по лиду завершилась успешно.',
  LOST: 'Работа по лиду завершена без сделки.', REJECTED: 'Заявка отклонена после проверки.',
};
const text = value => typeof value === 'string' ? value : '';
const dateLabel = value => value ? new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value)) : '—';
const moneyLabel = (amount, currency) => amount == null || amount === '' ? null : `${new Intl.NumberFormat('ru-RU').format(Number(amount))} ${currency || '₽'}`;
const toUtcBoundary = (value, end = false) => value ? `${value}T${end ? '23:59:59.999' : '00:00:00.000'}Z` : '';

function normalizeColumn(column = {}) {
  const id = column.lane ?? column.status ?? 'NEW';
  const closed = id === 'CLOSED' || id === 'LOST' || id === 'REJECTED';
  return {
    ...column,
    id: closed ? 'CLOSED' : id,
    items: Array.isArray(column.items) ? column.items : [],
    total: Number.isFinite(Number(column.total)) ? Number(column.total) : 0,
    nextCursor: column.nextCursor ?? null,
  };
}

function leadName(lead) { return text(lead.companyName ?? lead.company) || 'Компания не указана'; }
function leadRequest(lead) { return text(lead.requestSummary ?? lead.request) || 'Запрос не описан'; }
function leadStatus(lead) { return STATUS_LABELS[lead.status] ?? 'Статус уточняется'; }
function errorMessage(error) {
  if (error?.status === 401 || error?.status === 403) return 'Сессия завершена или доступ к лидам не подтверждён. Войдите снова.';
  return error?.message || 'Не удалось загрузить лиды. Попробуйте ещё раз.';
}
const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024;
const ATTACHMENT_TYPES = {
  '.pdf': 'application/pdf',
  '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
};
function validateAttachment(file) {
  if (!file) return '';
  if (file.size > MAX_ATTACHMENT_BYTES) return 'Размер файла превышает 20 МБ. Выберите файл меньшего размера.';
  const extension = `.${file.name.split('.').pop().toLowerCase()}`;
  if (!ATTACHMENT_TYPES[extension] || file.type !== ATTACHMENT_TYPES[extension]) return 'Допустимы только PDF или DOCX с соответствующим типом файла.';
  return '';
}
function safeLeadCreateError(error, hasAttachment) {
  if (!hasAttachment) return errorMessage(error);
  if (error.status === 413 || error.code === 'ATTACHMENT_TOO_LARGE') return 'Размер файла превышает 20 МБ. Заявка не создана.';
  if (error.status === 503) return 'Не удалось проверить или сохранить вложение. Лид не создан; попробуйте позже.';
  if ([400, 415, 422].includes(error.status) || String(error.code ?? '').startsWith('ATTACHMENT_')) return 'Допустимы PDF и DOCX размером до 20 МБ. Файл должен пройти проверку. Лид не создан.';
  return errorMessage(error);
}

function BoardCard({ lead, onSelect }) {
  const budget = moneyLabel(lead.budgetAmount ?? lead.budget, lead.budgetCurrency);
  const closedStatus = lead.status === 'LOST' || lead.status === 'REJECTED';
  return <button className="plb-card" type="button" onClick={() => onSelect(lead)} aria-label={`Открыть лид ${leadName(lead)}`}>
    <span className="plb-card-top"><span>{text(lead.number ?? lead.id) ? `Лид ${text(lead.number ?? lead.id)}` : 'Лид'}</span><ArrowRight size={17}/></span>
    <strong className="plb-company">{leadName(lead)}</strong>
    <span className="plb-request">{leadRequest(lead)}</span>
    {closedStatus && <span className="plb-status-tag">{leadStatus(lead)}</span>}
    {budget && <span className="plb-budget">{budget}</span>}
    {lead.assignedManagerName&&<span className="plb-manager">Менеджер · {lead.assignedManagerName}</span>}
    <span className="plb-card-footer"><span><Clock size={14}/>{dateLabel(lead.updatedAt ?? lead.updated_at)}</span>{lead.unreadMessages > 0 && <span><ChatCircleText size={14}/>{lead.unreadMessages}</span>}</span>
  </button>;
}

function CreateLeadDialog({ onClose, onCreated }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [attachment, setAttachment] = useState(null);
  const [attachmentError, setAttachmentError] = useState('');
  const attachmentInputRef = useRef(null);
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    const payload = {
      companyName: String(fields.get('companyName') || '').trim(),
      domainOrTaxId: String(fields.get('domainOrTaxId') || '').trim(),
      requestSummary: String(fields.get('requestSummary') || '').trim(),
      budgetAmount: fields.get('budgetAmount') ? Number(fields.get('budgetAmount')) : null,
      budgetCurrency: String(fields.get('budgetCurrency') || 'RUB'),
      plannedStartAt: fields.get('plannedStartAt') || null,
      decisionMakerName: String(fields.get('decisionMakerName') || '').trim(),
      decisionMakerEmail: String(fields.get('decisionMakerEmail') || '').trim(),
      description: String(fields.get('description') || '').trim(),
    };
    const fileError = validateAttachment(attachment);
    if (fileError || attachmentError) { setAttachmentError(fileError || attachmentError); return; }
    setBusy(true); setError(''); setFieldErrors({});
    try { await createPartnerLead(payload, attachment); await onCreated(); }
    catch (requestError) {
      const errors = {};
      for (const item of requestError.fields ?? []) if (item.field && item.message) errors[item.field] = item.message;
      setFieldErrors(errors); setError(safeLeadCreateError(requestError, Boolean(attachment)));
    } finally { setBusy(false); }
  }
  return <div className="plb-overlay" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="plb-dialog" role="dialog" aria-modal="true" aria-labelledby="plb-create-title">
      <header className="plb-dialog-heading"><div><span className="plb-eyebrow">Новая возможность</span><h2 id="plb-create-title">Добавить лид</h2></div><button type="button" className="plb-icon-button" onClick={onClose} aria-label="Закрыть"><X size={21}/></button></header>
      <p className="plb-dialog-intro">Передайте запрос менеджеру SimbirSoft. Вы сможете следить за его ходом на доске.</p>
      <form className="plb-form" onSubmit={submit}>
        <label>Компания <span>*</span><input name="companyName" required maxLength="200" autoFocus aria-invalid={Boolean(fieldErrors.companyName)}/>{fieldErrors.companyName&&<small>{fieldErrors.companyName}</small>}</label>
        <label>Домен или ИНН <span>*</span><input name="domainOrTaxId" required maxLength="200" placeholder="company.ru или ИНН" aria-invalid={Boolean(fieldErrors.domainOrTaxId)}/>{fieldErrors.domainOrTaxId&&<small>{fieldErrors.domainOrTaxId}</small>}</label>
        <label className="plb-form-wide">Кратко опишите запрос <span>*</span><input name="requestSummary" required maxLength="300" placeholder="Например, команда Java-разработчиков" aria-invalid={Boolean(fieldErrors.requestSummary)}/>{fieldErrors.requestSummary&&<small>{fieldErrors.requestSummary}</small>}</label>
        <label>Предварительный бюджет<input name="budgetAmount" type="number" min="0" step="0.01" placeholder="1 200 000"/></label>
        <label>Валюта<select name="budgetCurrency"><option value="RUB">RUB · ₽</option><option value="KZT">KZT · ₸</option><option value="USD">USD · $</option><option value="EUR">EUR · €</option></select></label>
        <label>Планируемый старт<input name="plannedStartAt" type="date"/></label>
        <label>Контактное лицо<input name="decisionMakerName" maxLength="200" placeholder="Имя и должность"/></label>
        <label className="plb-form-wide">Email контактного лица<input name="decisionMakerEmail" type="email" maxLength="254" placeholder="name@company.ru"/></label>
        <label className="plb-form-wide">Подробности запроса <span>*</span><textarea name="description" rows="4" maxLength="10000" required placeholder="Какая задача стоит перед компанией?"/></label>
        <label className="plb-form-wide">Вложение · PDF или DOCX, до 20 МБ<input ref={attachmentInputRef} name="attachment" type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" disabled={busy} aria-invalid={Boolean(attachmentError)} onChange={event=>{const file=event.target.files?.[0]??null;const validation=validateAttachment(file);setAttachment(validation?'':file);setAttachmentError(validation);setError('');setFieldErrors({});}}/>{attachmentError&&<small className="plb-file-error" role="alert">{attachmentError}</small>}{attachment&&<span className="plb-file-selected">{attachment.name} · {(attachment.size/1024/1024).toFixed(1)} МБ<button type="button" disabled={busy} onClick={()=>{setAttachment(null);setAttachmentError('');setError('');if(attachmentInputRef.current)attachmentInputRef.current.value='';}}>Убрать файл</button></span>}<small>Файл будет проверен сервером перед сохранением. Если проверка не пройдёт, заявка не будет создана.</small></label>
        {error&&<p className="plb-form-error" role="alert">{error}</p>}
        <footer className="plb-dialog-actions"><button type="button" className="plb-secondary" onClick={onClose} disabled={busy}>Отмена</button><button type="submit" className="plb-primary" disabled={busy}>{busy?'Отправляем…':'Отправить лид'}<ArrowRight size={17}/></button></footer>
      </form>
    </section>
  </div>;
}

function LeadDetails({ leadId, onClose }) {
  const [lead, setLead] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const load = () => { setLoading(true); setError(''); getPartnerLead(leadId).then(setLead).catch(e => setError(errorMessage(e))).finally(() => setLoading(false)); };
  useEffect(() => { load(); }, [leadId]);
  async function send(event) {
    event.preventDefault(); if (!message.trim()) return;
    setSending(true); setSendError('');
    try { await sendPartnerLeadMessage(leadId, message.trim()); setMessage(''); load(); }
    catch (e) { setSendError(errorMessage(e)); }
    finally { setSending(false); }
  }
  return <div className="plb-overlay" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="plb-dialog plb-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="plb-detail-title">
      <header className="plb-dialog-heading"><div><span className="plb-eyebrow">Карточка лида</span><h2 id="plb-detail-title">{lead?leadName(lead):'Подробности'}</h2></div><button type="button" className="plb-icon-button" onClick={onClose} aria-label="Закрыть"><X size={21}/></button></header>
      {loading&&<p className="plb-state" role="status" aria-busy="true">Загружаем данные лида…</p>}
      {error&&<div className="plb-state plb-error" role="alert"><p>{error}</p><button className="plb-secondary" onClick={load}><ArrowCounterClockwise size={17}/>Повторить</button></div>}
      {lead&&!loading&&!error&&<>
        <div className="plb-detail-status"><span className="plb-status-tag">{leadStatus(lead)}</span><span>Обновлён {dateLabel(lead.updatedAt ?? lead.updated_at)}</span></div>
        <h3 className="plb-detail-request">{leadRequest(lead)}</h3>
        <p className="plb-detail-description">{text(lead.description) || 'Подробное описание не добавлено.'}</p>
        <dl className="plb-detail-grid">{[["Домен / ИНН",lead.domainOrTaxId ?? lead.domain],['Предварительный бюджет',moneyLabel(lead.budgetAmount,lead.budgetCurrency)],['Планируемый старт',dateLabel(lead.plannedStartAt)],['Контактное лицо',lead.decisionMakerName],['Email',lead.decisionMakerEmail]].filter(([,value])=>value).map(([name,value])=><div key={name}><dt>{name}</dt><dd>{value}</dd></div>)}</dl>
        {Array.isArray(lead.history)&&lead.history.length>0&&<section className="plb-detail-section"><h3>История статусов</h3><ol className="plb-history">{lead.history.map((event,index)=><li key={event.id??`${event.status}-${index}`}><span className="plb-history-dot"/><div><strong>{STATUS_LABELS[event.status]??event.status}</strong><time>{dateLabel(event.changedAt??event.createdAt)}</time>{event.comment&&<p>{event.comment}</p>}</div></li>)}</ol></section>}
        {Array.isArray(lead.attachments)&&lead.attachments.length>0&&<section className="plb-detail-section"><h3>Вложения</h3><ul className="plb-attachments">{lead.attachments.map(file=>{
          const scanStatus=String(file.scanStatus??'').toUpperCase();
          return <li key={file.id}><span>{text(file.fileName)||'Файл'}</span>{scanStatus==='CLEAN'&&file.id?<a href={getPartnerLeadAttachmentUrl(leadId,file.id)} target="_blank" rel="noreferrer">Скачать</a>:['PENDING','SCANNING','PROCESSING'].includes(scanStatus)?<small>Файл проверяется. Скачивание пока недоступно.</small>:<small>Файл недоступен до завершения проверки.</small>}</li>;
        })}</ul></section>}
        <section className="plb-detail-section"><h3>Переписка с менеджером</h3><div className="plb-messages">{(lead.messages??[]).length===0?<p className="plb-muted">Сообщений пока нет. Здесь появятся вопросы менеджера и ваши ответы.</p>:(lead.messages??[]).map(item=><article className={`plb-message ${item.authorType==='PARTNER'?'is-partner':''}`} key={item.id}><strong>{text(item.authorName)||(item.authorType==='PARTNER'?'Вы':'Менеджер')}</strong><p>{text(item.message??item.body)}</p><time>{dateLabel(item.createdAt)}</time></article>)}</div>
          <form className="plb-reply" onSubmit={send}><label htmlFor="plb-reply-text">Сообщение менеджеру</label><textarea id="plb-reply-text" rows="3" value={message} onChange={event=>setMessage(event.target.value)} maxLength="5000" required placeholder="Напишите уточнение или задайте вопрос"/>{sendError&&<p className="plb-form-error" role="alert">{sendError}</p>}<button className="plb-primary" disabled={sending||!message.trim()}>{sending?'Отправляем…':'Отправить сообщение'}<ArrowRight size={17}/></button></form>
        </section>
      </>}
    </section>
  </div>;
}

export function PartnerLeadBoard({ title = 'Мои лиды' }) {
  const [columns, setColumns] = useState(() => LANES.map(lane => ({ ...lane, items: [], total: 0, nextCursor: null })));
  const [query, setQuery] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedLane, setSelectedLane] = useState('NEW');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [creating, setCreating] = useState(false);
  const [selectedLead, setSelectedLead] = useState(null);
  const [reload, setReload] = useState(0);
  const resetFilters = () => { setQuery(''); setFrom(''); setTo(''); setStatusFilter(''); };

  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      setLoading(true); setError('');
      getPartnerLeadBoard({ query, updatedFrom:toUtcBoundary(from), updatedTo:toUtcBoundary(to,true), status:statusFilter })
        .then(response => {
          if (!active) return;
          const received = (response.columns ?? []).map(normalizeColumn);
          setColumns(LANES.map(lane => {
            const column = received.find(item => item.id===lane.id || (lane.id==='CLOSED'&&['LOST','REJECTED'].includes(item.id))) ?? {};
    return { ...lane, ...column, id:lane.id, items:column.items??[], total:column.total??0, nextCursor:column.nextCursor??null };
          }));
          setLoaded(true);
        }).catch(requestError => { if (active) setError(errorMessage(requestError)); })
        .finally(() => { if (active) setLoading(false); });
    }, query ? 250 : 0);
    return () => { active=false; window.clearTimeout(timer); };
  }, [query, from, to, statusFilter, reload]);

  const visibleColumns = useMemo(() => statusFilter
    ? columns.filter(column => column.id===statusFilter || (column.id==='CLOSED'&&['LOST','REJECTED'].includes(statusFilter)))
    : columns, [columns,statusFilter]);
  const total = visibleColumns.reduce((sum,column)=>sum+column.total,0);

  async function loadMore(column) {
    if (!column.nextCursor) return;
    try {
      const result = await getPartnerLeadBoard({ query, updatedFrom:toUtcBoundary(from), updatedTo:toUtcBoundary(to,true), status:statusFilter, cursors:{ [column.id]:column.nextCursor } });
      const received = (result.columns??[]).map(normalizeColumn);
      setColumns(current=>current.map(item=>{
        if(item.id!==column.id)return item;
        const next=received.find(entry=>entry.id===column.id||item.id==='CLOSED'&&['LOST','REJECTED'].includes(entry.id));
        return next?{...item,items:[...item.items,...next.items],total:next.total,nextCursor:next.nextCursor}:item;
      }));
    } catch (requestError) { setError(errorMessage(requestError)); }
  }
  async function created() { setCreating(false); setStatusFilter(''); setSelectedLane('NEW'); setReload(value=>value+1); }

  return <section className="partner-lead-board" aria-label="Доска лидов">
    <PartnerPageHeading title={title}><button type="button" className="plb-primary" onClick={()=>setCreating(true)}><Plus size={18}/>Добавить лид</button></PartnerPageHeading>
    <div className="plb-toolbar">
      <div className="plb-search"><MagnifyingGlass size={19}/><input type="search" aria-label="Поиск лидов" placeholder="Компания, запрос, домен или ИНН" value={query} onChange={event=>setQuery(event.target.value)}/>{query&&<button aria-label="Очистить поиск" onClick={()=>setQuery('')}><X size={16}/></button>}</div>
      <label className="plb-filter"><span>Обновлён с</span><input aria-label="Обновлён с" type="date" value={from} max={to||undefined} onChange={event=>setFrom(event.target.value)}/></label>
      <label className="plb-filter"><span>по</span><input aria-label="Обновлён по" type="date" value={to} min={from||undefined} onChange={event=>setTo(event.target.value)}/></label>
      <label className="plb-status-filter"><span className="sr-only">Фильтр по этапу</span><select aria-label="Фильтр по этапу" value={statusFilter} onChange={event=>{setStatusFilter(event.target.value);if(event.target.value)setSelectedLane(event.target.value==='LOST'||event.target.value==='REJECTED'?'CLOSED':event.target.value);}}><option value="">Все этапы</option>{LANES.flatMap(lane=>lane.id==='CLOSED'?[{id:'LOST',title:'Закрыт · не реализован'},{id:'REJECTED',title:'Закрыт · отклонён'}]:[{id:lane.id,title:lane.title}]).map(item=><option key={item.id} value={item.id}>{item.title}</option>)}</select><CaretDown size={16}/></label>
      {(query||from||to||statusFilter)&&<button className="plb-clear" onClick={resetFilters}>Сбросить</button>}
      <span className="plb-total">{total} {total===1?'лид':'лидов'}</span>
    </div>
    <div className="plb-mobile-lanes" role="group" aria-label="Этапы лидов">{visibleColumns.map(column=><button key={column.id} type="button" className={selectedLane===column.id?'is-selected':''} aria-pressed={selectedLane===column.id} onClick={()=>setSelectedLane(column.id)}>{column.title}<span>{column.total}</span></button>)}</div>
    {error&&<div className="plb-board-error" role="alert"><span>{error}</span><button className="plb-secondary" onClick={()=>setReload(value=>value+1)}><ArrowCounterClockwise size={16}/>Повторить</button></div>}
    {loading&&!loaded?<div className="plb-board-loading" role="status" aria-busy="true"><span className="plb-spinner"/>Загружаем ваши лиды…</div>:null}
    {!loading&&loaded&&!error&&columns.every(column=>column.total===0)&&<div className="plb-empty"><span className="plb-empty-icon"><CalendarBlank size={25}/></span><h2>{query||from||to||statusFilter?'Лиды не найдены':'Здесь появятся ваши лиды'}</h2><p>{query||from||to||statusFilter?'Попробуйте изменить запрос или фильтры.':'Передайте менеджеру новую возможность — он поможет провести её по этапам.'}</p>{query||from||to||statusFilter?<button className="plb-secondary" onClick={resetFilters}>Сбросить фильтры</button>:null}</div>}
    {(total>0||statusFilter)&&<div className={`plb-board ${loading&&!loaded?'is-loading':''}`}>
      {visibleColumns.map(column=><section className={`plb-lane plb-lane-${column.tone} ${selectedLane===column.id?'is-selected':''}`} key={column.id} aria-label={`${column.title}, ${column.total} лидов`}>
        <header className="plb-lane-heading"><span className="plb-lane-marker"/><h2>{column.title}</h2><span className="plb-count" aria-label={`${column.total} всего`}>{column.total}</span><button type="button" className="plb-add-inline" aria-label="Добавить лид" onClick={()=>setCreating(true)}><Plus size={17}/></button></header>
        <div className="plb-lane-cards">{column.items.map((lead,index)=><BoardCard key={lead.id??`${lead.number}-${index}`} lead={lead} onSelect={item=>setSelectedLead(item.id??item.leadId)}/>)}</div>
        {column.items.length===0&&!loading&&loaded&&<p className="plb-lane-empty">Нет лидов на этом этапе</p>}
        {column.nextCursor&&<button type="button" className="plb-more" onClick={()=>loadMore(column)}>Показать ещё <CaretDown size={16}/></button>}
      </section>)}
    </div>}
    {columns.length>0&&total>0&&<div className="plb-board-caption">Показаны лиды текущего партнёра · статусы обновляет менеджер SimbirSoft</div>}
    <div className="plb-mobile-controls" aria-label="Переключение этапов"><button aria-label="Предыдущий этап" onClick={()=>{const i=visibleColumns.findIndex(c=>c.id===selectedLane);setSelectedLane(visibleColumns[Math.max(i-1,0)]?.id??'NEW');}} disabled={visibleColumns.findIndex(c=>c.id===selectedLane)<=0}><CaretLeft size={19}/></button><span>{visibleColumns.findIndex(c=>c.id===selectedLane)+1} / {visibleColumns.length}</span><button aria-label="Следующий этап" onClick={()=>{const i=visibleColumns.findIndex(c=>c.id===selectedLane);setSelectedLane(visibleColumns[Math.min(i+1,visibleColumns.length-1)]?.id??'NEW');}} disabled={visibleColumns.findIndex(c=>c.id===selectedLane)>=visibleColumns.length-1}><CaretRight size={19}/></button></div>
    <button className="plb-mobile-add" onClick={()=>setCreating(true)}><Plus size={18}/>Добавить лид</button>
    {creating&&<CreateLeadDialog onClose={()=>setCreating(false)} onCreated={created}/>}
    {selectedLead&&<LeadDetails leadId={selectedLead} onClose={()=>setSelectedLead(null)}/>}
  </section>;
}

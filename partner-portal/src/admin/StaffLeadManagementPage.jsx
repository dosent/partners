import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, ArrowClockwise, ClipboardText, X } from '@phosphor-icons/react';
import { changeStaffLeadStatus, getStaffLead, getStaffLeadQueue, sendStaffLeadMessage } from '../api/staffLeadsApi';
import { StaffFrame } from './StaffLoginPage';

const PAGE_SIZE = 20;
const statuses = ['NEW', 'QUALIFICATION', 'DUPLICATE_REVIEW', 'IN_WORK', 'WON', 'LOST', 'REJECTED'];
const statusLabels = {
  NEW: 'Новый', QUALIFICATION: 'Квалификация', DUPLICATE_REVIEW: 'Проверка пересечения',
  IN_WORK: 'В работе', WON: 'Успешно', LOST: 'Не состоялся', REJECTED: 'Отклонён',
};
const transitions = {
  NEW: ['QUALIFICATION', 'DUPLICATE_REVIEW', 'REJECTED'],
  QUALIFICATION: ['DUPLICATE_REVIEW', 'IN_WORK', 'LOST', 'REJECTED'],
  DUPLICATE_REVIEW: ['QUALIFICATION', 'IN_WORK', 'LOST', 'REJECTED'],
  IN_WORK: ['WON', 'LOST'], WON: [], LOST: [], REJECTED: [],
};
const dateTime = value => value
  ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
  : '—';

export function StaffLeadManagementPage({ session, onBack, onLogout }) {
  const canManage = (session.permissions ?? []).includes('lead.manage');
  const [queue, setQueue] = useState(null);
  const [detail, setDetail] = useState(null);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [status, setStatus] = useState('');
  const [nextStatus, setNextStatus] = useState('');
  const [message, setMessage] = useState('');
  const [internal, setInternal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const loadQueue = useCallback(async (requestedPage = 0) => {
    setBusy(true); setError('');
    try {
      const result = await getStaffLeadQueue({ page: requestedPage, size: PAGE_SIZE, query: appliedQuery, status });
      setQueue(result); setPage(result.page ?? requestedPage);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }, [appliedQuery, status]);

  useEffect(() => { if (canManage && !detail) loadQueue(0); }, [canManage, detail, loadQueue]);

  async function openLead(id) {
    setBusy(true); setError(''); setNotice(''); setDetail(null);
    try {
      const selected = await getStaffLead(id);
      setDetail(selected); setNextStatus(selected.status);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }

  async function saveStatus() {
    if (!detail || busy || nextStatus === detail.status) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await changeStaffLeadStatus(detail.id, nextStatus);
      const updated = await getStaffLead(detail.id);
      setDetail(updated); setNextStatus(updated.status);
      setNotice('Статус сохранён. Изменение добавлено в историю лида.');
      await loadQueue(page);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }

  async function submitMessage(event) {
    event.preventDefault();
    if (!detail || !message.trim() || busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const sent = await sendStaffLeadMessage(detail.id, message.trim(), internal);
      setDetail(current => ({ ...current, messages: [...(current.messages ?? []), sent] }));
      setMessage('');
      setNotice(internal ? 'Внутренний комментарий сохранён.' : 'Комментарий отправлен партнёру.');
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }

  if (!canManage) return <StaffFrame><section className="staff-card staff-state-card">
    <span className="staff-icon"><X size={27}/></span><h1>Недостаточно прав</h1>
    <p className="staff-intro">Для обработки лидов требуется разрешение «Управление лидами».</p>
    <button className="staff-secondary" onClick={onBack}><ArrowLeft size={18}/>В кабинет</button>
  </section></StaffFrame>;

  return <StaffFrame><div className="staff-moderation staff-leads">
    <div className="staff-admin-top"><div><p className="staff-eyebrow">Рабочая очередь</p><h1>Обработка лидов</h1></div>
      <button className="staff-secondary" onClick={onLogout}>Выйти</button></div>
    {error && <p className="staff-error" role="alert">{error}</p>}
    {notice && <p className="staff-success" role="status">{notice}</p>}
    {detail ? <>
      <button className="staff-back-link" onClick={() => { setDetail(null); setError(''); setNotice(''); }}>
        <ArrowLeft size={17}/>Очередь лидов
      </button>
      <article className="staff-card staff-lead-card">
        <div className="staff-application-heading"><div><p className="staff-eyebrow">{detail.number}</p>
          <h2>{detail.companyName}</h2><p className="staff-lead-meta">Создан {dateTime(detail.createdAt)} · обновлён {dateTime(detail.updatedAt)}</p></div>
          <span className="staff-application-badge">{statusLabels[detail.status] ?? detail.status}</span></div>
        <section className="staff-detail-section"><h3>Данные лида</h3><dl className="staff-detail-grid">
          <LeadField label="Потребность" value={detail.requestSummary}/>
          <LeadField label="Домен или ИНН" value={detail.domainOrTaxId}/>
          <LeadField label="Бюджет" value={detail.budgetAmount ? `${detail.budgetAmount} ${detail.budgetCurrency ?? ''}` : ''}/>
          <LeadField label="Планируемый старт" value={detail.plannedStartAt}/>
          <LeadField label="Контактное лицо" value={detail.decisionMakerName}/>
          <LeadField label="Email контакта" value={detail.decisionMakerEmail}/>
          <LeadField label="Описание" value={detail.description}/>
        </dl></section>
        <section className="staff-detail-section"><h3>Статус</h3>
          <div className="staff-lead-status-form"><label className="staff-form-label" htmlFor="lead-status">Новый статус</label>
            <select id="lead-status" aria-label="Статус лида" value={nextStatus} onChange={event => setNextStatus(event.target.value)}>
              <option value={detail.status}>{statusLabels[detail.status]}</option>
              {(transitions[detail.status] ?? []).map(value => <option value={value} key={value}>{statusLabels[value]}</option>)}
            </select>
            <button className="primary" onClick={saveStatus} disabled={busy || nextStatus === detail.status}>Сохранить статус</button>
          </div>
          {detail.history?.length > 0 && <ol className="staff-history">{detail.history.map((event, index) =>
            <li key={`${event.status}-${event.changedAt}-${index}`}><span>{statusLabels[event.status] ?? event.status}</span>
              <small>{dateTime(event.changedAt)}</small></li>)}</ol>}
        </section>
        <section className="staff-detail-section"><h3>Комментарии</h3>
          {detail.messages?.length ? <ol className="staff-lead-messages">{detail.messages.map(item =>
            <li className={item.internal ? 'is-internal' : ''} key={item.id}>
              <div><strong>{item.authorName || (item.authorType === 'STAFF' ? 'Сотрудник' : 'Партнёр')}</strong>
                <span>{item.internal ? 'Внутренний комментарий' : 'Видно партнёру'}</span><time>{dateTime(item.createdAt)}</time></div>
              <p>{item.body}</p>
            </li>)}</ol> : <p className="staff-lead-empty">Комментариев пока нет.</p>}
          <form className="staff-lead-message-form" onSubmit={submitMessage}>
            <label className="staff-form-label" htmlFor="lead-message">Комментарий партнёру</label>
            <textarea id="lead-message" value={message} onChange={event => setMessage(event.target.value)}
              maxLength={5000} rows={4} required placeholder="Ответ или запрос уточнений"/>
            <label className="staff-lead-internal"><input type="checkbox" checked={internal}
              onChange={event => setInternal(event.target.checked)}/>Внутренний комментарий</label>
            <button className="primary" type="submit" disabled={busy || !message.trim()}>Отправить комментарий</button>
          </form>
        </section>
      </article>
    </> : <>
      <div className="staff-moderation-toolbar staff-leads-toolbar">
        <form className="staff-lead-search" onSubmit={event => { event.preventDefault(); setAppliedQuery(query.trim()); setPage(0); }}>
          <label htmlFor="staff-lead-search">Поиск лидов</label>
          <input id="staff-lead-search" type="search" role="searchbox" aria-label="Поиск лидов"
            value={query} onChange={event => setQuery(event.target.value)} placeholder="Компания, номер, потребность или домен"/>
          <button className="staff-secondary" type="submit">Найти</button>
        </form>
        <label className="staff-lead-filter">Статус
          <select aria-label="Фильтр по статусу" value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
            <option value="">Все статусы</option>{statuses.map(value => <option value={value} key={value}>{statusLabels[value]}</option>)}
          </select>
        </label>
        <button className="staff-secondary" onClick={() => loadQueue(page)} disabled={busy}><ArrowClockwise size={17}/>Обновить</button>
      </div>
      {busy && !queue && <div className="staff-moderation-state" role="status">Загружаем очередь лидов…</div>}
      {!busy && !queue && error && <button className="staff-secondary" onClick={() => loadQueue(page)}>Повторить загрузку</button>}
      {queue && queue.items?.length === 0 && <div className="staff-moderation-state"><ClipboardText size={25}/>
        <strong>Лиды не найдены</strong><span>{appliedQuery || status ? 'Измените запрос или фильтр.' : 'Новых лидов пока нет.'}</span></div>}
      {!!queue?.items?.length && <>
        <div className="staff-queue-list" aria-label="Очередь обработки лидов">
          {queue.items.map(item => <button className="staff-queue-row" key={item.id}
            onClick={() => openLead(item.id)} disabled={busy} aria-label={`Открыть лид ${item.companyName}`}>
            <span className="staff-queue-main"><strong>{item.companyName}</strong>
              <small>{item.number} · {item.requestSummary} · обновлён {dateTime(item.updatedAt)}</small></span>
            <span className="staff-queue-status">{statusLabels[item.status] ?? item.status}</span><ArrowRight size={19}/>
          </button>)}
        </div>
        <div className="staff-pagination"><span>Всего лидов: {queue.total}</span><div>
          <button className="staff-secondary" onClick={() => loadQueue(page - 1)} disabled={busy || page <= 0}>Назад</button>
          <span>Страница {page + 1} из {Math.max(1, Math.ceil(queue.total / queue.size))}</span>
          <button className="staff-secondary" onClick={() => loadQueue(page + 1)}
            disabled={busy || (page + 1) * queue.size >= queue.total}>Далее</button>
        </div></div>
      </>}
    </>}
    <button className="staff-back-link" onClick={onBack}><ArrowLeft size={17}/>Вернуться в кабинет</button>
  </div></StaffFrame>;
}

function LeadField({ label, value }) {
  return <div><dt>{label}</dt><dd>{value || '—'}</dd></div>;
}

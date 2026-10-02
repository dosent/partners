import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, Check, ClipboardText, X } from '@phosphor-icons/react';
import {
  approvePartnerApplication,
  getManualReviewQueue,
  getPartnerApplication,
  rejectPartnerApplication,
  requestPartnerApplicationChanges,
} from '../api/staffAccessApi';
import { StaffFrame } from './StaffLoginPage';

const PAGE_SIZE = 20;
const typeLabels = { INDIVIDUAL: 'Физическое лицо', LEGAL_ENTITY: 'Юридическое лицо', SOLE_PROPRIETOR: 'Индивидуальный предприниматель' };
const statusLabels = {
  MANUAL_REVIEW: 'Требуется ручная проверка', PENDING_REVIEW: 'Ожидает проверки',
  APPROVED: 'Одобрена', REJECTED: 'Отклонена', CHANGES_REQUESTED: 'Запрошены изменения',
};
const dateTime = value => value ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—';

export function StaffModerationPage({ session, onBack, onLogout }) {
  const canRead = (session.permissions ?? []).includes('partner.application.read');
  const canDecide = (session.permissions ?? []).includes('partner.application.decide');
  const [page, setPage] = useState(0);
  const [queue, setQueue] = useState(null);
  const [selectedId, setSelectedId] = useState('');
  const [application, setApplication] = useState(null);
  const [reason, setReason] = useState('');
  const [manager, setManager] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const loadQueue = useCallback(async requestedPage => {
    setBusy(true); setError('');
    try {
      const result = await getManualReviewQueue(requestedPage, PAGE_SIZE);
      setQueue(result); setPage(result.page ?? requestedPage);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }, []);

  useEffect(() => { if (canRead) loadQueue(0); }, [canRead, loadQueue]);

  async function openApplication(id) {
    setBusy(true); setError(''); setNotice(''); setReason(''); setManager(''); setSelectedId(id); setApplication(null);
    try { setApplication(await getPartnerApplication(id)); }
    catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }

  async function decide(action) {
    if (!application || busy) return;
    if (action !== 'approve' && !reason.trim()) { setError('Укажите причину решения.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      const updated = action === 'approve'
        ? await approvePartnerApplication(application.id, manager.trim())
        : action === 'reject'
          ? await rejectPartnerApplication(application.id, reason.trim())
          : await requestPartnerApplicationChanges(application.id, reason.trim());
      setApplication(updated); setReason(''); setManager('');
      setNotice('Решение сохранено. Запись добавлена в историю заявки.');
      await loadQueue(page);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }

  if (!canRead) return <StaffFrame><section className="staff-card staff-state-card">
    <span className="staff-icon"><X size={27}/></span><h1>Недостаточно прав</h1>
    <p className="staff-intro">Для просмотра заявок требуется разрешение на чтение очереди модерации.</p>
    <button className="staff-secondary" onClick={onBack}><ArrowLeft size={18}/>В кабинет</button>
  </section></StaffFrame>;

  return <StaffFrame><div className="staff-moderation">
    <div className="staff-admin-top"><div><p className="staff-eyebrow">Ручная проверка</p><h1>Заявки партнёров</h1></div>
      <button className="staff-secondary" onClick={onLogout}>Выйти</button></div>
    {error && <p className="staff-error" role="alert">{error}</p>}
    {notice && <p className="staff-success" role="status">{notice}</p>}
    {selectedId ? <>
      <button className="staff-back-link" onClick={() => { setSelectedId(''); setApplication(null); setError(''); setNotice(''); }}><ArrowLeft size={17}/>Очередь заявок</button>
      {busy && !application && <div className="staff-moderation-state" role="status">Загружаем карточку…</div>}
      {!busy && !application && error && <button className="staff-secondary" onClick={() => openApplication(selectedId)}>Повторить загрузку</button>}
      {application && <ApplicationCard application={application} canDecide={canDecide} busy={busy}
        reason={reason} manager={manager} onReason={setReason} onManager={setManager} onDecision={decide}/>}
    </> : <>
      <div className="staff-moderation-toolbar"><p>Новые и повторно поданные заявки ожидают решения сотрудника.</p>
        <button className="staff-secondary" onClick={() => loadQueue(page)} disabled={busy}>Обновить</button></div>
      {busy && !queue && <div className="staff-moderation-state" role="status">Загружаем очередь…</div>}
      {queue && queue.items?.length === 0 && <div className="staff-moderation-state"><ClipboardText size={25}/><strong>Очередь пуста</strong><span>Заявок, ожидающих ручной проверки, пока нет.</span></div>}
      {!!queue?.items?.length && <>
        <div className="staff-queue-list" aria-label="Заявки на ручную проверку">
          {queue.items.map(item => <button className="staff-queue-row" key={item.id} onClick={() => openApplication(item.id)} disabled={busy}>
            <span className="staff-queue-main"><strong>Заявка {item.id}</strong><small>Редакция {item.revisionNumber} · подана {dateTime(item.submittedAt)}</small></span>
            <span className="staff-queue-status">Требуется проверка</span><ArrowRight size={19}/>
          </button>)}
        </div>
        <div className="staff-pagination"><span>Всего заявок: {queue.total}</span><div>
          <button className="staff-secondary" onClick={() => loadQueue(page - 1)} disabled={busy || page <= 0}>Назад</button>
          <span>Страница {page + 1} из {Math.max(1, Math.ceil(queue.total / queue.size))}</span>
          <button className="staff-secondary" onClick={() => loadQueue(page + 1)} disabled={busy || (page + 1) * queue.size >= queue.total}>Далее</button>
        </div></div>
      </>}
    </>}
    <button className="staff-back-link" onClick={onBack}><ArrowLeft size={17}/>Вернуться в кабинет</button>
  </div></StaffFrame>;
}

function ApplicationCard({ application, canDecide, busy, reason, manager, onReason, onManager, onDecision }) {
  const revision = application.currentRevision;
  const data = revision?.data ?? {};
  const consent = revision?.consent ?? {};
  const decided = application.status !== 'MANUAL_REVIEW';
  return <article className="staff-card staff-application-card">
    <div className="staff-application-heading"><div><p className="staff-eyebrow">Заявка {application.id}</p>
      <h2>{typeLabels[data.partnerType] ?? 'Партнёр'}</h2></div>
      <span className={`staff-application-badge ${decided ? 'is-decided' : ''}`}>{statusLabels[application.status] ?? application.status}</span>
    </div>
    <p className="staff-application-meta">Редакция {revision?.revisionNumber} · подана {dateTime(revision?.submittedAt)}</p>
    <section className="staff-detail-section"><h3>Сведения заявки</h3><dl className="staff-detail-grid">
      <Detail label="ФИО" value={data.fullName}/><Detail label="Email" value={data.email}/><Detail label="Телефон" value={data.phone}/>
      <Detail label="Страна и регион" value={[data.countryCode, data.region].filter(Boolean).join(' · ')}/>
      {data.companyName && <Detail label="Организация" value={data.companyName}/>}
      {data.taxIdentifier && <Detail label="ИНН / БИН" value={data.taxIdentifier}/>}
    </dl></section>
    <section className="staff-detail-section"><h3>Согласия</h3><dl className="staff-detail-grid">
      <Detail label="Локаль" value={consent.locale}/><Detail label="Версия оферты" value={consent.offerVersion}/>
      <Detail label="Оферта принята" value={dateTime(consent.offerAcceptedAt)}/><Detail label="Версия согласия на обработку данных" value={consent.privacyNoticeVersion}/>
      <Detail label="Согласие принято" value={dateTime(consent.privacyAcceptedAt)}/>
    </dl></section>
    {application.statusHistory?.length > 0 && <section className="staff-detail-section"><h3>История статусов</h3><ol className="staff-history">
      {application.statusHistory.map((event, index) => <li key={`${event.changedAt}-${index}`}><span>{statusLabels[event.status] ?? event.status}</span>
        <small>{dateTime(event.changedAt)} · {event.changedBy}</small>{event.reason && <p>{event.reason}</p>}</li>)}
    </ol></section>}
    {canDecide && !decided && <section className="staff-detail-section staff-decision-section"><h3>Решение</h3>
      <label className="staff-form-label">Причина для отказа или запроса изменений<textarea value={reason} onChange={event => onReason(event.target.value)} maxLength={1000} rows={3} placeholder="Обязательна для отказа и запроса изменений"/></label>
      <label className="staff-form-label">Ответственный менеджер при одобрении <input value={manager} onChange={event => onManager(event.target.value)} maxLength={120} placeholder="Логин сотрудника, необязательно"/></label>
      <div className="staff-decision-actions"><button className="primary" onClick={() => onDecision('approve')} disabled={busy}><Check size={18}/>Одобрить</button>
        <button className="staff-danger-button" onClick={() => onDecision('request-changes')} disabled={busy || !reason.trim()}>Запросить изменения</button>
        <button className="staff-danger-button" onClick={() => onDecision('reject')} disabled={busy || !reason.trim()}>Отклонить</button></div>
    </section>}
  </article>;
}

function Detail({ label, value }) {
  return <div><dt>{label}</dt><dd>{value || '—'}</dd></div>;
}

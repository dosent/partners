import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, ArrowClockwise, ClipboardText, FloppyDisk, X } from '@phosphor-icons/react';
import { assignStaffStaffingRequest, getStaffStaffingAssignees, getStaffStaffingRequestQueue } from '../api/staffStaffingRequestsApi';
import { StaffFrame } from './StaffLoginPage';
import './StaffStaffingRequestsPage.css';

const PAGE_SIZE = 20;
const statusLabels = { NEW: 'Новая' };
const dateTime = value => new Intl.DateTimeFormat('ru-RU', {
  dateStyle: 'medium', timeStyle: 'short',
}).format(new Date(value));

export function StaffStaffingRequestsPage({ session, onBack, onLogout }) {
  const canManage = (session.permissions ?? []).includes('staffing.request.manage');
  const [queue, setQueue] = useState(null);
  const [assignees, setAssignees] = useState([]);
  const [selection, setSelection] = useState({});
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [savingId, setSavingId] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const loadId = useRef(0);
  const busy = loading || savingId !== null;

  const loadQueue = useCallback(async (requestedPage = 0) => {
    const requestId = ++loadId.current;
    setLoading(true); setError(''); setNotice('');
    try {
      const [result, candidates] = await Promise.all([
        getStaffStaffingRequestQueue({ page: requestedPage, size: PAGE_SIZE }), getStaffStaffingAssignees(),
      ]);
      if (requestId !== loadId.current) return;
      setQueue(result); setPage(result.page);
      setAssignees(candidates.filter(candidate => candidate.role === 'RESOURCE_MANAGER'));
      setSelection(current => Object.fromEntries(result.items.map(item => [item.id,
        current[item.id] ?? item.assignedResourceManagerUsername ?? '',
      ])));
    } catch (requestError) {
      if (requestId === loadId.current) setError(requestError.message);
    } finally {
      if (requestId === loadId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canManage) loadQueue(0);
    return () => { loadId.current += 1; };
  }, [canManage, loadQueue]);

  async function saveAssignment(item, username) {
    if (busy) return;
    setSavingId(item.id); setError(''); setNotice('');
    try {
      const updated = await assignStaffStaffingRequest(item.id, username, item.version);
      setQueue(current => ({ ...current,
        items: current.items.map(row => row.id === updated.id ? updated : row),
      }));
      setSelection(current => ({ ...current, [item.id]: updated.assignedResourceManagerUsername ?? '' }));
      setNotice(username === null ? `Назначение заявки ${item.number} снято.`
        : `Ответственный для заявки ${item.number} сохранён.`);
    } catch (requestError) {
      setError(requestError.status === 409
        ? 'Заявка изменена другим сотрудником. Обновите очередь и повторите действие. Ваш выбор сохранён.'
        : requestError.message);
    } finally { setSavingId(null); }
  }

  if (!canManage) return <StaffFrame><section className="staff-card staff-state-card">
    <span className="staff-icon"><X size={27}/></span><h1>Недостаточно прав</h1>
    <p className="staff-intro">Для назначения ответственных требуется разрешение «Управление заявками на специалистов».</p>
    <button className="staff-secondary" onClick={onBack}><ArrowLeft size={18}/>В кабинет</button>
  </section></StaffFrame>;

  return <StaffFrame><div className="staff-staffing">
    <div className="staff-admin-top"><div><p className="staff-eyebrow">Рабочая очередь</p>
      <h1>Заявки на специалистов</h1></div><button className="staff-secondary" onClick={onLogout}>Выйти</button></div>
    {error && <p className="staff-error" role="alert">{error}</p>}
    {notice && <p className="staff-success" role="status">{notice}</p>}
    <div className="staff-staffing-toolbar"><span>{queue ? `Всего заявок: ${queue.total}` : 'Назначение ответственных'}</span>
      <button className="staff-secondary" onClick={() => loadQueue(page)} disabled={busy}>
        <ArrowClockwise size={17}/>Обновить</button></div>
    {loading && <div className="staff-staffing-state" role="status">Загружаем очередь заявок…</div>}
    {!loading && !queue && error && <button className="staff-secondary" onClick={() => loadQueue(page)}>Повторить загрузку</button>}
    {queue && !loading && queue.items.length === 0 && <div className="staff-staffing-state">
      <ClipboardText size={25}/><strong>Заявок пока нет</strong></div>}
    {queue && !loading && queue.items.length > 0 && <>
      {assignees.length === 0 && <p className="staff-staffing-note">Нет сотрудников с ролью Resource Manager для назначения.</p>}
      <div className="staff-staffing-list" aria-label="Очередь заявок на специалистов" aria-busy={busy}>
        {queue.items.map(item => {
          const selected = selection[item.id] ?? '';
          const current = item.assignedResourceManagerUsername ?? '';
          const eligible = assignees.some(candidate => candidate.username === selected);
          return <article className="staff-staffing-row" key={item.id}>
            <div className="staff-staffing-summary"><p className="staff-eyebrow">{item.number}</p>
              <h2>{item.title}</h2><p>{item.headcount} спец. · {statusLabels[item.status] ?? item.status}</p>
              <p>Создана {dateTime(item.createdAt)}</p>
              <p>Ответственный: <strong>{current || 'Не назначен'}</strong></p></div>
            <div className="staff-staffing-assignment"><label htmlFor={`assignee-${item.id}`}>Resource Manager</label>
              <select id={`assignee-${item.id}`} aria-label={`Ответственный для заявки ${item.number}`}
                value={selected} disabled={busy || assignees.length === 0}
                onChange={event => setSelection(values => ({ ...values, [item.id]: event.target.value }))}>
                <option value="">Выберите сотрудника</option>
                {selected && !eligible && <option value={selected} disabled>{selected} (недоступен для назначения)</option>}
                {assignees.map(candidate => <option value={candidate.username} key={candidate.username}>{candidate.username}</option>)}
              </select>
              <div className="staff-staffing-actions"><button className="primary"
                onClick={() => saveAssignment(item, selected)} disabled={busy || !eligible || selected === current}>
                <FloppyDisk size={17}/>{savingId === item.id ? 'Сохраняем…' : 'Сохранить назначение'}</button>
                <button className="staff-danger-button" onClick={() => saveAssignment(item, null)} disabled={busy || !current}>
                  <X size={17}/>Снять назначение</button></div>
            </div>
          </article>;
        })}
      </div>
      <div className="staff-staffing-pagination"><span>Страница {page + 1} из {Math.max(1, Math.ceil(queue.total / queue.size))}</span>
        <div><button className="staff-secondary" onClick={() => loadQueue(page - 1)} disabled={busy || page === 0}>
          <ArrowLeft size={17}/>Назад</button>
          <button className="staff-secondary" onClick={() => loadQueue(page + 1)} disabled={busy || (page + 1) * queue.size >= queue.total}>
            Далее<ArrowRight size={17}/></button></div></div>
    </>}
    <button className="staff-back-link" onClick={onBack}><ArrowLeft size={17}/>Вернуться в кабинет</button>
  </div></StaffFrame>;
}

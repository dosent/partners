import { useEffect, useMemo, useState } from 'react';
import { MagnifyingGlass, FloppyDisk, ArrowLeft, ShieldCheck, UserPlus } from '@phosphor-icons/react';
import { getStaffRoles, getStaffUsersWithRoles, replaceStaffRoles } from '../api/staffAccessApi';
import { StaffFrame } from './StaffLoginPage';
import './StaffRoleManagementPage.css';

const roleOptions = [
  ['PARTNER_MANAGER', 'Менеджер партнёров'],
  ['RESOURCE_MANAGER', 'Менеджер ресурсов'],
  ['FINANCE_CONTROLLER', 'Финансовый контролёр'],
  ['SYSTEM_ADMIN', 'Системный администратор'],
];

const rolesFrom = payload => payload.roles ?? payload.data?.roles ?? [];
const usersFrom = payload => payload.users ?? payload.data?.users ?? [];
const roleTitle = role => roleOptions.find(([value]) => value === role)?.[1] ?? role;

export function StaffRoleManagementPage({ session, onBack, onLogout }) {
  const [users, setUsers] = useState([]);
  const [filter, setFilter] = useState('');
  const [newUsername, setNewUsername] = useState('');
  const [foundUsername, setFoundUsername] = useState('');
  const [roles, setRoles] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const canManage = (session.permissions ?? []).includes('staff.access.manage');
  const visibleUsers = useMemo(() => {
    const query = filter.trim().toLowerCase();
    return users.filter(user => user.username.toLowerCase().includes(query));
  }, [users, filter]);

  useEffect(() => {
    if (!canManage) return;
    let active = true;
    setBusy(true);
    getStaffUsersWithRoles()
      .then(result => { if (active) setUsers(usersFrom(result)); })
      .catch(requestError => { if (active) setError(requestError.message); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [canManage]);

  async function refreshUsers() {
    const result = await getStaffUsersWithRoles();
    setUsers(usersFrom(result));
  }

  async function openUser(username, currentRoles) {
    setError(''); setNotice('');
    setFoundUsername(username);
    setRoles([...currentRoles]);
  }

  async function addUser(event) {
    event.preventDefault();
    const target = newUsername.trim();
    if (!target) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await getStaffRoles(target);
      const normalizedUsername = result.username ?? result.data?.username ?? target.toLowerCase();
      await openUser(normalizedUsername, rolesFrom(result));
    } catch (requestError) { setFoundUsername(''); setRoles([]); setError(requestError.message); }
    finally { setBusy(false); }
  }

  async function save() {
    if (!foundUsername) return;
    const description = roles.length ? roles.map(role => `${roleTitle(role)} (${role})`).join(', ') : 'нет назначенных ролей';
    if (!window.confirm(`Сохранить для логина «${foundUsername}» следующий набор ролей: ${description}?`)) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await replaceStaffRoles(foundUsername, roles);
      const savedRoles = rolesFrom(result);
      const savedUsername = result.username ?? result.data?.username ?? foundUsername;
      setRoles(savedRoles);
      setFoundUsername(savedUsername);
      await refreshUsers();
      setNewUsername('');
      setNotice(`Для «${savedUsername}» сохранены роли: ${savedRoles.length ? savedRoles.map(roleTitle).join(', ') : 'нет назначенных ролей'}.`);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }

  if (!canManage) return <StaffFrame><section className="staff-card staff-state-card"><h1>Недостаточно прав</h1><p className="staff-intro">У вашей учётной записи нет разрешения на управление доступом.</p><button className="staff-secondary" onClick={onBack}><ArrowLeft size={18}/>В кабинет</button></section></StaffFrame>;

  return <StaffFrame><div className="staff-admin-top"><div><p className="staff-eyebrow">Администрирование</p><h1>Доступ сотрудников</h1></div><button className="staff-secondary" onClick={onLogout}>Выйти</button></div>
    <section className="staff-card staff-role-card">
      <p className="staff-intro">Пользователи с назначенными ролями портала. Нового сотрудника добавьте по логину.</p>
      <label className="staff-search-label" htmlFor="staff-user-filter">Поиск по логину</label>
      <div className="staff-lookup-row"><MagnifyingGlass size={18}/><input id="staff-user-filter" value={filter} onChange={event => setFilter(event.target.value)} autoComplete="off" placeholder="Начните вводить логин"/></div>
      {error && <p className="staff-error" role="alert">{error}</p>}
      {notice && <p className="staff-success" role="status">{notice}</p>}
      {busy && !users.length && <p className="staff-intro" role="status">Загружаем список пользователей…</p>}
      {!busy && users.length === 0 && <div className="staff-empty-state"><h2>Назначений пока нет</h2><p>Введите логин сотрудника, чтобы назначить ему роли портала.</p></div>}
      {users.length > 0 && <div className="staff-users-table-wrap"><table className="staff-users-table"><thead><tr><th scope="col">Логин</th><th scope="col">Назначенные роли</th><th scope="col"><span className="sr-only">Действия</span></th></tr></thead><tbody>
        {visibleUsers.map(user => <tr key={user.username}><th scope="row">{user.username}</th><td>{user.roles.map(roleTitle).join(', ')}</td><td><button type="button" className="staff-text-button" onClick={() => openUser(user.username, user.roles)}>Изменить</button></td></tr>)}
      </tbody></table>{visibleUsers.length === 0 && <p className="staff-empty-filter">По запросу ничего не найдено.</p>}</div>}
      <form className="staff-lookup staff-add-user" onSubmit={addUser}><label htmlFor="staff-username">Добавить или открыть логин</label><div className="staff-lookup-row"><input id="staff-username" value={newUsername} onChange={event => setNewUsername(event.target.value)} autoComplete="off" maxLength={120} placeholder="например, ivan.ivanov" required/><button className="primary" type="submit" disabled={busy}><UserPlus size={18}/>{busy ? 'Открываем…' : 'Добавить логин'}</button></div></form>
      {foundUsername && <div className="staff-role-editor"><div className="staff-user"><span>Логин для изменения</span><strong>{foundUsername}</strong></div><h2><ShieldCheck size={21}/>Роли портала</h2><fieldset disabled={busy}><legend className="sr-only">Выберите роли</legend>{roleOptions.map(([role,label])=><label className="staff-role-option" key={role}><input type="checkbox" checked={roles.includes(role)} onChange={event=>setRoles(current=>event.target.checked?[...current,role]:current.filter(item=>item!==role))}/><span><strong>{label}</strong><small>{role}</small></span></label>)}</fieldset><button className="primary" onClick={save} disabled={busy}><FloppyDisk size={18}/>{busy?'Сохраняем…':'Сохранить роли'}</button></div>}
    </section>
    <button className="staff-back-link" onClick={onBack}><ArrowLeft size={17}/>Вернуться в кабинет</button>
  </StaffFrame>;
}

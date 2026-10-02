import { useState } from 'react';
import { MagnifyingGlass, FloppyDisk, ArrowLeft, ShieldCheck } from '@phosphor-icons/react';
import { getStaffRoles, replaceStaffRoles } from '../api/staffAccessApi';
import { StaffFrame } from './StaffLoginPage';

const roleOptions = [
  ['PARTNER_MANAGER', 'Менеджер партнёров'],
  ['RESOURCE_MANAGER', 'Менеджер ресурсов'],
  ['FINANCE_CONTROLLER', 'Финансовый контролёр'],
  ['SYSTEM_ADMIN', 'Системный администратор'],
];

function rolesFrom(payload) {
  return payload.roles ?? payload.data?.roles ?? [];
}

export function StaffRoleManagementPage({ session, onBack, onLogout }) {
  const [username, setUsername] = useState('');
  const [foundUsername, setFoundUsername] = useState('');
  const [roles, setRoles] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const canManage = (session.permissions ?? []).includes('staff.access.manage');

  async function lookup(event) {
    event.preventDefault();
    const target = username.trim();
    if (!target) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await getStaffRoles(target);
      setRoles(rolesFrom(result)); setFoundUsername(target);
    } catch (requestError) { setFoundUsername(''); setRoles([]); setError(requestError.message); }
    finally { setBusy(false); }
  }

  async function save() {
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await replaceStaffRoles(foundUsername, roles);
      setRoles(rolesFrom(result));
      setNotice('Назначения ролей сохранены. Изменения применятся при следующем запросе сотрудника.');
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  }

  if (!canManage) return <StaffFrame><section className="staff-card staff-state-card"><h1>Недостаточно прав</h1><p className="staff-intro">У вашей учётной записи нет разрешения на управление доступом.</p><button className="staff-secondary" onClick={onBack}><ArrowLeft size={18}/>В кабинет</button></section></StaffFrame>;

  return <StaffFrame><div className="staff-admin-top"><div><p className="staff-eyebrow">Администрирование</p><h1>Доступ сотрудников</h1></div><button className="staff-secondary" onClick={onLogout}>Выйти</button></div>
    <section className="staff-card staff-role-card">
      <p className="staff-intro">Найдите учётную запись и назначьте роли портала.</p>
      <form className="staff-lookup" onSubmit={lookup}><label htmlFor="staff-username">Логин сотрудника</label><div className="staff-lookup-row"><input id="staff-username" value={username} onChange={event=>setUsername(event.target.value)} autoComplete="off" maxLength={120} placeholder="например, ivan.ivanov" required/><button className="primary" type="submit" disabled={busy}><MagnifyingGlass size={18}/>{busy?'Поиск…':'Найти'}</button></div></form>
      {error && <p className="staff-error" role="alert">{error}</p>}
      {notice && <p className="staff-success" role="status">{notice}</p>}
      {foundUsername && <div className="staff-role-editor"><div className="staff-user"><span>Учётная запись</span><strong>{foundUsername}</strong></div><h2><ShieldCheck size={21}/>Роли портала</h2><fieldset disabled={busy}><legend className="sr-only">Выберите роли</legend>{roleOptions.map(([role,label])=><label className="staff-role-option" key={role}><input type="checkbox" checked={roles.includes(role)} onChange={event=>setRoles(current=>event.target.checked?[...current,role]:current.filter(item=>item!==role))}/><span><strong>{label}</strong><small>{role}</small></span></label>)}</fieldset><button className="primary" onClick={save} disabled={busy}><FloppyDisk size={18}/>{busy?'Сохраняем…':'Сохранить роли'}</button></div>}
    </section>
    <button className="staff-back-link" onClick={onBack}><ArrowLeft size={17}/>Вернуться в кабинет</button>
  </StaffFrame>;
}

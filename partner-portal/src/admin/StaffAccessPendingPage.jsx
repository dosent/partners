import { Clock, ArrowClockwise, SignOut } from '@phosphor-icons/react';
import { StaffFrame } from './StaffLoginPage';

export function StaffAccessPendingPage({ session, onRecheck, onLogout, loading, error }) {
  return <StaffFrame><section className="staff-card staff-state-card">
    <span className="staff-icon pending"><Clock size={27}/></span>
    <p className="staff-eyebrow">Учётная запись подтверждена</p>
    <h1>Доступ пока не выдан</h1>
    <p className="staff-intro">Для входа в рабочие разделы портала попросите администратора назначить вам роль.</p>
    <div className="staff-user"><span>Ваша учётная запись</span><strong>{session.username}</strong></div>
    {error && <p className="staff-error" role="alert">{error}</p>}
    <button className="primary" onClick={onRecheck} disabled={loading}>{loading ? 'Проверяем…' : 'Проверить доступ'}{!loading && <ArrowClockwise size={18}/>}</button>
    <button className="staff-secondary" onClick={onLogout}><SignOut size={18}/>Выйти</button>
  </section></StaffFrame>;
}

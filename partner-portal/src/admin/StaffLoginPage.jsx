import { useState } from 'react';
import { ArrowRight, ShieldCheck } from '@phosphor-icons/react';
import { loginStaff } from '../api/staffAccessApi';

export function StaffLoginPage({ onAuthenticated }) {
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setLoading(true);
    const form = new FormData(event.currentTarget);
    try {
      const session = await loginStaff(String(form.get('username')).trim(), String(form.get('password')));
      onAuthenticated(session);
    } catch (requestError) {
      if (requestError.status === 401) setError('Проверьте логин и пароль и попробуйте ещё раз.');
      else if (requestError.status === 503) setError('Вход недоступен: проверьте настройки и доступность корпоративного LDAP.');
      else setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  return <StaffFrame><section className="staff-card staff-login-card">
    <span className="staff-icon"><ShieldCheck size={27}/></span>
    <p className="staff-eyebrow">Для сотрудников SimbirSoft</p>
    <h1>Вход в портал</h1>
    <p className="staff-intro">Используйте корпоративную учётную запись.</p>
    <form className="staff-form" onSubmit={submit}>
      <label>Логин<input name="username" autoComplete="username" required maxLength={120} autoFocus/></label>
      <label>Пароль<input name="password" type="password" autoComplete="current-password" required/></label>
      {error && <p className="staff-error" role="alert">{error}</p>}
      <button className="primary" type="submit" disabled={loading}>{loading ? 'Выполняется вход…' : 'Войти'}{!loading && <ArrowRight size={18}/>}</button>
    </form>
  </section></StaffFrame>;
}

export function StaffFrame({ children }) {
  return <div className="staff-app"><a className="staff-brand" href="/"><img src="/assets/logo.svg" alt="SimbirSoft"/><span>Партнёрский портал</span></a><main className="staff-main">{children}</main><footer className="staff-footer">Корпоративный доступ SimbirSoft</footer></div>;
}

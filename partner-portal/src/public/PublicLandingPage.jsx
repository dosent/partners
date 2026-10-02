import { ArrowRight, CheckCircle, ShieldCheck, UserCircle } from '@phosphor-icons/react';
import './PublicLandingPage.css';

const steps = [
  ['01', 'Войдите через Яндекс ID', 'Авторизация помогает связать заявку с вашей учётной записью.'],
  ['02', 'Заполните заявку', 'Укажите контактные данные и выберите тип заявителя.'],
  ['03', 'Дождитесь решения', 'Сотрудник SimbirSoft проверит заявку вручную.'],
];

export function PublicLandingPage({ onRegister, onLogin }) {
  return <div className="public-landing">
    <header className="public-header">
      <a className="public-brand" href="/" aria-label="Партнёрский портал SimbirSoft">
        <img src="/assets/logo.svg" alt="SimbirSoft" />
        <span>Партнёрский портал</span>
      </a>
      <nav className="public-header-actions" aria-label="Навигация">
        <a className="public-staff-link" href="/staff">Для сотрудников</a>
        <button type="button" className="public-login-link" onClick={onLogin}><UserCircle size={19}/>Войти</button>
      </nav>
    </header>

    <main className="public-main">
      <section className="public-hero">
        <div className="public-hero-copy">
          <p className="public-eyebrow"><span/>Партнёрская программа SimbirSoft</p>
          <h1>Создавайте новые возможности вместе</h1>
          <p className="public-lead">Передайте информацию о потенциальном клиенте и отслеживайте заявку в личном кабинете после одобрения.</p>
          <div className="public-hero-actions">
            <button type="button" className="public-primary" onClick={onRegister}>Стать партнёром<ArrowRight size={19}/></button>
            <button type="button" className="public-secondary" onClick={onLogin}>Войти через Яндекс ID</button>
          </div>
          <p className="public-review-note"><ShieldCheck size={18}/>Каждая заявка проходит ручную проверку.</p>
        </div>
        <div className="public-hero-art" aria-hidden="true">
          <div className="public-orbit public-orbit-one"/><div className="public-orbit public-orbit-two"/>
          <div className="public-art-card"><span className="public-art-icon"><CheckCircle size={28}/></span><div><strong>Заявка партнёра</strong><small>Передана на проверку</small></div><span className="public-art-status">На рассмотрении</span></div>
          <div className="public-art-caption"><span/><span/><span/></div>
        </div>
      </section>

      <section className="public-process" aria-labelledby="public-process-title">
        <div className="public-section-heading"><p className="public-eyebrow">Как начать</p><h2 id="public-process-title">Три шага до рассмотрения заявки</h2></div>
        <div className="public-steps">{steps.map(([number, title, description]) => <article className="public-step" key={number}><span className="public-step-number">{number}</span><h3>{title}</h3><p>{description}</p></article>)}</div>
      </section>

      <section className="public-bottom-cta"><div><p className="public-eyebrow">Партнёрский портал</p><h2>Начните с регистрации</h2><p>Для подачи заявки потребуется войти через Яндекс ID.</p></div><button type="button" className="public-primary" onClick={onRegister}>Перейти к регистрации<ArrowRight size={19}/></button></section>
    </main>

    <footer className="public-footer"><span>© SimbirSoft</span><a href="/staff">Вход для сотрудников</a></footer>
  </div>;
}

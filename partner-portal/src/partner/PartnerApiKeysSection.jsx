import { useEffect, useState } from 'react';
import { createMyPartnerApiKey, listMyPartnerApiKeys, revokeMyPartnerApiKey } from '../api/partnerRegistrationApi.js';
import './PartnerApiKeysSection.css';

export function PartnerApiKeysSection({ content, onError }) {
  const labels = content?.strings ?? {};
  const text = key => labels[`portal.apiKeys.${key}`] ?? '';
  const mcpGuideKeys = ['mcp.guide.title', 'mcp.guide.steps', 'mcp.guide.endpoint', 'mcp.guide.example.title', 'mcp.guide.example', 'mcp.guide.security'];
  const showMcpGuide = mcpGuideKeys.every(key => text(key).trim());
  const [keys, setKeys] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [name, setName] = useState('');
  const [creating, setCreating] = useState(false);
  const [createdSecret, setCreatedSecret] = useState(null);
  const [revokeId, setRevokeId] = useState(null);
  const [revokingId, setRevokingId] = useState(null);
  const [failure, setFailure] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    listMyPartnerApiKeys().then(value => {
      if (active) { setKeys(Array.isArray(value) ? value : []); setLoadFailed(false); }
    }).catch(error => {
      if (active) { setLoadFailed(true); setFailure('load'); onError?.(error); }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const create = async event => {
    event.preventDefault();
    if (!name.trim()) return;
    setCreating(true); setFailure('');
    try {
      const result = await createMyPartnerApiKey(name.trim());
      const { secret, ...summary } = result;
      setKeys(current => [summary, ...current]);
      setCreatedSecret({ secret });
      setName('');
    } catch (error) { setFailure('create'); onError?.(error); }
    finally { setCreating(false); }
  };

  const revoke = async id => {
    setRevokingId(id); setFailure('');
    try {
      await revokeMyPartnerApiKey(id);
      setKeys(current => current.map(key => key.id === id ? { ...key, revokedAt: new Date().toISOString() } : key));
      setRevokeId(null);
    } catch (error) { setFailure('revoke'); onError?.(error); }
    finally { setRevokingId(null); }
  };

  const closeSecret = () => setCreatedSecret(null);
  return <section className="partner-api-keys" aria-labelledby="partner-api-keys-title">
    <h2 id="partner-api-keys-title">{text('title')}</h2>
    <p>{text('description')}</p>
    {showMcpGuide && <aside className="partner-api-key-guide" aria-labelledby="partner-api-key-guide-title">
      <h3 id="partner-api-key-guide-title">{text('mcp.guide.title')}</h3>
      <p>{text('mcp.guide.steps')}</p>
      <p>{text('mcp.guide.endpoint')}</p>
      <h4>{text('mcp.guide.example.title')}</h4>
      <pre><code>{text('mcp.guide.example')}</code></pre>
      <p>{text('mcp.guide.security')}</p>
    </aside>}
    <form className="partner-api-key-create" onSubmit={create}>
      <label htmlFor="partner-api-key-name">{text('name.label')}</label>
      <input id="partner-api-key-name" value={name} onChange={event => setName(event.target.value)} maxLength={100} required />
      <button className="secondary" type="submit" disabled={creating || !name.trim()}>{creating ? text('actions.creating') : text('actions.create')}</button>
    </form>
    {failure && <p className="partner-api-key-error" role="alert">{text(`errors.${failure}`)}</p>}
    {loading && <p role="status" aria-busy="true">{text('loading')}</p>}
    {!loading && !loadFailed && keys.length === 0 && <p>{text('empty')}</p>}
    {!loading && keys.length > 0 && <ul className="partner-api-key-list">{keys.map(key => <li key={key.id}>
      <div><strong>{key.name}</strong><code>{key.prefix}</code><small>{key.revokedAt ? text('status.revoked') : `${text('status.created')}: ${key.createdAt}`}</small></div>
      {!key.revokedAt && (revokeId === key.id ? <span className="partner-api-key-confirm">
        <span>{text('revoke.confirm')}</span>
        <button type="button" className="danger" disabled={revokingId === key.id} onClick={() => revoke(key.id)}>{text('actions.revoke')}</button>
        <button type="button" className="secondary" onClick={() => setRevokeId(null)}>{text('actions.cancel')}</button>
      </span> : <button type="button" className="danger" onClick={() => setRevokeId(key.id)}>{text('actions.revoke')}</button>)}
    </li>)}</ul>}
    {createdSecret && <div className="partner-api-key-secret" role="region" aria-live="polite" aria-labelledby="partner-api-key-secret-title">
      <h3 id="partner-api-key-secret-title">{text('secret.title')}</h3>
      <p>{text('secret.description')}</p>
      <code tabIndex="0">{createdSecret.secret}</code>
      <button className="secondary" type="button" onClick={closeSecret}>{text('actions.cancel')}</button>
    </div>}
  </section>;
}

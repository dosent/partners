import { useRef, useState } from 'react';

export function validCandidateFile(file) {
  if (!file || file.size === 0 || file.size > 20 * 1024 * 1024) return false;
  const extension = file.name.split('.').pop()?.toLowerCase();
  return (extension === 'pdf' && (!file.type || file.type === 'application/pdf'))
    || (extension === 'docx' && (!file.type || file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'));
}
const fieldName = field => ({ 'hourlyRate.amount': 'amount', 'hourlyRate.currency': 'currency', experienceMonths: 'experienceYears' })[field] ?? field;

export function CandidateForm({ initialValue, text, onSave, onCancel, busy, fieldErrors = [], uncertain = false, retryNeedsFile = false }) {
  const editing = Boolean(initialValue?.id);
  const [values, setValues] = useState(() => ({
    role: initialValue?.role ?? '', grade: initialValue?.grade ?? '', externalKey: initialValue?.externalKey ?? '',
    skills: initialValue?.skills?.join(', ') ?? '',
    experienceYears: initialValue ? String(Math.floor(initialValue.experienceMonths / 12)) : '',
    experienceMonths: initialValue ? String(initialValue.experienceMonths % 12) : '',
    description: initialValue?.description ?? '', amount: initialValue?.hourlyRate?.amount ?? '',
    currency: initialValue?.hourlyRate?.currency ?? '', availability: initialValue?.availability ?? '',
  }));
  const [file, setFile] = useState(null);
  const [errors, setErrors] = useState({});
  const submitLock = useRef(false);
  const errorFor = name => errors[name] || fieldErrors.some(error => fieldName(error.field) === name);
  const change = event => {
    const { name, value } = event.target;
    setValues(current => ({ ...current, [name]: value }));
    setErrors(current => ({ ...current, [name]: false }));
  };
  async function submit(event) {
    event.preventDefault();
    if (busy || submitLock.current) return;
    const skills = values.skills.split(',').map(value => value.trim()).filter(Boolean);
    const years = Number(values.experienceYears), months = Number(values.experienceMonths);
    const amount = values.amount.trim();
    const invalid = {
      role: !values.role.trim() || values.role.trim().length > 200,
      grade: values.grade.trim().length > 80,
      externalKey: values.externalKey.trim().length > 200 || /[\u0000-\u001f\u007f-\u009f]/.test(values.externalKey),
      skills: skills.length === 0 || skills.length > 40 || skills.some(value => value.length > 120) || new Set(skills).size !== skills.length,
      experienceYears: values.experienceYears === '' || !Number.isInteger(years) || years < 0 || years > 60,
      experienceMonths: values.experienceMonths === '' || !Number.isInteger(months) || months < 0 || months > 11 || years * 12 + months > 720,
      description: !values.description.trim() || values.description.trim().length > 10000,
      amount: !/^\d{1,10}(?:\.\d{1,2})?$/.test(amount) || !/[1-9]/.test(amount),
      currency: !/^[A-Z]{3}$/.test(values.currency.trim()),
      availability: !['AVAILABLE', 'UNAVAILABLE'].includes(values.availability),
      cv: !editing && !validCandidateFile(file),
    };
    // Retry uses the immutable create snapshot retained by the page, including its file.
    if (!uncertain && Object.values(invalid).some(Boolean)) { setErrors(invalid); return; }
    submitLock.current = true;
    try {
      await onSave({ profile: {
        role: values.role.trim(), grade: values.grade.trim() || null, externalKey: values.externalKey.trim() || null,
        skills, experienceMonths: years * 12 + months, description: values.description.trim(),
        availability: values.availability, hourlyRate: { amount, currency: values.currency.trim() },
      }, cv: file });
    } finally { submitLock.current = false; }
  }
  function field(name, title, input, help) {
    return <div className={`candidate-field ${name === 'description' || name === 'skills' ? 'candidate-wide' : ''}`}>
      <label htmlFor={`candidate-${name}`}>{title}</label>
      {input({ id: `candidate-${name}`, name, value: values[name], onChange: change,
        disabled: busy || uncertain, 'aria-invalid': errorFor(name) ? true : undefined,
        'aria-describedby': errorFor(name) ? `candidate-${name}-error` : help ? `candidate-${name}-help` : undefined })}
      {help && <small id={`candidate-${name}-help`}>{help}</small>}
      {errorFor(name) && <small className="candidate-field-error" id={`candidate-${name}-error`}>{text.invalid}</small>}
    </div>;
  }
  return <form className="candidate-form" onSubmit={submit} noValidate aria-busy={busy}>
    {Object.values(errors).some(Boolean) && <p className="staff-error" role="alert">{errors.cv ? text.invalidFile : text.invalid}</p>}
    {uncertain && <p className="candidate-note" role="status">{text.retryHelp}</p>}
    <div className="candidate-fields">
      {field('role', text.role, props => <input {...props} required maxLength={200}/>)}
      {field('grade', text.grade, props => <input {...props} maxLength={80}/>)}
      {field('externalKey', text.externalKey, props => <input {...props} maxLength={200}/>, text.externalHelp)}
      {field('availability', text.filter, props => <select {...props} required><option value="">{text.choose}</option><option value="AVAILABLE">{text.available}</option><option value="UNAVAILABLE">{text.unavailable}</option></select>, text.manual)}
      {field('skills', text.skills, props => <textarea {...props} required rows={2} maxLength={5000}/>, text.skillsHelp)}
      {field('experienceYears', text.years, props => <input {...props} required inputMode="numeric" type="number" min="0" max="60" step="1"/>)}
      {field('experienceMonths', text.months, props => <input {...props} required inputMode="numeric" type="number" min="0" max="11" step="1"/>)}
      {field('description', text.description, props => <textarea {...props} required rows={6} maxLength={10000}/>)}
      {field('amount', text.amount, props => <input {...props} required inputMode="decimal" maxLength={13}/>)}
      {field('currency', text.currency, props => <input {...props} required maxLength={3} autoCapitalize="characters"/>)}
      {!editing && <div className="candidate-field candidate-wide"><label htmlFor="candidate-cv">{text.cv}</label>
        <input id="candidate-cv" name="cv" type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          disabled={busy || (uncertain && !retryNeedsFile)} required onChange={event => { setFile(event.target.files?.[0] ?? null); setErrors(current => ({ ...current, cv: false })); }}
          aria-invalid={errorFor('cv') ? true : undefined} aria-describedby="candidate-cv-help"/>
        <small id="candidate-cv-help">{text.fileHelp}</small>{errorFor('cv') && <small className="candidate-field-error">{text.invalidFile}</small>}
      </div>}
    </div>
    <div className="candidate-actions"><button type="submit" className="primary" disabled={busy}>{busy ? text.saving : uncertain ? text.retryCreate : text.save}</button>
      <button type="button" className="secondary" onClick={onCancel} disabled={busy || uncertain}>{text.cancel}</button></div>
  </form>;
}

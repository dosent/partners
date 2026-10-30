// Optional localization keys: registration publication does not require this catalogue.
export const CANDIDATE_TEXT_KEYS = [
  'title', 'create', 'loading', 'empty', 'refresh', 'search', 'filter', 'all',
  'available', 'unavailable', 'manual', 'previous', 'next', 'page', 'total', 'open',
  'edit', 'back', 'home', 'logout', 'locale', 'role', 'grade', 'externalKey', 'externalHelp',
  'skills', 'skillsHelp', 'years', 'months', 'description', 'amount', 'currency', 'perHour',
  'choose', 'cv', 'fileHelp', 'save', 'saving', 'cancel', 'download', 'replace',
  'updatedAt', 'rateChangedAt', 'rateChangedBy', 'saved', 'error', 'conflict', 'invalid',
  'invalidFile', 'serviceUnavailable', 'rejectedFile', 'externalKeyConflict', 'forbidden', 'retry', 'retryCreate', 'retryHelp', 'missingFile',
];
export function candidateText(content) {
  const strings = content?.strings;
  if (!CANDIDATE_TEXT_KEYS.every(key => typeof strings?.[`candidates.${key}`] === 'string' && strings[`candidates.${key}`].trim())) return null;
  return Object.fromEntries(CANDIDATE_TEXT_KEYS.map(key => [key, strings[`candidates.${key}`]]));
}

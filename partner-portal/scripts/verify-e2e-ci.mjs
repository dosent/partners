import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const pipelinePath = resolve(projectRoot, '..', '.gitlab-ci.yml');
const jobName = 'partner-portal-e2e';
const errors = [];

function validateRules(rules) {
  const conditions = Array.isArray(rules)
    ? rules.map((rule) => String(rule?.if ?? '').replaceAll("'", '"').trim())
    : [];
  const ruleErrors = [];

  if (!conditions.some((condition) => /\$CI_PIPELINE_SOURCE\s*==\s*"merge_request_event"/.test(condition))) {
    ruleErrors.push('rules must include merge request pipelines ($CI_PIPELINE_SOURCE == "merge_request_event").');
  }
  if (!conditions.some((condition) => /\$CI_COMMIT_BRANCH\s*==\s*"main"/.test(condition))) {
    ruleErrors.push('rules must include the main branch ($CI_COMMIT_BRANCH == "main").');
  }
  if (!conditions.some((condition) => /\$CI_COMMIT_BRANCH\b/.test(condition))) {
    ruleErrors.push('rules must include branch pipelines ($CI_COMMIT_BRANCH).');
  }

  return ruleErrors;
}

let pipeline;
try {
  pipeline = YAML.parse(await readFile(pipelinePath, 'utf8'));
} catch (error) {
  console.error(`ERROR: Cannot read or parse .gitlab-ci.yml: ${error.message}`);
  process.exit(1);
}

const job = pipeline?.[jobName];

if (!job || typeof job !== 'object') {
  errors.push(`Missing GitLab CI job "${jobName}".`);
} else {
  const script = Array.isArray(job.script) ? job.script.join('\n') : String(job.script ?? '');
  if (!/(^|\s)npm run test:e2e(?:\s|$)/m.test(script)) {
    errors.push(`${jobName} must run "npm run test:e2e".`);
  }

  errors.push(...validateRules(job.rules).map((error) => `${jobName} ${error}`));

  const artifacts = job.artifacts;
  if (artifacts?.when !== 'always') {
    errors.push(`${jobName} artifacts.when must be "always".`);
  }

  const paths = Array.isArray(artifacts?.paths) ? artifacts.paths : [];
  const normalizedPaths = paths.map((path) => String(path).replaceAll('\\', '/'));
  if (!normalizedPaths.some((path) => path.endsWith('playwright-report/')))
    errors.push(`${jobName} artifacts.paths must include the Playwright HTML report directory.`);
  if (!normalizedPaths.some((path) => path.endsWith('test-results/')))
    errors.push(`${jobName} artifacts.paths must include the Playwright test-results directory.`);
  if (!normalizedPaths.some((path) => path.endsWith('playwright-report/e2e-services.log')))
    errors.push(`${jobName} artifacts.paths must include the Compose service log.`);
}

if (errors.length > 0) {
  console.error(errors.map((error) => `ERROR: ${error}`).join('\n'));
  process.exitCode = 1;
} else {
  console.log(`GitLab CI E2E job "${jobName}" and diagnostic artifacts are configured.`);
  const incompleteRules = validateRules([
    { if: '$CI_PIPELINE_SOURCE == "merge_request_event"' },
    { if: '$CI_COMMIT_BRANCH' },
  ]);
  if (!incompleteRules.some((error) => error.includes('main branch'))) {
    console.error('ERROR: Negative rules check did not reject a missing main-branch rule.');
    process.exitCode = 1;
  } else {
    console.log('Negative check passed: a missing main-branch rule is rejected.');
  }
}

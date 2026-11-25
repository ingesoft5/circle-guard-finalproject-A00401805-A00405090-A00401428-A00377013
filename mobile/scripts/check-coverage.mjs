#!/usr/bin/env node
// CG-DOC: Explicit Jest coverage gate for the Expo mobile client (E2.7 / DCG-5).
// Jest applies the thresholds declared in package.json; this check recalculates
// them from coverage-summary.json so the scope and the numbers remain visible.
// Core modules keep the 60% gate. Expo route modules under app/ are included in
// the report with a 0% baseline because they have no dedicated unit tests yet;
// the baseline is explicit and is never presented as adequate coverage.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const summaryPath = path.join(root, 'coverage', 'coverage-summary.json');
const metrics = ['lines', 'statements', 'functions', 'branches'];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Minimal glob matcher for the path patterns used in Jest coverageThreshold. */
function globToRegExp(glob) {
  const normalized = glob.replace(/^\.\//, '').replaceAll('\\', '/');
  let source = '^';
  for (let i = 0; i < normalized.length; i += 1) {
    const char = normalized[i];
    if (char === '*') {
      if (normalized[i + 1] === '*') {
        source += '.*';
        i += 1;
        if (normalized[i + 1] === '/') i += 1;
      } else {
        source += '[^/]*';
      }
    } else if (char === '{') {
      const end = normalized.indexOf('}', i);
      if (end === -1) {
        source += '\\{';
      } else {
        const options = normalized.slice(i + 1, end).split(',');
        source += `(?:${options.map(escapeRegExp).join('|')})`;
        i = end;
      }
    } else {
      source += escapeRegExp(char);
    }
  }
  return new RegExp(`${source}$`);
}

let summary;
let packageJson;
try {
  summary = JSON.parse(readFileSync(summaryPath, 'utf8'));
  packageJson = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'));
} catch (error) {
  console.error(`[error] Cannot read coverage inputs: ${error.message}`);
  process.exit(1);
}

const thresholds = packageJson.jest?.coverageThreshold;
if (!thresholds || Object.keys(thresholds).length === 0) {
  console.error('[error] package.json has no jest.coverageThreshold groups');
  process.exit(1);
}

const files = Object.keys(summary)
  .filter((key) => key !== 'total')
  .map((key) => ({
    key,
    relative: path.relative(root, key).replaceAll('\\', '/'),
  }));

const failures = [];
for (const [glob, minimum] of Object.entries(thresholds)) {
  const matcher = globToRegExp(glob);
  const matched = files.filter((file) => matcher.test(file.relative));
  if (matched.length === 0) {
    failures.push(`${glob}: no files found in coverage-summary.json`);
    continue;
  }

  const percentages = [];
  for (const metric of metrics) {
    const totals = matched.reduce(
      (acc, file) => {
        const entry = summary[file.key][metric] ?? {};
        acc.total += entry.total ?? 0;
        acc.covered += entry.covered ?? 0;
        return acc;
      },
      { total: 0, covered: 0 },
    );
    const pct = totals.total === 0 ? 100 : (totals.covered / totals.total) * 100;
    const required = minimum[metric];
    percentages.push(`${metric}=${pct.toFixed(2)}%`);
    if (typeof required !== 'number' || pct + 1e-9 < required) {
      failures.push(`${glob} ${metric}: ${pct.toFixed(2)}% < ${required ?? 'missing'}`);
    }
  }
  console.log(`[ok] ${glob} (${matched.length} files): ${percentages.join(', ')}`);
}

if (failures.length > 0) {
  console.error(`[error] mobile coverage gate failed:\n  ${failures.join('\n  ')}`);
  process.exit(1);
}

const total = summary.total;
console.log(
  `[info] all included files: ` +
    metrics.map((metric) => `${metric}=${total[metric]?.pct?.toFixed(2) ?? 'n/a'}%`).join(', '),
);

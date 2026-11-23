#!/usr/bin/env node
// CG-DOC: Explicit Jest coverage gate for the Expo mobile client (E2.7 / DCG-5).
// jest.config coverageThreshold already fails the run, but this check keeps the
// 60% rule visible and verifiable from the generated JSON summary.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MINIMUM = 60;
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const summaryPath = path.join(root, 'coverage', 'coverage-summary.json');

let summary;
try {
  summary = JSON.parse(readFileSync(summaryPath, 'utf8'));
} catch (error) {
  console.error(`[error] Cannot read ${summaryPath}: ${error.message}`);
  process.exit(1);
}

const total = summary.total;
if (!total) {
  console.error('[error] coverage-summary.json has no "total" section');
  process.exit(1);
}

const metrics = ['lines', 'statements', 'functions', 'branches'];
const failed = [];

for (const metric of metrics) {
  const pct = total[metric]?.pct;
  if (typeof pct !== 'number' || Number.isNaN(pct)) {
    failed.push(`${metric}: missing value`);
    continue;
  }
  if (pct < MINIMUM) {
    failed.push(`${metric}: ${pct.toFixed(2)}% < ${MINIMUM}%`);
  }
}

if (failed.length > 0) {
  console.error(`[error] mobile coverage gate failed:\n  ${failed.join('\n  ')}`);
  process.exit(1);
}

console.log(
  `[ok] mobile coverage gate >= ${MINIMUM}%: ` +
    metrics.map((metric) => `${metric}=${total[metric].pct.toFixed(2)}%`).join(', '),
);

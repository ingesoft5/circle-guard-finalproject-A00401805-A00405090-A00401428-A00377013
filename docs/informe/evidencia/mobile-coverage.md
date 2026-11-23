<!-- CG-DOC: Evidence of the explicit mobile coverage gate above 60%. -->
# Mobile coverage gate (E2.7 / DCG-5)

## Gate

- `mobile/package.json` declares `jest.coverageThreshold.global` at **60 %** for
  `lines`, `statements`, `functions` and `branches`.
- `npm run test:coverage` adds the `json-summary` reporter and executes
  `mobile/scripts/check-coverage.mjs`, which fails when any global metric is below 60 %.
- Jenkins runs the same command in the `Mobile tests + coverage` stage
  (`Jenkinsfile`) and archives `lcov.info` plus the HTML report.

## Measured result

Command:

```bash
cd mobile
npm ci --no-audit --no-fund
npm run test:coverage -- --runInBand
```

Result:

| Metric | Value | Gate |
|---|---:|---:|
| Statements | 99.76 % | >= 60 % |
| Branches | 87.23 % | >= 60 % |
| Functions | 100 % | >= 60 % |
| Lines | 99.75 % | >= 60 % |

Tests: **20 suites, 92 tests passed**. The generated coverage reports are build
artifacts and are not committed as source changes.

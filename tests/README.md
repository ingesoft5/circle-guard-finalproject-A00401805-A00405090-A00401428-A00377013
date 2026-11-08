<!-- CG-DOC: Test suites, end-to-end execution, and required environment variables. -->
# Tests

## Purpose

`tests/` contains execution-level verification for the CircleGuard platform.

## End-to-end tests

The `e2e/` suite uses Playwright against a deployed gateway.

```bash
cd tests/e2e
npm install
npx playwright install --with-deps chromium
GATEWAY_URL=http://<ENVS_IP>:8087 npx playwright test
```

Covered checks:

- gateway health endpoint returns `UP`;
- gateway rejects requests without a staff JWT.

## Jenkins integration

The pipeline runs the suite after the `dev` deployment:

```text
E2E → GATEWAY_URL=http://<ENVS_IP>:8087
```

## Requirements

- Deployed `dev` gateway.
- Network access from the Jenkins host to the gateway port.
- Playwright Chromium dependencies.

## Documentation

Every test file includes a `CG-DOC` purpose header and documents the behavior under test.

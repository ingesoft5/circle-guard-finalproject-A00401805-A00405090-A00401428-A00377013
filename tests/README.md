<!-- CG-DOC: Test suites, end-to-end execution, and required environment variables. -->
# Tests

## Purpose

`tests/` contains execution-level verification for the CircleGuard platform.

## End-to-end tests

The `e2e/` suite uses Playwright against the deployed Expo web client and the
gateway.

```bash
cd tests/e2e
npm install
npx playwright install --with-deps chromium
WEB_URL=http://<ENVS_IP>:8081 \
GATEWAY_URL=http://<ENVS_IP>:8087 \
npx playwright test
```

Covered checks:

- `login.spec.ts`: secure login screen and required credentials;
- `health-form.spec.ts`: health questionnaire route and fallback;
- `access-validation.spec.ts`: gateway health and rejected access without a staff JWT;
- `promotion-flow.spec.ts` when the promotion stack is deployed.

## Jenkins integration

The pipeline runs the suite after the `dev` deployment:

```text
E2E → WEB_URL=http://<ENVS_IP>:8081, GATEWAY_URL=http://<ENVS_IP>:8087
```

## Requirements

- Deployed `dev` web client and gateway.
- Network access from the Jenkins host to ports 8081 and 8087.
- Playwright Chromium dependencies.

## Documentation

Every test file includes a `CG-DOC` purpose header and documents the behavior under test.

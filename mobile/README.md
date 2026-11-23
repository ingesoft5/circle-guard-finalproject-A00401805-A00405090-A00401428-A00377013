<!-- CG-DOC: Expo mobile client structure, configuration, and test commands. -->
# Mobile

## Purpose

`mobile/` contains the CircleGuard Expo client for students, visitors, gate staff, and administrators.

## Structure

```text
mobile/
├── app/          # Expo Router screens and layouts
├── components/   # Reusable React Native components
├── constants/    # Shared colors and configuration
├── context/      # Authentication context
├── hooks/        # Data, auth, QR, proximity, and permission hooks
├── utils/        # Storage, proximity scanning, and background tasks
└── assets/       # Fonts and images
```

## Requirements

- Node.js 20 or newer.
- Expo CLI through `npx expo`.
- Backend services reachable from the device or emulator.

## Commands

```bash
npm install
npm start
npm run android
npm run ios
npm run web
npm test
npm run test:coverage
```

`npm run test:coverage` generates `coverage/lcov.info`, HTML reports and
`coverage/coverage-summary.json`, then runs `scripts/check-coverage.mjs`. The script
fails if any global metric (`lines`, `statements`, `functions`, `branches`) is below
**60 %**. The same rule is declared in `package.json` under `jest.coverageThreshold`.

## Configuration

- `app.json` defines the Expo application metadata.
- `constants/Config.ts` resolves backend service URLs.
- `metro.config.js` and `babel.config.js` configure the bundler.
- `tailwind.config.js` configures NativeWind styling.

## Documentation

- Exported hooks, components, contexts, and utilities use TSDoc in English.
- Every source file includes a `CG-DOC` purpose header.
- No secrets or production credentials are stored in the mobile project.

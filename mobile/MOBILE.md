<!-- CG-DOC: Estructura, configuración y comandos de prueba del cliente móvil Expo. -->
# Móvil

## Propósito

`mobile/` contiene el cliente Expo de CircleGuard para estudiantes, visitantes, personal de control de acceso y administradores.

## Estructura

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

## Requisitos

- Node.js 20 o superior.
- Expo CLI mediante `npx expo`.
- Servicios backend accesibles desde el dispositivo o el emulador.

## Comandos

```bash
npm install
npm start
npm run android
npm run ios
npm run web
npm test
npm run test:coverage
```

`npm run test:coverage` genera `coverage/lcov.info`, informes HTML y
`coverage/coverage-summary.json`, y luego ejecuta `scripts/check-coverage.mjs`. El script
termina con error si alguna métrica global (`lines`, `statements`, `functions`, `branches`) está por debajo
del **60 %**. La misma regla se declara en `package.json` bajo `jest.coverageThreshold`.

## Configuración

- `app.json` define los metadatos de la aplicación Expo.
- `constants/Config.ts` resuelve las URLs de los servicios backend.
- `metro.config.js` y `babel.config.js` configuran el empaquetador.
- `tailwind.config.js` configura los estilos de NativeWind.

## Documentación

- Los hooks, componentes, contextos y utilidades exportados usan TSDoc en inglés.
- Cada archivo de código incluye una cabecera de propósito `CG-DOC`.
- El proyecto móvil no almacena secretos ni credenciales de producción.

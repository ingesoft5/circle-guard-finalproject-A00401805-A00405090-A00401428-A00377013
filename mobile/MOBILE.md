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
`coverage/coverage-summary.json`, y luego ejecuta `scripts/check-coverage.mjs`. La
configuración de Jest incluye ahora `app/**/*.{ts,tsx}` en `collectCoverageFrom`,
por lo que las pantallas y layouts de Expo Router aparecen en el informe; antes
solo se medían `hooks/`, `utils/`, `constants/` y `components/`.

La compuerta es por grupos y se declara en `package.json` bajo
`jest.coverageThreshold`; `scripts/check-coverage.mjs` recalcula cada grupo desde
el JSON generado y falla si no alcanza su mínimo:

| Grupo | Mínimo (`lines`, `statements`, `functions`, `branches`) |
|---|---|
| `hooks/**`, `utils/**`, `constants/**`, `components/**` | 60 % (sin cambios) |
| `app/**` (rutas de Expo Router) | 0 % (línea base explícita) |

Las rutas `app/` no tienen pruebas unitarias dedicadas todavía: se incluyen en
el informe para que su ausencia de cobertura sea visible y verificable, pero el
**0 % es una línea base, no una afirmación de cobertura suficiente**. El total
agregado de todos los archivos incluidos se imprime solo como información
(`[info]`), no como compuerta. `jest.restoreMocks = true` restaura los spies
entre casos para que un test no herede dobles configurados por otro.

## Configuración

- `app.json` define los metadatos de la aplicación Expo.
- `constants/Config.ts` resuelve las URLs de los servicios backend.
- `metro.config.js` y `babel.config.js` configuran el empaquetador.
- `tailwind.config.js` configura los estilos de NativeWind.

## Documentación

- Los hooks, componentes, contextos y utilidades exportados usan TSDoc en inglés.
- Cada archivo de código incluye una cabecera de propósito `CG-DOC`.
- El proyecto móvil no almacena secretos ni credenciales de producción.

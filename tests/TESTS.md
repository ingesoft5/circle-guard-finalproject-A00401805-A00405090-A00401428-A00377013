<!-- CG-DOC: Suites de pruebas, ejecución de extremo a extremo y variables de entorno requeridas. -->
# Pruebas

## Propósito

`tests/` contiene la verificación a nivel de ejecución de la plataforma CircleGuard.

## Pruebas de extremo a extremo

La suite `e2e/` usa Playwright contra el cliente web de Expo desplegado y el
gateway.

```bash
cd tests/e2e
npm install
npx playwright install --with-deps chromium
WEB_URL=http://<ENVS_IP>:8081 \
GATEWAY_URL=http://<ENVS_IP>:8087 \
npx playwright test
```

Comprobaciones cubiertas:

- `login.spec.ts`: pantalla de inicio de sesión segura y credenciales obligatorias;
- `health-form.spec.ts`: ruta del cuestionario de salud y comportamiento alternativo;
- `access-validation.spec.ts`: salud del gateway y acceso rechazado sin JWT de personal;
- `promotion-flow.spec.ts` cuando el stack de Promotion está desplegado.

## Integración con Jenkins

La canalización ejecuta la suite tras el despliegue en `dev`:

```text
E2E → WEB_URL=http://<ENVS_IP>:8081, GATEWAY_URL=http://<ENVS_IP>:8087
```

## Requisitos

- Cliente web y gateway de `dev` desplegados.
- Acceso de red desde el host de Jenkins a los puertos 8081 y 8087.
- Dependencias de Chromium de Playwright.

## Documentación

Cada archivo de prueba incluye una cabecera de propósito `CG-DOC` y documenta el comportamiento bajo prueba.

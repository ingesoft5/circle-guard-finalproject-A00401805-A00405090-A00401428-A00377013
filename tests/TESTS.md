<!-- CG-DOC: Suites de pruebas, ejecución de extremo a extremo, inventario de evidencia y variables requeridas. -->
# Pruebas

## Propósito y alcance

`tests/` reúne la verificación de la plataforma CircleGuard en tres niveles:

| Nivel | Dónde se ejecuta | Comando de referencia | Qué demuestra |
|---|---|---|---|
| Unitario Java | Gradle `test` / `check` | `./gradlew :servicio:check --no-daemon` | Comportamiento de clases con dependencias simuladas; cobertura JaCoCo oficial. |
| Integración Java | Gradle `integrationTest` | `./gradlew :servicio:integrationTest --no-daemon` | Contratos que levantan al menos un Testcontainers. Gate independiente. |
| E2E web/API | Playwright sobre el Ingress k3d | `cd tests/e2e && npx playwright test` | Flujos visibles desde fuera del clúster, incluidos Kafka y Neo4j vía `kubectl exec`. |

Las cifras históricas se leen de los artefactos archivados; **no se reconstruyen**
ni se presentan como resultado de la corrida actual. El inventario detallado está en
[`docs/informe/evidencia/validacion-final/tests/README.md`](../docs/informe/evidencia/validacion-final/tests/README.md).

## Pruebas de extremo a extremo (Playwright)

La suite `tests/e2e/` apunta al Ingress de Traefik del clúster on-prem en el
puerto **30080**. Todas las URLs de servicio que usa Jenkins (`WEB_URL`,
`GATEWAY_URL`, `AUTH_URL`, `IDENTITY_URL`, `PROMOTION_URL`) son el mismo host
`http://dev.circleguard.local:30080`; el Ingress enruta por cabecera `Host`.
`E2E_HOST_RESOLVER='MAP dev.circleguard.local 127.0.0.1'` permite al navegador
resolver ese host sin editar `/etc/hosts`. `globalSetup` comprueba el Ingress
en `http://127.0.0.1:30080` con `Host: dev.circleguard.local` antes de ejecutar
cualquier spec: si no responde, la corrida falla rápido.

```bash
cd tests/e2e
npm install
npx playwright install --with-deps chromium
WEB_URL=http://dev.circleguard.local:30080 \
GATEWAY_URL=http://dev.circleguard.local:30080 \
AUTH_URL=http://dev.circleguard.local:30080 \
IDENTITY_URL=http://dev.circleguard.local:30080 \
PROMOTION_URL=http://dev.circleguard.local:30080 \
E2E_HOST_RESOLVER='MAP dev.circleguard.local 127.0.0.1' \
E2E_ANONYMOUS_ID=e2e-local-000 \
npx playwright test
```

### Cobertura de la suite (5 specs / 19 casos)

| Spec | Casos | Qué cubre |
|---|---:|---|
| `access-validation.spec.ts` | 9 | Salud del gateway, rechazo sin JWT y JWT malformado, login inválido en auth, flujo completo login → QR → validación en gateway, identidad anónima idempotente, visitor sin PII, bloqueo de `identity:lookup` y JWT sin PII. |
| `health-form.spec.ts` | 2 | Carga del cuestionario o mensaje de «no active survey», y vuelta a la pantalla anterior. |
| `login.spec.ts` | 2 | Formulario de inicio de sesión y validación de campos obligatorios. |
| `promotion-flow.spec.ts` | 3 | Respuesta de `promotion-service`, promoción a `SUSPECT` con síntomas y control negativo sin síntomas. |
| `notification-flow.spec.ts` | 3 | Actuator dentro del pod, suscripción de `notification-group` a `promotion.status.changed` y auditoría `SUCCESS` en `notification.audit`. |

Los flujos de `promotion-flow` y `notification-flow` publican los mismos eventos
`survey.submitted` que form-service mediante `kubectl exec ... kafka-console-producer.sh`,
porque Kafka no tiene ruta por el Ingress. `notification-flow` siembra su propio
visitante; Jenkins siembra los dos visitantes de `promotion-flow` antes de la
etapa. Las esperas usan `expect.poll()`; el único `setTimeout(5_000)` restante es
la ventana de observación del caso negativo de promoción.

### Configuración y artefactos

- `playwright.config.ts`: `retries=2` en CI, `workers=1` en CI, reporteros
  `list` + HTML (`playwright-report/`) + JUnit (`test-results/junit.xml`).
- `trace`, `screenshot` y `video` se conservan solo en fallo y se guardan en
  `test-results/artifacts/`. **Jenkins no publica esas capturas**: en fallo solo
  archiva `tests/e2e/test-results/**/*.xml`; en éxito archiva el informe HTML y
  el XML excluyendo `*.png`, `*.webm`, `*.zip` y `*.trace`.
- `global-setup.ts` / `global-teardown.ts` son hooks de fail-fast y cierre de
  corrida; no emiten credenciales, tokens ni datos de prueba.

### Variables de entorno

| Variable | Uso | Origen |
|---|---|---|
| `WEB_URL`, `GATEWAY_URL`, `AUTH_URL`, `IDENTITY_URL`, `PROMOTION_URL` | Bases HTTP de las specs | Jenkins las fija al Ingress `:30080`. |
| `E2E_HOST_RESOLVER` | Regla `--host-resolver-rules` del navegador | Jenkins: `MAP dev.circleguard.local 127.0.0.1`. |
| `E2E_HEALTHCHECK_URL` | URL del healthcheck en `globalSetup` | Opcional; por defecto `http://127.0.0.1:30080`. |
| `E2E_ANONYMOUS_ID` | Prefijo de los visitantes sembrados por corrida | `e2e-${BUILD_NUMBER}-${GIT_COMMIT:0:7}` en Jenkins; obligatorio en `promotion-flow` y `notification-flow`. |
| `KUBE_NS`, `KAFKA_DEPLOYMENT`, `NOTIFICATION_DEPLOYMENT` | Selectores de `kubectl exec` | Opcionales; defaults `cg-dev`, `kafka`, `notification`. |
| `E2E_STAFF_USER`, `E2E_STAFF_PASSWORD` | Login de personal en `access-validation` | Inyectar desde el secret store de Jenkins. Los defaults del spec son datos ficticios del seed local, no secretos reales. |
| `E2E_HEALTH_USER`, `E2E_HEALTH_PASSWORD` | Login de usuario de salud y verificación de anonimización | Igual que la fila anterior. |
| `E2E_WRONG_PASSWORD` | Control negativo de login | Opcional; por defecto `wrong-password`. |

Si Jenkins no inyecta las credenciales `E2E_*`, la suite usa los valores
ficticios del spec y puede fallar contra un seed distinto. No se almacenan
contraseñas reales en el repositorio.

## Integración con Jenkins

- La etapa `E2E` se ejecuta en `main`, con `javaServices` seleccionados y sin
  `ROLLBACK_TAG`. Parameters: `SKIP_E2E=false` y `SKIP_ZAP=false` por defecto, de
  modo que un trigger externo que solo envía
  `SERVICE=auto&TAG=&SKIP_INTEGRATION=false&DEPLOY=false&ROLLBACK_TAG=` sigue
  ejecutando ambas compuertas. `SERVICE` admite `mobile` y `circleguard-mobile`.
- La etapa tiene `timeout(time: 12, unit: 'MINUTES')`; `ci.e2e()` siembra Neo4j,
  instala Chromium y ejecuta Playwright con las variables anteriores.
- Antes de cada corrida se limpian resultados previos para no archivar XML de
  builds anteriores. La etapa `Integration Tests` falla si no se ejecutó ningún
  `integrationTest` declarado, y `ci.integrationTest` rechaza XML vacío
  (`allowEmptyResults: false`).
- auth, identity y dashboard tienen `integration: false` en
  `jenkins/vars/ciServices.groovy`: usan MockMvc/H2 o `@DataJpaTest`, sin
  contenedor. El catálogo compartido sigue fijado a `@Library('circleguard@main')`;
  el `Jenkinsfile` aplica overrides locales hasta que el cambio se fusione.

## Inventario y trazabilidad histórica

| Ámbito | Magnitud leída de los artefactos | Limitación |
|---|---|---|
| JUnit Java | 66 XML / 293 casos | Solo cubre las clases que aparecen en algún XML; no es el inventario completo. |
| Clases Java | 87 clases de test / 352 métodos `@Test` + 5 casos adicionales | Cifra histórica; no implica que todas tengan XML. |
| Playwright | 5 specs / 19 casos | La suite actual está descrita arriba. |
| Jest mobile | 20 suites / 92 tests (`92/92` histórico) | `Jest 92/92` es mobile, **no** el objetivo E2E de 13 pruebas; la suite E2E actual tiene 19 casos. |
| JaCoCo Form / Promotion | 84,77 % / 76,94 % de líneas | Leído de `jacoco-*-test-report.xml` archivado. |

### Clases con `@Test` sin XML archivado (19)

| Servicio | Clases |
|---|---|
| dashboard | `PromotionClientConfigurationTest`, `PrometheusEndpointTest`, `PromotionClientMetricsTest` |
| file | `MinioStorageIntegrationTest`, `S3FileStorageServiceTest` |
| form | `FormOutboxRecoveryIntegrationTest`, `FormSecurityTest`, `FormOutboxDispatcherTest`, `FormOutboxSchedulerTest` |
| notification | `NotificationKafkaIntegrationTest`, `AuditLogServiceTest`, `CircleFencedListenerTest`, `EmailServiceImplTest`, `ExposureNotificationListenerUnitTest`, `PushServiceImplTest`, `SmsServiceImplTest`, `TemplateServiceFallbackTest`, `TemplateServiceUnitTest` |
| promotion | `PromotionPerformanceBenchmark` (antes `PromotionPerformanceTest`) |

Su resultado **no está documentado** por esos XML y no se afirma que pasaron.
`@Tag("integration")` tampoco garantiza integración real: las clases con
`@MockitoBean` sustituyen dependencias completas. Solo se considera integración
de contenedor la que levanta Testcontainers y declara esa etiqueta.

### Cobertura mobile

`mobile/package.json` incluye `app/**/*.{ts,tsx}` en `collectCoverageFrom`, por
lo que las pantallas de Expo Router aparecen en el informe. La compuerta es por
grupos: `hooks/`, `utils/`, `constants/` y `components/` mantienen el umbral del
**60 %**; `app/` tiene una línea base explícita del **0 %** porque aún no tiene
tests unitarios dedicados. El total agregado se imprime como información, no
como compuerta. `mobile/scripts/check-coverage.mjs` recalcula los mínimos desde
`coverage-summary.json`. `jest.restoreMocks = true` evita que un caso herede
spies configurados por otro.

## Matriz de evidencia

| Afirmación | Artefacto | Alcance / cautela |
|---|---|---|
| 66 XML y 293 casos archivados | `docs/informe/evidencia/validacion-final/tests/junit-*.xml` | Histórico; no es la corrida actual. |
| Cobertura Java oficial | `build/reports/jacoco/test/jacocoTestReport.xml` por servicio | Solo `test` unitario; `integrationTest` se publica en stage separado. |
| 20 suites / 92 tests mobile | `docs/informe/evidencia/validacion-final/tests/README.md`, salida de `npm run test:coverage` | Compuerta por grupos; `app/**` se reporta con línea base 0 %. |
| E2E ejecutada | `tests/e2e/test-results/junit.xml`, `tests/e2e/playwright-report/` | En fallo solo se archiva el XML; las capturas quedan en el workspace. |
| Integración con contenedor | `*/build/test-results/integrationTest/*.xml`, `*/build/integration-test/*.log` | `allowEmptyResults: false` y guarda global anti-skip en `build.gradle.kts`. |
| Pruebas de carga | `tests/performance-locust/LOCUST.md` | Diseño y comandos; requiere ejecución real contra `cg-stage`. |
| Seguridad pasiva | `tests/security-zap/SECURITY-ZAP.md`, Jenkins `OWASP ZAP Security Gate` | El gate de Jenkins escanea `stage` antes de promover a producción. |

## Herramientas de apoyo (no aserciones)

- `tests/performance-locust/locustfile.py` — **[HERRAMIENTA DE APOYO]**
  escenarios de carga E2.6; lo ejecuta Locust, no JUnit ni Playwright.
- `tests/security-zap/zap-baseline.sh` — **[HERRAMIENTA DE APOYO]** escaneo
  pasivo reproducible; el gate bloqueante de Jenkins es independiente.
- `tests/e2e/capture-evidence.sh` y `tests/e2e/capture-professor.mjs` —
  **[HERRAMIENTA DE APOYO]** capturas de evidencia legible para sustentación;
  no forman parte de la aserción de la suite.

Ninguno de estos scripts sustituye a una prueba automatizada ni debe citarse
como resultado de una corrida si no se ejecutó.

## Contrato Kafka E.0 (no modificar)

Los tests de integración de Kafka conservan la configuración acordada:

| Test | Imagen |
|---|---|
| `FormPromotionKafkaIntegrationTest` (`form`) | `confluentinc/cp-kafka:7.6.0` con `ConfluentKafkaContainer` |
| `FormOutboxRecoveryIntegrationTest` (`form`) | `confluentinc/cp-kafka:7.6.0` con `ConfluentKafkaContainer` |
| `NotificationKafkaIntegrationTest` (`notification`) | `confluentinc/cp-kafka:7.6.0` con `ConfluentKafkaContainer` |
| `NotificationKafkaTraceIntegrationTest` (`notification`) | `apache/kafka:3.7.0` con `KafkaContainer` |
| `KafkaPromotionIntegrationTest` (`promotion`) | `apache/kafka:3.7.0` con `KafkaContainer` |

Los tres tests Confluent mantienen exactamente:

```java
.withCommand("sh", "-c",
    "while [ ! -f /tmp/testcontainers_start.sh ]; do sleep 0.1; done; sleep 2; /tmp/testcontainers_start.sh")
```

Ese comando evita el error `Text file busy` (exit 126) durante el arranque del
contenedor. No se sustituye por esperas en Java ni se cambia la imagen: cualquier
cambio aquí invalida la evidencia E.0.

## Limitaciones conocidas

- `globalSetup` falla si el Ingress `:30080` no está levantado: es un fail-fast
  intencional, no un error de la spec.
- Los tests con contenedores requieren Docker en el host de Jenkins; no se
  ejecutaron en la verificación local de este cambio.
- Playwright requiere Chromium y `kubectl` con acceso a `cg-dev`; la corrida
  local de este cambio validó configuración y archivos, no el clúster.

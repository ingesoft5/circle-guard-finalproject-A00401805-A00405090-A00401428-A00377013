<!-- CG-DOC: Inventario histórico de resultados de pruebas archivados; las cifras se leen de los artefactos, no se reconstruyen. -->
# Pruebas — inventario [HISTÓRICO]

> **[HISTÓRICO]** Los archivos de esta carpeta son una instantánea de ejecuciones
> anteriores conservada como evidencia cruda. No son el resultado de la corrida
> actual del pipeline y no se ha verificado su reproducibilidad en este entorno.

## Alcance de los artefactos

- `junit-<servicio>-<tipo>-TEST-*.xml`: 66 reportes JUnit archivados (66 suites,
  293 casos registrados en los XML). Cubren exclusivamente las clases que
  aparecen en algún XML; **no** constituyen el inventario completo de pruebas.
- Cobertura mobile: `mobile-coverage-summary.json` y `mobile-lcov.info` cuando
  están presentes.
- No se edita ninguna cifra ni se regenera ningún XML: lo aquí documentado es
  lectura de los artefactos versionados.

Las cifras de referencia del cierre, leídas de los reportes y del histórico de
Jenkins, son:

| Ámbito | Magnitud |
|---|---|
| Jest (mobile) | 20 suites / 92 tests |
| Java | 87 clases de test / 352 métodos `@Test` + 5 casos adicionales |
| Playwright (E2E) | 5 specs / 19 casos |
| Form (JaCoCo, reporte `jacoco-form-test-report.xml`) | 84,77 % de líneas |
| Promotion (JaCoCo, reporte `jacoco-promotion-test-report.xml`) | 76,94 % de líneas (564/733) |

El hecho histórico «Jest 92/92» no debe confundirse con el objetivo pendiente
de 13 pruebas E2E descrito en `tests/TESTS.md`.

## Inventario de XML por servicio

| Servicio | XML archivados |
|---|---:|
| auth | 11 |
| dashboard | 4 |
| file | 2 |
| form | 11 |
| gateway | 4 |
| identity | 7 |
| notification | 11 |
| promotion | 16 |
| **Total** | **66** |

## Pruebas sin XML archivado (19 clases)

Las siguientes clases contienen `@Test` pero **no tienen reporte XML en esta
carpeta**. Se inventarian para evitar un falso positivo: su resultado no está
documentado por estos artefactos y **no se afirma que pasaron**.

| Servicio | Clase |
|---|---|
| dashboard | `PromotionClientConfigurationTest`, `PrometheusEndpointTest`, `PromotionClientMetricsTest` |
| file | `MinioStorageIntegrationTest`, `S3FileStorageServiceTest` |
| form | `FormOutboxRecoveryIntegrationTest`, `FormSecurityTest`, `FormOutboxDispatcherTest`, `FormOutboxSchedulerTest` |
| notification | `NotificationKafkaIntegrationTest`, `AuditLogServiceTest`, `CircleFencedListenerTest`, `EmailServiceImplTest`, `ExposureNotificationListenerUnitTest`, `PushServiceImplTest`, `SmsServiceImplTest`, `TemplateServiceFallbackTest`, `TemplateServiceUnitTest` |
| promotion | `PromotionPerformanceBenchmark` (antes `PromotionPerformanceTest`) |

`MinioStorageIntegrationTest` y `PromotionPerformanceBenchmark` son además sensibles
a Docker/tiempo de ejecución; su inclusión en este inventario no implica que se
hayan ejecutado.

## Calificación de las pruebas «de integración»

La etiqueta `@Tag("integration")` no garantiza integración real. Las clases que
declaran `@MockitoBean` sustituyen dependencias completas por mocks; por tanto
**no son integración de extremo a extremo**, aunque arranques un contexto Spring:

- `ExposureNotificationListenerTest` y `NotificationRetryTest` (notification):
  contexto Spring con `@MockitoBean`; sin contenedor. Se tratan como unitarias.
- `AuthIntegrationTest` (auth): MockMvc + H2 con `LdapAuthenticationProvider` e
  `IdentityClient` simulados; sin contenedor.
- `FormPromotionKafkaIntegrationTest` (form), `NotificationKafkaIntegrationTest`
  (notification), `AdministrativeCorrectionTest` y
  `HealthStatusReevaluationTest` (promotion): sí arrancan contenedores reales,
  perovarios collaboradores siguen siendo `@MockitoBean`; la cobertura es
  parcial, no un contrato completo entre servicios.

Solo se considera integración real (gate `integrationTest`) la que levanta al
menos un contenedor Testcontainers y declara `@Tag("integration")`.

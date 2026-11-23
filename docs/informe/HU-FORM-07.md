# HU-FORM-07 — Resiliencia del flujo de cuestionarios

- Historia: DCG-367.
- Base del análisis: `origin/main` en `1a9199d` (22 de noviembre de 2026).
- Alcance de esta entrega: subtareas 1 a 4, diagnóstico e implementación local.
- Estado: mecanismo implementado localmente; pruebas de caída y recuperación, revisión del equipo y evidencia desplegada pendientes.

## 1. Línea base: qué ocurría si fallaba Kafka

`HealthSurveyService.submitSurvey` abre una transacción PostgreSQL, calcula `hasSymptoms`, llama a `repository.save` y luego a `FormEventPublisher.publish` para enviar `survey.submitted`. La aprobación de un certificado sigue el mismo orden: guarda el estado y envía `certificate.validated`. `repository.save` no equivale por sí solo a un commit; este ocurre al finalizar correctamente la transacción. El evento de encuesta contiene `anonymousId`, `hasSymptoms` y `timestamp`; el de aprobación contiene `anonymousId`, `status`, `adminId` y `timestamp`. Ninguno contiene un identificador estable del evento ni el ID de la encuesta.

Antes de esta implementación, `FormEventPublisher` llamaba a `KafkaTemplate.send`, que devuelve un `CompletableFuture`. No esperaba su resultado. Registraba `success` o `failure` en el callback y conservaba un `trace_id` para el log. Un fallo lanzado directamente por `send` se registraba y se volvía a lanzar. Un fallo que llegaba después en el futuro se registraba, pero no se comunicaba a `HealthSurveyService` ni cambiaba la respuesta HTTP. No había reintento de aplicación, registro persistente de publicaciones pendientes ni coordinación transaccional entre PostgreSQL y Kafka.

| Momento | Efecto deducible del código | Evidencia |
|---|---|---|
| `send` lanza una excepción de ejecución durante el método transaccional | La excepción se propaga; Spring marca la transacción para rollback y la solicitud falla, siempre que no haya un manejo externo que cambie ese resultado. El `save` aún no garantiza persistencia confirmada. | `HealthSurveyService`, `FormEventPublisher`; `FormEventPublisherTest.recordsAndRethrowsSynchronousFailure` comprueba la propagación y el contador, no el rollback real. |
| `send` devuelve un futuro y luego completa con error | El callback incrementa `failure` y escribe un log acotado. El método de negocio puede terminar y confirmar PostgreSQL sin que Kafka haya aceptado el evento. La respuesta HTTP puede ser 200. | `FormEventPublisher`; `FormEventPublisherTest.countsAsyncFailureWithoutLeakingPayloadOrException` verifica el callback, no la transacción. |
| Kafka confirma el envío antes del commit de PostgreSQL | Promotion puede recibir el evento cuando la encuesta todavía no es visible; si después falla el commit, queda un evento sobre un cambio no confirmado. | Orden `save` → `send` dentro de `@Transactional`; riesgo de intercalado, aún sin prueba que fuerce esa secuencia. |
| PostgreSQL falla antes de llegar a `send` | No se intenta publicar desde este método y la solicitud falla. | Orden de llamadas de `HealthSurveyService`; requiere prueba de fallo para acreditar el comportamiento completo. |

La prueba `FormPromotionKafkaIntegrationTest` verifica publicación y propagación `traceparent` con un broker temporal. No simula una caída del broker entre `save`, confirmación Kafka y commit de PostgreSQL. Por ello, este informe describe el comportamiento del código y los riesgos; no afirma que se haya reproducido una pérdida en un entorno desplegado.

## Comparación con otros servicios

| Servicio | Patrón actual | Límite relevante para Form |
|---|---|---|
| Auth → Identity | `IdentityClient` usa `@Retry` y `@CircuitBreaker` para una llamada HTTP. El fallback normalmente vuelve a lanzar el error; la alternativa de ID local está desactivada por defecto. | La llamada HTTP es síncrona: el decorador puede observar el resultado de cada intento. |
| Identity → Kafka | `IdentityAuditPublisher` usa `@Retry` y `@CircuitBreaker`, configurados en `application.yml` con tres intentos y 500 ms entre ellos por defecto. El fallback incrementa métricas y registra el resultado. | Llama a `KafkaTemplate.send` sin esperar el futuro. Un fallo asíncrono no llega al Retry/Circuit Breaker; copiar ese patrón literalmente no resuelve la ventana de pérdida de Form. El fallback tampoco persiste un evento pendiente. |
| Promotion → Kafka | `HealthStatusService` publica `promotion.status.changed` mediante `KafkaTemplate.send` después de actualizar estado. | También hay una frontera entre estado de negocio y publicación; no aporta una garantía transaccional reutilizable para Form. |

La uniformidad útil para HU7 es conservar configuración externa, métricas y logs operativos acotados. La decisión del mecanismo y sus garantías corresponde a la subtarea 3 y requiere coordinación con los consumidores.

## 2. Riesgos de pérdida, duplicación e inconsistencia

| Riesgo | Escenario | Consecuencia posible | Estado de la evidencia |
|---|---|---|---|
| Pérdida de evento tras guardar | Kafka falla de forma asíncrona y PostgreSQL confirma la encuesta o la aprobación. | Promotion no actualiza el estado esperado; solo quedan métrica y log, sin una cola persistente de recuperación. | Camino de código y prueba unitaria del callback; no hay prueba de fallo de broker y recuperación. |
| Evento sin cambio confirmado | Kafka confirma antes del commit de PostgreSQL y luego la transacción se revierte. | Promotion procesa un hecho que no quedó confirmado en Form. | Riesgo de intercalado deducido; no reproducido. |
| Duplicación por reenvío | El cliente repite una solicitud tras una respuesta incierta, o una futura política de reintentos repite un envío cuyo acuse se perdió. | Dos eventos pueden representar una misma acción; en `submitSurvey`, una segunda solicitud también puede crear otra encuesta. | No se encontró identificador estable de evento ni deduplicación en Form; no se ha medido frecuencia. |
| Inconsistencia en el consumidor | Promotion recibe un evento duplicado o su procesamiento lanza una excepción. `SurveyListener` captura la excepción y retorna normalmente. | Puede perderse la señal de fallo para el contenedor Kafka; repetir una actualización de estado podría activar efectos secundarios más de una vez. | Código de `SurveyListener`; el resultado exacto de offsets y efectos secundarios necesita prueba dirigida. |
| Éxito aparente para el cliente | El fallo asíncrono ocurre después de que Form retorna la entidad o la respuesta HTTP. | El cliente cree que terminó el flujo, aunque Promotion no recibió el evento. | Código y prueba de callback; falta prueba HTTP con fallo real. |

## 3. Patrón seleccionado y justificación

Se seleccionó **outbox transaccional en PostgreSQL más Retry de Resilience4j al despachar**. El outbox resuelve la ventana principal: la encuesta o aprobación y su evento pendiente se guardan en la misma transacción. Un trabajador publica después del commit; por ello no puede emitir un evento de una transacción revertida. Si Kafka falla, la fila permanece para recuperación posterior. Resilience4j limita a tres los intentos por ciclo, con 500 ms de espera y un límite de 3 s por acuse. Los valores son configurables.

Se consideró Circuit Breaker, que podría reducir llamadas durante una caída prolongada, pero no conserva por sí solo eventos pendientes. Bulkhead limita concurrencia, pero el trabajador ya tiene una sola ejecución programada por instancia y el bloqueo de filas evita que dos réplicas tomen el mismo evento simultáneamente. No se copió el publicador de Identity porque su Retry no observa los fallos del futuro Kafka. La elección técnica necesita revisión y acuerdo del equipo; este informe no presenta esa coordinación como completada.

## 4. Implementación local

- Flyway V6 crea `form_event_outbox` con ID único, tipo, clave, payload JSON, `traceparent`, intentos y próxima fecha de intento. `FormEventPublisher` inserta la fila desde las transacciones existentes de `HealthSurveyService`; no invoca Kafka en la solicitud HTTP.
- Un despachador selecciona una fila vencida con `FOR UPDATE SKIP LOCKED`, espera el acuse de Kafka con tiempo máximo y borra la fila tras éxito. Varias réplicas de Form no seleccionan la misma fila simultáneamente. Un fallo incrementa el contador `failure` de HU6, registra solo datos operativos y posterga la fila con espera exponencial limitada a 60 s. Cada ciclo hace como máximo tres intentos; la fila no se descarta automáticamente.
- El mensaje conserva topic, clave y contenido JSON. Añade un header `form-event-id` estable para reconocer reenvíos; conserva el contexto W3C capturado al crear la fila y lo restaura para la observación Kafka. El contador `success` se incrementa tras acuse. El planificador revisa filas cada segundo y procesa hasta diez por ejecución.
- La entrega es **al menos una vez**. Si Kafka aceptó un mensaje pero el acuse se perdió, un nuevo intento puede duplicarlo. Promotion aún no deduplica `form-event-id`; la coordinación de esa garantía con su responsable sigue pendiente. El outbox contiene los campos del evento en PostgreSQL y no debe exponerse en logs ni capturas de Jira.

`check` de Form pasó, incluidas las pruebas unitarias y el umbral de cobertura. Tras iniciar Docker Engine, `integrationTest` pasó con dos casos, sin fallos ni omisiones: adjuntos con PostgreSQL temporal y Form → Kafka → Promotion con broker temporal y `traceparent`. Las subtareas 5 y 6 aún requieren pruebas dirigidas de indisponibilidad y recuperación, documentación final de resultados y validación operativa. No se ha desplegado el cambio ni se ha modificado el clúster.

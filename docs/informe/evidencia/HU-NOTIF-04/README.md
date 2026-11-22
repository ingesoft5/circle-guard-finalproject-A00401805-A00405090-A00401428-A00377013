<!-- CG-DOC: Evidence index for HU-NOTIF-04 (DCG-318): notification deployment and E2E. -->
# HU-NOTIF-04 - Desplegar `notification-service` en dev, stage y prod

Cubre `DCG-354`, `DCG-355`, `DCG-356` y `DCG-357` de `DCG-318`.

## What ships

| File | Purpose |
|---|---|
| `deploy/compose/{dev,stage,prod}/docker-compose.yml` | `notification` con healthcheck de proceso, Kafka del stack y credenciales de canal por `${...}` |
| `deploy/compose/{dev,stage,prod}/.env.example` | Claves SMTP, Twilio y Gotify que cada ambiente necesita; el `.env` real sigue ignorado por git |
| `Jenkinsfile` | El catálogo local de `enrichServices` marca `notification` como desplegable sin esperar a la Shared Library |
| `tests/e2e/notification-flow.spec.ts` | Cuestionario, Kafka, cascada de promoción y despacho multicanal verificados contra dev |

El despliegue en sí reutiliza lo que ya existe: `ciServices.groovy` ya declara
`docker: true, publish: true` para `notification`, `deploy/k8s/base/apps.yml`
ya tiene Deployment y Service con sondas en :8082, y el Dockerfile ya trae
healthcheck de actuator. Esta historia agrega el compose y la ejecución E2E.

## Flujo de despliegue del pipeline

Solo corre en `main`; un PR nunca despliega.

| Orden | Stage | Condición | Qué hace |
|---|---|---|---|
| 1 | `Deploy dev` | `services` no vacío | `ci.deploy('dev', env.TAG)`: pull, `k3d image import`, `kustomize edit set image`, `apply -k`, `rollout status` por servicio |
| 2 | `E2E` | `!SKIP_INTEGRATION` | Siembra visitantes en Neo4j y corre Playwright contra el Ingress de k3d (`:30080`) |
| 3 | `Deploy stage` | `services` no vacío | Igual que dev contra el overlay `stage` |
| 4 | `OWASP ZAP Security Gate` | `!SKIP_INTEGRATION` | Baseline contra `auth` en `cg-stage`, bloqueante con `FAIL-NEW` |
| 5 | `Approval prod` | `services` no vacío | `input message: 'Aprobar despliegue a producción'` — espera aprobación manual |
| 6 | `Deploy prod` | `services` no vacío | Igual que dev contra el overlay `prod` |

`ci.deploy` aplica el **mismo tag** a los nueve servicios del overlay, así que
"la misma imagen validada se promueve entre ambientes" se cumple por
construcción: `env.TAG` se resuelve una sola vez en `Resolve services` y viaja
sin cambios a dev, stage y prod.

La marca de "desplegable" vive en dos sitios y esta historia solo toca el local:

- `jenkins/vars/ciServices.groovy` (catálogo de la Shared Library, `deploy: true`)
  es el estado final, pero un PR no ve sus propios cambios de library.
- `Jenkinsfile` → `enrichServices().deployable` ya incluye `notification`, que es
  lo que hace que el PR y `main` desplieguen sin esperar a la library.

## Procedimiento de rollback (`DCG-357`)

El pipeline tiene una vía de rollback explícita, sin reconstruir nada:

```text
Jenkinsfile:427  string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'Si se indica, despliega ese tag en prod sin reconstruir.')
Jenkinsfile:446  if (params.ROLLBACK_TAG?.trim()) { env.TAG = normalizeSemver(...); services = [] }
Jenkinsfile:732  stage('Rollback prod') { when { params.ROLLBACK_TAG?.trim() } ... ci.deploy('prod', params.ROLLBACK_TAG) }
```

Con `ROLLBACK_TAG` no vacío, `services` queda vacío y todos los stages de
construcción, pruebas, empaquetado, publicación y Docker se saltan por su
`when`: el único stage que ejecuta es `Rollback prod`.

### Pasos exactos a ejecutar en el host

1. Elegir el tag de rollback. Debe ser un release anterior **completo**: el stage
   hace `docker pull` de los nueve servicios con ese tag, así que un tag que no
   exista en Nexus para alguno de ellos falla en el primer `pull`.

   ```bash
   # Tags ya publicados de notification (el repositorio docker de Nexus se
   # resuelve por nombre; ajustar si el host usa otro):
   curl -fsS "$NEXUS_URL/service/rest/v1/search?name=notification" \
     | jq -r '.items[] | [.repository, .version] | @tsv' | sort -k2 -Vr | head -10
   # Alternativa: el image-digest.txt archivado del build anterior en Jenkins.
   ```

2. Confirmar el tag que corre hoy en prod, para poder volver:

   ```bash
   kubectl -n cg-prod get deploy notification \
     -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
   ```

3. Lanzar el rollback desde Jenkins (job multibranch, rama `main`), sin tocar
   `SERVICE` ni `TAG`:

   ```text
   ROLLBACK_TAG=<tag-anterior>     # p.ej. 1.4.0
   SKIP_INTEGRATION=false
   DEPLOY=false
   ```

   Consola esperada: `Rollback solicitado: <tag>` en `Resolve services` y, al final
   del stage `Rollback prod`, la salida de `kubectl rollout status` del último
   servicio del bucle (`deployment "web" successfully rolled out`). El library no
   imprime una línea de "despliegue OK": la prueba es el `rollout status` de los
   nueve servicios.

4. Verificar en el clúster:

   ```bash
   kubectl -n cg-prod get deploy notification \
     -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'   # debe decir :<tag-anterior>
   kubectl -n cg-prod rollout status deploy/notification --timeout=180s
   kubectl -n cg-prod exec deploy/notification -- \
     wget -qO- http://127.0.0.1:8082/actuator/health                 # {"status":"UP"...}
   kubectl -n cg-prod logs deploy/notification --tail=50
   ```

5. Registrar la evidencia (`07-rollback.png` y `08-rollback-post.png` de la
   tabla de abajo) y, si el rollback fue por un incidente, dejar el tag
   afectado anotado en el ticket `DCG-357`.

Si el rollback no basta porque el problema es de datos y no de imagen, lo que se
revierte es el deployment, no la base: `kubectl -n cg-prod rollout undo
deploy/notification`. Ese comando no está envuelto por el pipeline y queda como
recurso manual del operador.

## Cómo verifica el E2E que la notificación llegó

`notification-service` no expone API HTTP (solo `/actuator/health`) ni tiene ruta
en el Ingress, así que `tests/e2e/notification-flow.spec.ts` usa `kubectl exec`
desde el propio runner, igual que `promotion-flow.spec.ts` publica sus eventos.
Tres pruebas:

1. `notification-service está saludable dentro del clúster` — `wget` al actuator
   dentro del pod; el healthcheck mide el proceso, no el SMTP
   (`management.health.mail.enabled=false`).
2. `el consumidor notification-group está suscrito a promotion.status.changed` —
   `kafka-consumer-groups.sh --describe --group notification-group`; prueba el
   cableado de Kafka sin publicar nada.
3. `el cuestionario llega a notification-service y el despacho deja un audit
   SUCCESS` — siembra su propio visitante (`cypher-shell`, porque el pipeline
   solo siembra los dos de promoción), publica `survey.submitted` con
   `hasSymptoms: true`, y hace polling sobre `notification.audit` hasta ver un
   registro `SUCCESS` con `userId` igual al `anonymousId` de la corrida.
   Un `SUCCESS` en `notification.audit` prueba la cadena completa:
   `survey.submitted` → promoción (`promotion.status.changed`) → consumo por
   `notification-group` → despacho por canal.

Ese `SUCCESS` sale de los canales MOCK: `SmsServiceImpl` y `PushServiceImpl`
registran el despacho como SUCCESS cuando `twilio.account-sid` empieza con
`AC_MOCK` o el token de Gotify es `MOCK_TOKEN`. EMAIL no puede dar SUCCESS en dev
porque no hay SMTP, y por eso la aserción pide *algún* canal con
`/^(EMAIL|SMS|PUSH)$/` en vez de exigir los tres.

**Brecha conocida:** el runner no tiene `@playwright/test` instalado en el árbol;
el stage `E2E` corre `npm install` antes de `npx playwright test`, así que la
validación local se limita a `docker compose config` y al chequeo de sintaxis del
spec. La corrida real la hace el pipeline.

## Evidencia pendiente de captura

Todo lo de abajo requiere una corrida del pipeline en `main` después del merge.

| Evidencia | Cómo obtenerla |
|---|---|
| `01-deploy-dev.png` | Consola del stage `Deploy dev` con los nueve `rollout status` en verde, `notification` incluido |
| `02-pod-dev.png` | `kubectl -n cg-dev get pods -l app=notification` con `1/1 Running` |
| `03-e2e-notification.png` | Consola del stage `E2E` con las tres pruebas de `notification-flow.spec.ts` en verde |
| `04-audit-topic.png` | `kubectl -n cg-dev exec deploy/kafka -- /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic notification.audit --from-beginning --timeout-ms 5000` con el registro `SUCCESS` |
| `05-deploy-stage.png` | Consola del stage `Deploy stage` |
| `06-approval-prod.png` | Stage `Approval prod` esperando el input manual |
| `07-rollback.png` | Corrida con `ROLLBACK_TAG=<anterior>`: `Rollback solicitado: <tag>` y los `rollout status` del stage `Rollback prod` |
| `08-rollback-post.png` | `kubectl -n cg-prod get deploy notification -o jsonpath=...` mostrando el tag anterior y el actuator en `UP` |

No se enlazan imágenes que aún no existen en el repositorio.

## Reproducir las verificaciones locales

```bash
# Validación de los tres stacks sin levantar nada (el .env real no se versiona).
for env in dev stage prod; do
  docker compose -f deploy/compose/$env/docker-compose.yml \
    --env-file deploy/compose/$env/.env.example config --quiet \
    && echo "$env OK"
done

# Sin --env-file el comando falla por los `${MINIO_ROOT_USER:?requerido}` que ya
# existían antes de esta historia; esos errores no vienen de notification.

# Stack de notificación solo, con Kafka y la cascada de promoción.
cd deploy/compose/dev
docker compose up -d --wait kafka promotion notification
docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic survey.submitted <<< \
  '{"anonymousId":"e2e-local-notification","hasSymptoms":true}'
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic notification.audit \
  --from-beginning --timeout-ms 5000    # {"channel":"SMS","status":"SUCCESS",...}
```

## Estado conocido de los canales

- El compose pasa las credenciales por `${...}`; el `.env` real nunca se
  versiona y `.env.example` solo lleva placeholders.
- Sin `.env` que las sobrescriba, los tres ambientes arrancan en modo MOCK
  (`AC_MOCK_SID` / `MOCK_TOKEN`): el servicio no contacta Twilio ni Gotify y el
  audit es SUCCESS. Es deliberado: un despliegue no debe enviar tráfico real por
  accidente, y las credenciales reales las inyecta el `.env` del host.
- `SPRING_KAFKA_PRODUCER_VALUE_SERIALIZER=JsonSerializer` se fija en el compose
  porque `AuditLogService` publica un `Map` en `notification.audit` con el
  `KafkaTemplate` autoconfigurado, cuyo `value-serializer` por defecto es
  `StringSerializer`. Sin esa clave el despacho termina en RETRY/FAILED y el
  audit nunca llega al topic.
- En Kubernetes el pod toma `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `AUTH_API_URL`,
  `MANAGEMENT_HEALTH_MAIL_ENABLED` y los secretos `JWT_SECRET`/`QR_SECRET` de
  `cg-config`/`cg-secrets`; las claves SMTP/Twilio/Gotify llegan con los Secrets
  de `HU-NOTIF-05` (`DCG-358`), que es donde la historia de orquestación las
  declara.

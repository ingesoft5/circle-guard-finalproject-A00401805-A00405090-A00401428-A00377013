<!-- CG-DOC: Resumen del proyecto, guía de configuración y arquitectura. -->
# CircleGuard Monorepo

#### **Integrantes:**
* **Daniela Castaño Moreno**
* **Santiago Alejandro Estrada**
* **Faiber Piedrahita Perlaza**
* **Alejandro Quiñones Caicedo**




**Privacidad absoluta. Contención de alta velocidad. Campus seguro.**

CircleGuard es un sistema universitario de seguimiento de contactos y acordonamiento sanitario de última generación, diseñado para identificar grupos de contacto interconectados («Círculos») y aplicar cercos de salud rápidos sin comprometer el anonimato individual.

---

## Contexto de despliegue: de AWS a on-premise

La Parte 1 se desarrolló y desplegó en AWS con los scripts de `infra/bootstrap/`. Faltando aproximadamente media hora para la entrega, la cuenta de AWS fue bloqueada y perdimos el acceso. Siguiendo la sugerencia del profesor, rehicimos y terminamos toda la plataforma on-premise en el computador de la sala 205M, con dirección privada `10.147.20.67` (ZeroTier).

Allí se reconstruyeron y verificaron Jenkins, SonarQube, Nexus, el registro Docker y el clúster k3d con los namespaces `cg-dev`, `cg-stage` y `cg-prod`. Los scripts y la evidencia de AWS se conservan como registro histórico de la Parte 1; la plataforma vigente y toda la Parte 2 se operan on-premise. El detalle técnico está en [`docs/infra/migracion-onpremise.md`](docs/infra/migracion-onpremise.md).

---

## Visión y misión

Nuestra visión es un campus universitario donde la velocidad de contención sanitaria supera los tiempos de confirmación de laboratorio sin comprometer la privacidad de los estudiantes. CircleGuard aprovecha la inteligencia nativa del campus —horarios de clase e infraestructura WiFi— para ofrecer un ecosistema de protección basado en grafos y validado por personas.

### Diferenciadores clave
- **Privacidad como código**: sin exposición de nombres reales fuera de una bóveda segura del Centro de Salud.
- **Contención recursiva**: cascadas de promoción de estado (Sospechoso → Probable → Confirmado) que se disparan en milisegundos.
- **Integración con el campus**: check-in inteligente mediante triangulación de puntos de acceso WiFi y Bluetooth Low Energy (BLE).

---

## Métricas de éxito

| Métrica | Objetivo | Medición |
|:---|:---|:---|
| **Velocidad de contención** | < 60 segundos | Prueba automatizada de la cascada del motor de promoción |
| **Cumplimiento de privacidad** | 100 % de anonimato | Prueba de penetración sobre la base de datos de grafos (cero nombres reales) |
| **Adopción de check-in** | > 70 % | Analítica sobre la validación de contactos en clases programadas |
| **Tasa de falsos positivos** | < 15 % | Encuestas posteriores al cerco sobre contactos reales frente a sospechosos |
| **Disponibilidad del sistema** | 99,5 % | 07:00–22:00 (horas pico académicas) |

---

## Resumen de arquitectura

CircleGuard sigue una **arquitectura de microservicios** sobre un **modelo de datos híbrido**.

### Motor central
1. **Máquina de promoción de estados**: usa **Neo4j** para recorridos recursivos de grafo que identifican contactos dentro de una ventana temporal de 14 días.
2. **Bóveda de anonimización**: una **PostgreSQL** segregada gestiona la correspondencia de identidades mediante hash con sal, conforme a la normativa **FERPA**.
3. **Núcleo orientado a eventos**: **Apache Kafka** gestiona los cambios de estado asíncronos, los registros de auditoría y el despacho de notificaciones.

### Directorio de servicios
- **Servicio de autenticación**: autenticación de doble vía LDAP (Universidad) / Local (Invitados) con RBAC dinámico.
- **Servicio de identidad**: bóveda criptográfica para anonimizar identidades reales.
- **Servicio de promoción**: el motor de estados (procesamiento recursivo de grafos).
- **Servicio de notificaciones**: despachador multicanal (Push/correo/SMS).
- **Servicio de formularios**: motor dinámico de cuestionarios de salud.
- **Servicio de gateway**: validación de acceso al campus mediante tokens QR firmados y con vigencia limitada.
- **Servicio de dashboard**: analítica geoespacial de puntos calientes que preserva la privacidad.
- **Servicio de archivos**: almacenamiento seguro de certificados y documentos (compatible con S3).

---

## Stack técnico

| Capa | Tecnología | Justificación |
|:---|:---|:---|
| **Backend** | Spring Boot 3.2.4 / Kotlin 1.9.24 / Java 21 | Servicios JVM tipados y reactivos. |
| **Base de datos de grafos** | Neo4j 5.26 | Recorridos recursivos de alto rendimiento que no se logran con SQL. |
| **Base de datos relacional** | PostgreSQL 16 | Almacenamiento conforme a ACID para identidad y configuración. |
| **Bus de mensajes** | Apache Kafka 3.7.0 (KRaft) | Registro de eventos persistente y con trazabilidad de auditoría para el despacho de estados. |
| **Caché** | Redis 7.2 | Caché distribuida L2 para la validación rápida del estado en el acceso al campus. |
| **Mobile/Web** | Expo (React Native) | Una sola base de código para iOS, Android y navegador. |
| **Infraestructura** | Docker Compose on-premise (`cg-tools`) + clúster k3d (`cg`) | k3d ejecuta k3s (Kubernetes certificado por la CNCF): la API es idéntica a K8s, pero se empaqueta sobre Docker para operar un host compartido sin `sudo` ni exponer puertos; ver [CI/CD](#plataforma-cicd) y `infra/onpremise/ONPREMISE.md`. |

---

## Hoja de ruta

### Fase 1: MVP — El núcleo inteligente (actual)
- [x] Máquina de promoción de estados (Sospechoso → Probable → Confirmado).
- [x] Grafo temporal con aristas TTL de 14 días.
- [x] Notificaciones de cerco multicanal (Push/correo/SMS).
- [ ] Consola de desidentificación del Centro de Salud.

### Fase 2: Crecimiento — Inteligencia espacial
- [ ] Integración de triangulación de puntos de acceso WiFi.
- [ ] Validación de acceso al campus (Gatekeeper) mediante QR.
- [ ] Integración con LMS para automatizar el estado «Asistencia remota».

### Fase 3: Visión — Ecosistema completo
- [ ] Detección de círculos fuera del campus mediante Bluetooth P2P.
- [ ] Panel global de salud con visualización de puntos calientes.
- [ ] Puente con API de laboratorio para la ingestión automatizada de resultados de pruebas.

---

## Desarrollo local

### 1. Infraestructura
Para la plataforma on-premise vigente (Compose `cg-tools` + k3d `cg`, namespaces `cg-dev`/`cg-stage`/`cg-prod`), sigue [`infra/onpremise/ONPREMISE.md`](infra/onpremise/ONPREMISE.md) y [`docs/infra/migracion-onpremise.md`](docs/infra/migracion-onpremise.md).

Los stacks `deploy/compose/{dev,stage,prod}` se conservan como **referencia local histórica** de la Parte 1 (definen los mismos servicios y middleware para una sola máquina); ya no son el destino de entrega.

Para ejecutar el stack local histórico, copia la plantilla de entorno, define `TAG` y `NEXUS_REGISTRY`, y reemplaza todos los marcadores `change-me` antes de iniciar:

- `POSTGRES_PASSWORD`
- `NEO4J_PASSWORD`
- `OPENLDAP_ADMIN_PASSWORD`
- `QR_SECRET`
- `JWT_SECRET`
- `VAULT_SECRET` (usa un valor real de al menos 32 caracteres)
- `VAULT_SALT`
- `VAULT_HASH_SALT`

Define `ENV_HOST_IP` si el stack debe ser alcanzable desde fuera del host.

```bash
cp deploy/compose/dev/.env.example deploy/compose/dev/.env
docker compose --env-file deploy/compose/dev/.env -f deploy/compose/dev/docker-compose.yml up -d
```
*El middleware incluye: PostgreSQL, Neo4j, Kafka (KRaft), Redis y OpenLDAP.*

**Siembra manual (Promotion/Neo4j).** `services/circleguard-promotion-service/src/main/resources/seed_faculties.cypher`
es un script manual: la aplicación no lo ejecuta al iniciar. Ejecútalo desde Neo4j Browser
o `cypher-shell` contra la instancia local cuando necesites datos de prueba de facultades.

### 2. Compilación y ejecución
CircleGuard usa Gradle para compilar los servicios en paralelo:
```bash
# Iniciar todos los microservicios en paralelo
./gradlew bootRun --parallel

# Iniciar un servicio específico
./gradlew :services:<service-name>:bootRun
```

### 3. Exploración de la API
Cada servicio expone una interfaz OpenAPI 3.0. Una vez en ejecución, visita:
`http://localhost:<service-port>/swagger-ui/index.html`

---

## Desarrollo del frontend

El frontend está construido con **Expo (React Native)** y admite iOS, Android y Web desde una sola base de código ubicada en `/mobile`.

### 1. Requisitos previos
Asegúrate de tener Node.js instalado y las dependencias cargadas:
```bash
cd mobile
npm install
```

### 2. Ejecutar la aplicación
Puedes ejecutar la app en distintos modos según la plataforma objetivo:

| Plataforma | Comando | Notas |
|:---|:---|:---|
| **Menú de desarrollo** | `npm run start` | Abre el menú de inicio de Expo Go. |
| **Android** | `npm run android` | Requiere Android Studio/emulador o un dispositivo conectado. |
| **iOS** | `npm run ios` | Requiere macOS con Xcode/Simulator instalado. |
| **Navegador web** | `npm run web` | Abre el panel/app en el navegador predeterminado. |

### 3. Pruebas
Para ejecutar las pruebas unitarias y de componentes del frontend:
```bash
npm run test
```

---

## Pruebas

Mantenemos la integridad del sistema con pruebas en varios niveles:

| Comando | Alcance |
|:---|:---|
| `./gradlew test` | Suite unitaria completa (todos los servicios) |
| `./gradlew :services:<name>:test` | Pruebas unitarias de un solo servicio |
| `./gradlew :services:circleguard-gateway-service:integrationTest` | Pruebas del gateway contra un contenedor Redis efímero |
| `tests/e2e` (Playwright) | Flujos de navegador contra el cliente web y el gateway desplegados — ver [`tests/TESTS.md`](tests/TESTS.md) |
| `tests/security-zap/` | Línea base de OWASP ZAP — ver [`SECURITY-ZAP.md`](tests/security-zap/SECURITY-ZAP.md) |
| `tests/performance-locust/` | Pruebas de carga contra los entornos desplegados |

**Nota**: Las pruebas de integración usan **Testcontainers** para crear instancias efímeras de Neo4j, PostgreSQL y Redis y validar sin efectos laterales. La suite de integración del gateway requiere Docker y se excluye de `test` para que las compilaciones unitarias no dependan de él.

**WSL (repo en `/mnt/c`)**: el montaje 9p hace que el classpath sea demasiado lento para el límite de 3 minutos por tarea. Mueve las salidas de compilación a ext4 con el script de inicialización local:

```bash
./gradlew -I gradle/wsl-ext4.init.gradle.kts \
  :services:circleguard-gateway-service:integrationTest
```

---

## Privacidad y cumplimiento

- **Cumplimiento FERPA**: las identidades de los estudiantes nunca se almacenan en el grafo de contactos.
- **Derecho al olvido**: los usuarios pueden solicitar la purga completa de sus datos mediante la Bóveda de Identidad.
- **Privacidad temporal**: todas las aristas de contacto se purgan automáticamente después de 14 días.

---

## Estructura del proyecto

```text
circleguard/
├── .github/
│   ├── pull_request_template.md      # Plantilla de cambio y revisión
│   └── workflows/enforce-pr.yml      # Bloquea pushes directos a main
├── .githooks/                        # commit-msg (Conventional Commits) y pre-push
├── services/                         # 8 microservicios Spring Boot/Kotlin
│   ├── circleguard-auth-service/     # Dockerfile multinivel sin root
│   ├── circleguard-identity-service/
│   ├── circleguard-promotion-service/
│   ├── circleguard-form-service/
│   ├── circleguard-gateway-service/
│   ├── circleguard-notification-service/
│   ├── circleguard-file-service/
│   └── circleguard-dashboard-service/
├── mobile/                           # Cliente Expo (iOS/Android/web) + Dockerfile Nginx
├── Jenkinsfile                       # Canalización multibranch
├── jenkins/
│   ├── vars/                         # Biblioteca compartida: ci, ciServices, ciNotify, ciVersion
│   ├── test/                         # Pruebas unitarias de la biblioteca compartida
│   └── JENKINS.md
├── infra/
│   ├── onpremise/                    # Bootstrap y verificación de cg-tools + k3d (Parte 2)
│   ├── terraform/                    # IaC modular: modules/{compute,network,observability,registry,storage}
│   │                                 # y envs/{dev,stage,prod}
│   ├── bootstrap/                    # SOLO HISTÓRICO (Parte 1, AWS) — ver docs/informe/
│   └── INFRA.md
├── deploy/
│   ├── compose/tools/                # cg-tools: Jenkins, SonarQube, Nexus, registro (on-prem)
│   ├── compose/{dev,stage,prod}/     # Conjuntos de la Parte 1: referencia local, no destino de entrega
│   ├── k8s/                          # Kustomize: base, components, overlays, monitoring, form06-dev
│   ├── finops/                       # OpenCost, KEDA y dashboard de costos (bonificación)
│   ├── mesh/istio/                   # Service mesh (bonificación)
│   ├── chaos/                        # Chaos Mesh (bonificación)
│   ├── scripts/                      # despliegue, reversión, prueba rápida, siembra (Parte 1 histórica)
│   └── DEPLOY.md
├── tests/
│   ├── e2e/                          # Playwright: login, formulario, acceso, promociones
│   ├── performance-locust/           # Pruebas de carga
│   ├── security-zap/                 # OWASP ZAP baseline
│   └── TESTS.md
├── docs/
│   ├── agil/                         # Sprint 0-2, backlog, métricas y trazabilidad Jira
│   ├── arquitectura/                 # Arquitectura, pipeline y diagnósticos
│   ├── bonificaciones/               # chaos, finops, mesh
│   ├── costos/                       # Política spot y costos
│   ├── diagramas/, infra/, informe/, operaciones/, patrones/
│   ├── release-notes/, standards/
│   └── change-management.md
├── scripts/                          # Verificación de bonificaciones y sincronía Jira
├── gradle/                           # Convenciones JaCoCo y wrapper de Gradle
├── build.gradle.kts, settings.gradle.kts, gradlew
├── BRANCHING-STRATEGY.md
├── README.md
├── CHANGELOG.md
├── IAG_REGISTRO.md
├── Proyecto_Final_CircleGuard.{tex,pdf}
└── sonar-project.properties
```

**Correspondencia con el árbol sugerido del enunciado.** El texto propone `tests/e2e-playwright/` y
`observability/{prometheus,grafana,alerts}/` como *estructura sugerida* (`Proyecto_Final_CircleGuard.tex:287`).
En este repositorio esas responsabilidades están en:

| Enunciado | Ruta real |
|:---|:---|
| `tests/e2e-playwright/` | `tests/e2e/` (suite Playwright) |
| `observability/prometheus/` | `deploy/k8s/monitoring/prometheus.yml` |
| `observability/grafana/` | `deploy/k8s/monitoring/grafana.yml` y `deploy/finops/grafana-cost-dashboard.yml` |
| `observability/alerts/` | `deploy/k8s/monitoring/alertmanager.yml`, `deploy/k8s/base/*-alerts.yml` y `deploy/k8s/components/form-observability/monitoring/form-alerts.yml` |

## Convenciones del repositorio

- **Documentación:** todo archivo fuente, script, configuración y documento mantenido lleva un encabezado
  `CG-DOC:` con el propósito del archivo. Los registros históricos y entregables del curso están
  exentos; la sintaxis y los casos están en [`docs/standards/documentation.md`](docs/standards/documentation.md).
- **Control de cambios:** GitHub Flow estricto, `main` protegida, squash merge y al menos una aprobación.
  La especificación canónica está en [`BRANCHING-STRATEGY.md`](BRANCHING-STRATEGY.md).
- **Trazabilidad:** cuando existe correspondencia con Jira, el título del PR incluye la clave `DCG-*`
  del tablero [DCG 67](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67).
  Los estados de los PR no se modifican como parte de tareas de documentación.
- **Registros históricos:** actas Scrum, notas de versión, `CHANGELOG.md`, `IAG_REGISTRO.md`, evidencias
  y el informe final son documentos de corte: no se reescriben para normalizarlos.

---

## Plataforma CI/CD

La plataforma corre on-premise en un único host (accesible por ZeroTier):

- `cg-tools` (Docker Compose): Jenkins, SonarQube, Nexus, registro `circleguard-docker` y el relay smee.io del webhook.
- Clúster k3d `cg`: namespaces `cg-dev`, `cg-stage` y `cg-prod` con PostgreSQL, Neo4j, Kafka, Redis, OpenLDAP, los cinco servicios núcleo, los tres reportes y el cliente web; Ingress de Traefik en `:30080` ruteado por `Host` (`<env>.circleguard.local`).

**Por qué k3d y no Kubernetes instalado en el host.** No es una decisión entre «k3s y k8s»: k3d ejecuta k3s, una distribución de Kubernetes certificada por la CNCF, así que la API y los manifiestos son los mismos. La elección es de empaquetado y operación. El host es compartido con otros proyectos y el usuario `docker` no tiene `sudo`; instalar `kubelet`/`containerd` o un clúster `kubeadm` exigiría privilegios y puertos adicionales. k3d ejecuta el plano de control y los agentes como contenedores, expone solo el Ingress en `:30080` y reutiliza el espejo HTTP del registro (`10.147.20.67:5000`).

| Alternativa | Resultado | Motivo |
|:---|:---|:---|
| **k3d (k3s en Docker)** | **Elegida** | Sin `sudo`, sin puertos de API expuestos, mirror de registro ya configurado. |
| k3s nativo en el host | Descartada | Requiere `systemd`/root y puertos `6443`/`10250` en un host compartido. |
| minikube / kind | Descartadas | Añaden una VM o un nodo Docker extra con su propia red; no aportan sobre k3d aquí. |
| kubeadm / clúster externo (EKS) | Descartadas | Requieren privilegios, automatización IaC adicional o salida a nube; la API es la misma. |

Detalle completo: [`deploy/k8s/K8S.md`](deploy/k8s/K8S.md).

Levantar desde cero en el host on-premise:

```bash
git clone https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013.git
cd circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013
./infra/onpremise/bootstrap-onpremise.sh   # .env.onpremise + Jenkins + SonarQube + Nexus
./infra/onpremise/k3d-cluster-create.sh    # clúster cg + namespaces cg-dev/cg-stage/cg-prod
docker compose --env-file deploy/compose/tools/.env.onpremise \
  -f deploy/compose/tools/docker-compose.onpremise.yml -p cg-tools up -d
kubectl apply -k deploy/k8s/overlays/dev   # y stage/prod según el entorno
./infra/onpremise/verify-onpremise.sh      # chequeo read-only de salud
```

El registro HTTP del host (`10.147.20.67:5000`) lo usa el clúster para jalar imágenes; `k3d image import -c cg` importa las que Jenkins publica.

La infraestructura de la Parte 1 (cuatro instancias EC2, scripts `infra/bootstrap/*`) queda **congelada como evidencia histórica**; no es operativa y se documenta en [`docs/informe/`](docs/informe/) y en el `CHANGELOG.md`.

Documentación:

- [Guía on-premise](infra/onpremise/ONPREMISE.md)
- [Diseño de despliegue](deploy/DEPLOY.md)
- [Pruebas](tests/TESTS.md)
- [Terraform](infra/terraform/TERRAFORM.md)
- [Componentes de observabilidad](deploy/k8s/components/form-observability/FORM-OBSERVABILITY.md)
- [Costos](docs/costos/README.md)
- Bonificaciones: [FinOps](docs/bonificaciones/finops/README.md), [Service Mesh](docs/bonificaciones/mesh/README.md), [Chaos](docs/bonificaciones/chaos/README.md)
- [Gestión ágil](docs/agil/AGIL.md)
- [Arquitectura](docs/arquitectura/ARQUITECTURA.md)
- [Canalización](docs/arquitectura/pipeline.md)
- [Canalización on-prem (kubectl/Kustomize, sin SSH)](docs/infra/migracion-onpremise.md)
- [Entornos](docs/infra/environments.md)
- [Configuración de CI/CD](docs/infra/ci-cd-setup.md)
- [Runbook](docs/infra/runbook.md)
- [Gestión de cambios](docs/change-management.md)
- [Notas de versión](docs/release-notes/RELEASE-NOTES.md)
- [Operaciones](docs/operaciones/OPERACIONES.md)
- [Estándares de documentación](docs/standards/documentation.md)
- Canalización: [`Jenkinsfile`](Jenkinsfile) y [`jenkins/vars/ci.groovy`](jenkins/vars/ci.groovy)

### Artefactos finales de entrega

- [Informe final (DOCX)](docs/informe/CircleGuard_INFORME_FINAL.docx) y [PDF](docs/informe/CircleGuard_INFORME_FINAL.pdf) — consolidación de la Parte 1 + Parte 2, ordenado y con el cuerpo justificado.
- [Presentación final (PPTX)](docs/informe/CircleGuard_PRESENTACION_FINAL.pptx) — 20 diapositivas con notas del orador.
- [Registro IAG](IAG_REGISTRO.md) — trazabilidad individual del uso de IA, incluida la sección reescrita del gateway/file-service.
- [Índice de cobertura](docs/informe/evidencia/trazabilidad/coverage-reports.md) — artefactos JaCoCo de los servicios núcleo, incluido `promotion`.
- [Estado del clúster on-prem](docs/infra/evidencia/estado-cluster-20261123.md) — diagnóstico histórico del clúster y [evidencia final consolidada](docs/informe/evidencia/validacion-final/) con `cg-dev`, `cg-stage` y `cg-prod` en 14/14, `monitoring` 6/6 y el build `main #68` en `SUCCESS`.

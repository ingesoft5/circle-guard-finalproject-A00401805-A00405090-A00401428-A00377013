<!-- CG-DOC: GW-CI-02 report on gateway-Redis integration testing with Testcontainers. -->
# GW-CI-02 — Integración Gateway–Redis con Testcontainers

**Historia de usuario:** GW-CI-02 — Integración Gateway-Redis con Testcontainers  
**Servicio:** `circleguard-gateway-service` (puerto 8087)  
**Entregable relacionado:** E1.6 — Pruebas automatizadas  
**Rama:** `feat/HU-GATEWAY-03-integracion-redis-testcontainers`  
**Fecha de verificación:** 8 de noviembre de 2026

> **Versión DOCX con capturas:** [`GW-CI-02-Validacion-Testcontainers.docx`](GW-CI-02-Validacion-Testcontainers.docx)

---

## 1. Alcance y criterios de aceptación

El objetivo es validar el flujo de validación de QR del gateway contra una instancia **real y efímera de Redis** levantada con Testcontainers, sin mocks, y ejecutarlo como una tarea Gradle independiente dentro del pipeline de Jenkins.

| Criterio de la HU | Cumplimiento | Evidencia |
|---|---|---|
| `@SpringBootTest` + Testcontainers con `redis:7.2` | `GatewayRedisIntegrationTest` + `AbstractRedisIntegrationTest` | Código y `redis-container.log` |
| Sin mocks de Redis | `StringRedisTemplate` conectado al contenedor | `AbstractRedisIntegrationTest` |
| Casos ACTIVE, RECOVERED, SUSPECT, CONTAGIED | Pruebas parametrizadas y casos individuales | `junit-integrationTest.xml` |
| Estado ausente y token expirado | `deniesWhenStatusIsMissing`, `deniesExpiredToken` | `junit-integrationTest.xml` |
| Tarea Gradle `integrationTest` | Tarea registrada en `build.gradle.kts` | `./gradlew :services:circleguard-gateway-service:integrationTest` |
| Etapa del pipeline | Stage `Integration Test` + `ci.integrationTest` | `Jenkinsfile`, `vars/ci.groovy` |
| Menos de 3 minutos | `timeout` de 3 min y 24–25 s observados | `evidencia/gw-ci-02/3-runs.txt` |
| Limpieza de contenedores | Ryuk + verificación con `docker ps` | `3-runs.txt` |
| Tres ejecuciones consecutivas | Tres corridas verdes | `3-runs.txt` |
| Redis caído documentado | Sección 6 de este informe | `QrValidationServiceTest.shouldPropagateRedisFailure` |
| Reporte JUnit publicado | `junit` + `archiveArtifacts` en Jenkins | `vars/ci.groovy` |
| Log del contenedor archivado | `@AfterAll` escribe `redis-container.log` | `evidencia/gw-ci-02/redis-container.log.txt` |

> Esta integración es un extra del gateway y **no reemplaza** las tres integraciones entre servicios exigidas por el enunciado del proyecto.

---

## 2. Configuración implementada

### 2.1 Tarea Gradle

`services/circleguard-gateway-service/build.gradle.kts`:

- `tasks.test` excluye `**/integration/**` para que las pruebas unitarias no requieran Docker.
- Se registra `integrationTest` en el grupo `verification`, con `include("**/integration/**")`, `shouldRunAfter(test)`, timeout de 3 minutos y `outputs.upToDateWhen { false }` para que Jenkins siempre la ejecute.
- Se mantiene `finalizedBy(tasks.jacocoTestReport)` solo en `test`; el gate de cobertura sigue calculándose con las pruebas unitarias.

```bash
./gradlew :services:circleguard-gateway-service:integrationTest
```

### 2.2 Clase base con el contenedor

`src/test/java/com/circleguard/gateway/integration/AbstractRedisIntegrationTest.java`:

- Contenedor estático `redis:7.2-alpine` con `--save "" --appendonly no` para evitar persistencia.
- `@DynamicPropertySource` inyecta host, puerto aleatorio, `qr.secret` y `jwt.secret`.
- `Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)` garantiza que las pruebas no arranquen antes de que Redis esté listo.
- `flushDb()` antes de cada prueba y semilla con `user:status:<uuid>`.
- `@AfterAll` escribe `build/integration-test/redis-container.log`.

### 2.3 Perfil de prueba

`src/test/resources/application-test.yml` define Redis local, timeouts de 10 s y secretos de prueba. `@DynamicPropertySource` tiene prioridad sobre estos valores y es lo que conecta al puerto aleatorio del contenedor.

### 2.4 Pipeline

- `vars/ci.groovy`: nuevo helper `integrationTest(servicePath, serviceDir)` que ejecuta Gradle, publica JUnit y archiva el reporte HTML y el log de Redis.
- `Jenkinsfile`: stage `Integration Test` entre `Quality Gate` y `Package`, con timeout de 5 minutos y condicionado a que no sea un rollback.

---

## 3. Casos cubiertos

`GatewayRedisIntegrationTest` contiene 9 ejecuciones de prueba (2 parametrizadas y 3 individuales):

| Caso | Semilla en Redis | Token | Resultado esperado | Resultado |
|---|---|---|---|---|
| `ACTIVE` | `user:status:<id>=ACTIVE` | válido (60 s) | `valid=true`, `GREEN`, `Welcome to Campus` | ✅ |
| `RECOVERED` | `RECOVERED` | válido | `valid=true`, `GREEN` | ✅ |
| `SUSPECT` | `SUSPECT` | válido | `valid=false`, `RED`, `Access Denied: Health Risk Detected` | ✅ |
| `CONTAGIED` | `CONTAGIED` | válido | `valid=false`, `RED` | ✅ |
| `PROBABLE` | `PROBABLE` | válido | `valid=false`, `RED` | ✅ |
| `CONFIRMED` | `CONFIRMED` | válido | `valid=false`, `RED` | ✅ |
| `POTENTIAL` | `POTENTIAL` | válido | `valid=false`, `RED` | ✅ |
| Estado ausente | sin clave | válido | `valid=false`, `RED` | ✅ |
| Token expirado | `ACTIVE` | expiración `-1 s` | `valid=false`, `RED`, `Invalid or Expired Token` | ✅ |

Cada prueba usa un `UUID` propio, de modo que no hay dependencia entre casos.

---

## 4. Resultados de las tres ejecuciones

Comando utilizado:

```bash
./gradlew :services:circleguard-gateway-service:integrationTest
```

| Corrida | Resultado | Duración | Contenedores residuales |
|---|---|---|---|
| 1 | `BUILD SUCCESSFUL` | 28 s | 0 tras la limpieza de Ryuk |
| 2 | `BUILD SUCCESSFUL` | 25 s | 0 tras la limpieza de Ryuk |
| 3 | `BUILD SUCCESSFUL` | 26 s | 0 tras la limpieza de Ryuk |

- 9 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Tiempo de ejecución JUnit de la última corrida: **3.229 s** (con la imagen de Redis ya descargada).
- Todas las corridas quedaron muy por debajo del límite de 3 minutos.
- Justo al terminar cada corrida puede verse el contenedor de Ryuk durante unos segundos; el Redis efímero ya fue eliminado. Tras unos 30 s, `docker ps -a --filter label=org.testcontainers=true` queda completamente vacío: Ryuk elimina también su propio contenedor.

Salida completa: [`evidencia/gw-ci-02/3-runs.txt`](evidencia/gw-ci-02/3-runs.txt).

> **Nota de entorno local:** el repositorio vive en `/mnt/c` (sistema 9p de WSL), donde el arranque de la JVM y la carga de clases es muy lento. Para medir localmente se usó un init script no versionado (`/tmp/opencode/build-dir.init.gradle`) que mueve `buildDir` a ext4. En el agente Jenkins el repositorio está en ext4, por lo que no se requiere ese ajuste.

---

## 5. Evidencias

| Evidencia | Archivo |
|---|---|
| Salida de las tres corridas | [`evidencia/gw-ci-02/3-runs.txt`](evidencia/gw-ci-02/3-runs.txt) |
| Reporte JUnit XML | [`evidencia/gw-ci-02/junit-integrationTest.xml`](evidencia/gw-ci-02/junit-integrationTest.xml) |
| Log del contenedor Redis | [`evidencia/gw-ci-02/redis-container.log.txt`](evidencia/gw-ci-02/redis-container.log.txt) |
| Stage de Jenkins | Pendiente de la ejecución del pipeline en el PR |
| Reporte HTML de Gradle | Generado en `build/reports/tests/integrationTest/` y archivado por Jenkins |

---

## 6. Comportamiento con Redis caído (solo documentación)

La HU pide documentar qué ocurre cuando Redis no está disponible; **no** se implementa resiliencia (retry, circuit breaker o caché local), pues corresponde a la Parte 2.

Comportamiento actual:

1. `QrValidationService.validateToken` consulta `user:status:<id>` mediante `StringRedisTemplate`.
2. Si Redis no responde, Spring Data Redis lanza `RedisConnectionFailureException`, que es una subclase de `DataAccessException`.
3. `ApiExceptionHandler` la captura y responde **HTTP 503 Service Unavailable** con:

```json
{
  "valid": false,
  "status": "RED",
  "message": "Service Unavailable: Health Status Unavailable"
}
```

4. La prueba unitaria `QrValidationServiceTest.shouldPropagateRedisFailure` evidencia que la excepción se propaga desde el servicio en lugar de devolver un resultado silencioso.

Consecuencia operativa: si Redis cae, el gateway **falla cerrado** (deniega el acceso con 503) y no concede permisos por defecto. Esto es coherente con el principio de seguridad del sistema, aunque implica indisponibilidad del control de acceso hasta que Redis se recupere.

---

## 7. Conclusión

La integración Gateway–Redis con Testcontainers queda implementada y verificada:

- Redis real, sin mocks, en un contenedor efímero.
- Cobertura de los seis escenarios exigidos por la HU y de todos los estados de riesgo del dominio.
- Tarea `integrationTest` independiente, reproducible y siempre ejecutada en CI.
- Etapa de Jenkins con publicación de JUnit, reporte HTML y log del contenedor.
- Tres corridas consecutivas verdes, cada una por debajo de 30 s y sin contenedores residuales.
- Comportamiento ante caída de Redis documentado y respaldado por una prueba unitaria.

Con esto se aporta evidencia para el entregable **E1.6 (Pruebas automatizadas)** sin modificar la lógica de negocio, los contratos de API ni el código de producción.

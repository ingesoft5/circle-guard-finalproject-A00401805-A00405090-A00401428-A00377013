<!-- CG-DOC: Registry of generative AI usage and human verification. -->
# Registro de Uso de Inteligencia Artificial Generativa (IAG) — CircleGuard

**Curso:** Ingeniería de Software V (09776 - TIC)  
**Universidad Icesi — Departamento de Computación y Sistemas Inteligentes**  
**Política de uso declarada:** **Nivel 3 — Colaboración con IAG (con registro de prompts)**

> **Declaración de uso:**  
> De acuerdo con las directrices del proyecto final (Secciones 2.4 y 3.1 — Entregable E1.8), este documento registra el trabajo colaborativo con herramientas de Inteligencia Artificial Generativa durante el ciclo de vida DevOps de **CircleGuard**. Para cada intervención se documenta el análisis e insumo propio elaborado previamente por el estudiante, el prompt exacto empleado, las validaciones/ajustes manuales aplicados sobre la salida generada y una reflexión crítica sobre el resultado.

---

## Tabla de Contenido

1. [Módulo: `circleguard-auth-service` e `identity-service` (Faiber Piedrahita)](#1-módulo-circleguard-auth-service-e-identity-service-faiber-piedrahita)
   - [1.1 HU-AUTH-01 (DCG-77) — Diagnóstico de estructura y dependencias de `auth-service`](#11-hu-auth-01-dcg-77--diagnóstico-de-estructura-y-dependencias-de-auth-service)
   - [1.2 HU-AUTH-02 (DCG-78) — Automatizar las pruebas unitarias y de integración de `auth-service`](#12-hu-auth-02-dcg-78--automatizar-las-pruebas-unitarias-y-de-integración-de-auth-service)
2. [GW-CI-02 — Integración Gateway–Redis con Testcontainers](#gw-ci-02)
3. [Bitácora E1.2 — Idempotencia, evidencias y cierre documental](#bitacora-e12)
4. [Módulo: `circleguard-promotion-service` (Alejandro Quiñones Caicedo)](#4-módulo-circleguard-promotion-service-alejandro-quiñones-caicedo)
   - [4.1 HU-PROMO-01 (DCG-304) — Diagnosticar la estructura y dependencias de `promotion-service`](#41-hu-promo-01-dcg-304--diagnosticar-la-estructura-y-dependencias-de-promotion-service)

---

## 1. Módulo: `circleguard-auth-service` e `identity-service` (Faiber Piedrahita)

### 1.1 HU-AUTH-01 (DCG-77) — Diagnóstico de estructura y dependencias de `auth-service`

- **Fecha:** 4 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-77` (`DCG-87`, `DCG-88`, `DCG-89`)
- **Objetivo de la interacción:** Verificar la estructura técnica, compilación y pruebas de `circleguard-auth-service` (`DCG-89`) y consolidar el documento formal de diagnóstico arquitectónico y matriz de dependencias en `docs/arquitectura/diagnostico-auth-service.md` (`DCG-87` y `DCG-88`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Tras revisar manualmente el código fuente de `services/circleguard-auth-service/`, identifiqué y documenté previamente los siguientes hallazgos técnicos como insumo base:

1. **Puerto y tecnología:** Java 21 con Spring Boot `3.2.4`, expone el puerto `8180` (`application.yml`), usando `io.jsonwebtoken:jjwt-*:0.11.5`.
2. **Controladores y rutas HTTP:**
   - `LoginController` (`/api/v1/auth/login` y `/api/v1/auth/visitor/handoff`): autentica con `AuthenticationManager`, solicita el `anonymousId` a `identity-service` mediante `IdentityClient` y emite un JWT con `JwtTokenService`.
   - `QrTokenController` (`/api/v1/auth/qr/generate`): genera un token QR de corta duración (`QrTokenService`) a partir del `anonymousId` del usuario autenticado.
   - `UserController` (`/api/v1/users/permissions/{permissionName}`): consulta en `LocalUserRepository` los usuarios que poseen un permiso específico.
3. **Seguridad y RBAC:**
   - `DualChainAuthenticationProvider`: intenta autenticar primero contra OpenLDAP (`LdapAuthenticationProvider`) y, si falla, hace fallback a la base de datos local PostgreSQL (`DaoAuthenticationProvider` + `CustomUserDetailsService`).
   - Modelos JPA: `LocalUser` (`local_users`), `Role` (`roles`), `Permission` (`permissions`) y tablas puente `user_roles` y `role_permissions`.
   - Migraciones Flyway: `V1__initial_auth_schema.sql` a `V5__add_dashboard_permission.sql` (sembrando roles `HEALTH_CENTER`, `GATE_STAFF`, `STUDENT` y usuarios `staff_guard`, `health_user`, `super_admin`).
4. **Problemas detectados para el pipeline CI/CD:**
   - En `IdentityClient.java` la URL hacia `identity-service` está quemada como `"http://localhost:8083/api/v1/identities/map"`, lo cual fallará al contenerizar el servicio.
   - En `src/main/resources/application.yml` las credenciales de PostgreSQL (`circleguard_auth`), OpenLDAP (`dc=circleguard,dc=edu`) y las semillas `jwt.secret` y `qr.secret` están en texto plano sin variables de entorno.
   - En `build.gradle.kts` aún no están declarados `spring-boot-starter-actuator` (para healthchecks) ni el plugin `jacoco` (para medir el $\geq 60\%$ de cobertura en `HU-AUTH-02`).

#### B. Prompt empleado

```text
Estoy desarrollando la historia de usuario HU-AUTH-01 (DCG-77): Diagnosticar la estructura y dependencias de `auth-service` del proyecto CircleGuard ubicado en:
`/home/faiber/Desktop/IngesoftV/final/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`

Tras revisar el código fuente de `services/circleguard-auth-service/`, identifiqué lo siguiente:
1. Puerto y tecnología: Java 21 con Spring Boot 3.2.4, expone el puerto `8180` (`application.yml`).
2. Controladores y rutas HTTP:
   - `LoginController` (`/api/v1/auth/login` y `/api/v1/auth/visitor/handoff`): autentica con `AuthenticationManager`, solicita el `anonymousId` a `identity-service` mediante `IdentityClient` y emite un JWT con `JwtTokenService`.
   - `QrTokenController` (`/api/v1/auth/qr/generate`): genera un token QR de corta duración (`QrTokenService`) a partir del `anonymousId` del usuario autenticado.
   - `UserController` (`/api/v1/users/permissions/{permissionName}`): consulta en `LocalUserRepository` los usuarios que poseen un permiso específico.
3. Seguridad y RBAC:
   - `DualChainAuthenticationProvider`: intenta autenticar primero contra OpenLDAP (`LdapAuthenticationProvider`) y, si falla, hace fallback a la base de datos local PostgreSQL (`DaoAuthenticationProvider` + `CustomUserDetailsService`).
   - Modelos JPA: `LocalUser`, `Role`, `Permission`.
   - Migraciones Flyway: `V1__initial_auth_schema.sql` a `V5__add_dashboard_permission.sql`.
4. Problemas detectados para el pipeline CI/CD:
   - En `IdentityClient.java` la URL hacia `identity-service` está quemada como `"http://localhost:8083/api/v1/identities/map"`, lo cual fallará al contenerizar el servicio.
   - En `src/main/resources/application.yml` las credenciales de PostgreSQL (`circleguard_auth`), OpenLDAP (`dc=circleguard,dc=edu`) y las semillas `jwt.secret` y `qr.secret` están en texto plano sin variables de entorno.
   - En `build.gradle.kts` aún no están declarados `spring-boot-starter-actuator` (para healthchecks) ni el plugin `jacoco` (para medir el >= 60% de cobertura en HU-AUTH-02).

---

A partir de lo que te especifiqué anteriormente y del archivo `Proyecto_Final_CircleGuard.pdf` adjunto, apóyame completando las 3 subtareas de HU-AUTH-01 (`DCG-87`, `DCG-88`, `DCG-89`):

1. Verificación en el código y compilación con Gradle (`DCG-89`):
   - Contrasta mi levantamiento con el código fuente de `services/circleguard-auth-service/` por si omití algún detalle técnico (filtros, configuración de seguridad en `SecurityConfig.java` o `JwtAuthenticationFilter.java`, o `src/test/`).
   - Ejecuta `./gradlew :services:circleguard-auth-service:test :services:circleguard-auth-service:assemble` y captura el resultado real de compilación y ejecución de pruebas.

2. Documentación técnica formal del diagnóstico (`DCG-87` y `DCG-88`):
   - Con base en mi análisis y la verificación, redacta y crea el archivo `docs/arquitectura/diagnostico-auth-service.md` incluyendo:
     - Resumen arquitectónico de `circleguard-auth-service` y diagrama Mermaid del flujo de autenticación dual (`LDAP` -> fallback `PostgreSQL` -> `IdentityClient` -> `JWT`).
     - Tabla completa de endpoints HTTP (método, ruta, controlador, entrada/salida y seguridad).
     - Detalle del modelo de datos RBAC y de las 5 migraciones Flyway (`V1` a `V5`).
     - Matriz de dependencias externas (`PostgreSQL`, `OpenLDAP`, `identity-service`) y tabla de variables de entorno que debemos parametrizar en `application.yml` e `IdentityClient.java`.
     - Evidencia verificable del comando Gradle ejecutado y sus resultados, junto con la lista de preparación para `HU-AUTH-02` y `HU-AUTH-03`.
```

#### C. Validación humana y hallazgos adicionales del contraste

- **Contraste con `SecurityConfig.java`:** Se verificó que el método `@Bean public LdapContextSource contextSource()` tiene quemados en Java `"ldap://localhost:389"`, `"dc=circleguard,dc=edu"`, `"cn=admin,dc=circleguard,dc=edu"` y `"admin"`, ignorando `application.yml`, y que `corsConfigurationSource()` fija `http://localhost:8081` y `http://localhost:8080`. Ambos puntos se incorporaron a la matriz de parametrización para `HU-AUTH-03`.
- **Contraste con `QrTokenService.java`:** Se detectó una discrepancia de unidades entre el valor por defecto en Java (`60000` ms) y el valor en `application.yml` (`300` ms).
- **Verificación de compilación (`DCG-89`):** Se verificó que `.gitignore` excluía `*.jar` (impidiendo versionar `gradle-wrapper.jar`) y que el proyecto exige el toolchain de **Java 21**. Ejecutando con OpenJDK 21 se validó `BUILD SUCCESSFUL in 23s` para `:services:circleguard-auth-service:test` y `:services:circleguard-auth-service:assemble`, generando `circleguard-auth-service-1.0.0-SNAPSHOT.jar` (`54 MB`).

#### D. Reflexión crítica

El uso colaborativo de la IAG (Nivel 3) permitió contrastar mi levantamiento inicial contra la totalidad de clases del módulo y descubrir acoplamientos ocultos adicionales (como el doble quemado de OpenLDAP y CORS en `SecurityConfig.java` y la exclusión de `gradle-wrapper.jar` en `.gitignore`) antes de pasar a la etapa de pruebas (`HU-AUTH-02`) y contenerización (`HU-AUTH-03`).

---

### 1.2 HU-AUTH-02 (DCG-78) — Automatizar las pruebas unitarias y de integración de `auth-service`

- **Fecha:** 5 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-78` (`DCG-90`, `DCG-91`, `DCG-92`, `DCG-93`)
- **Objetivo de la interacción:** Configurar el plugin JaCoCo con reportes XML/HTML y umbral mínimo de cobertura de líneas $\geq 60\%$ (`DCG-91`), implementar la batería completa de pruebas unitarias para controladores, servicios, proveedor dual, filtro JWT y cliente de identidad (`DCG-90`), desarrollar la prueba de integración reproducible del flujo de autenticación contra base de datos relacional (`DCG-92`) y verificar la estabilidad de las pruebas en ejecución limpia de Gradle con Java 21 (`DCG-93`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

A partir del diagnóstico realizado en `HU-AUTH-01` (`docs/arquitectura/diagnostico-auth-service.md`) y de la inspección detallada de las clases de `services/circleguard-auth-service/`, definí previamente el estado actual y el diseño de casos de prueba necesarios para cumplir con el Entregable E1.6 ($\geq 60\%$ de cobertura en servicios núcleo):

1. **Estado actual de pruebas y build (`build.gradle.kts` y `src/test/`):**
   - Solo existía `LoginControllerTest.java` con un único test (`shouldLoginSuccessfullyAndReturnAnonymizedToken`).
   - En `services/circleguard-auth-service/build.gradle.kts` no estaba configurado el plugin `jacoco` ni las tareas `jacocoTestReport` (XML/HTML para SonarQube y Jenkins) y `jacocoTestCoverageVerification` (mínimo `0.60` de cobertura de líneas).
   - En `src/test/resources/application.yml` faltaba definir `qr.secret` y `qr.expiration` (requeridos por `QrTokenService` al levantar el contexto completo de Spring) y deshabilitar Flyway en pruebas con H2 (`spring.flyway.enabled: false`).
2. **Diseño de casos unitarios por componente (`DCG-90`):**
   - `LoginController`: login exitoso (`200`), credenciales inválidas cuando `AuthenticationManager` lanza `BadCredentialsException` (`401`), error inesperado cuando falla `IdentityClient` (`500`), `/api/v1/auth/visitor/handoff` exitoso (`200`), y handoff sin `anonymousId` (`400`).
   - `QrTokenController`: `GET /api/v1/auth/qr/generate` autenticado devolviendo `qrToken` y `expiresIn: "60"` (`200`), y petición sin autenticar (`403`).
   - `UserController`: `GET /api/v1/users/permissions/{permissionName}` retornando la lista mapeada de `username` y `email` (incluyendo mapeo de `email == null` a `""`).
   - `DualChainAuthenticationProvider`: éxito en el proveedor primario (`LdapAuthenticationProvider`), fallback exitoso al proveedor secundario (`DaoAuthenticationProvider`) cuando LDAP lanza `AuthenticationException`, fallo cuando ambos proveedores lanzan excepción y validación de `supports(UsernamePasswordAuthenticationToken.class)`.
   - `CustomUserDetailsService`: carga de usuario activo con roles y permisos aplanados (`ROLE_<name>` + permisos), usuario con `isActive = false` lanzando `DisabledException`, y usuario inexistente lanzando `UsernameNotFoundException`.
   - `JwtTokenService` y `QrTokenService`: generación y firma `HS256` verificando `subject == anonymousId` y el claim `permissions`.
   - `JwtAuthenticationFilter`: población del `SecurityContextHolder` con un header `Authorization: Bearer <jwt válido>`, limpieza del contexto ante un token inválido/expirado sin interrumpir el `FilterChain`, y paso normal sin header `Bearer`.
   - `IdentityClient`: parametrización de la URL con `@Value("${identity.service.url:http://localhost:8083/api/v1/identities/map}")` e inyección de `RestTemplate` para probar unitariamente `getAnonymousId(realIdentity)` con `MockRestServiceServer`.
3. **Diseño de prueba de integración y estabilidad en CI (`DCG-92` y `DCG-93`):**
   - Prueba de integración (`@SpringBootTest` + `@AutoConfigureMockMvc`) del flujo de extremo a extremo de `auth-service`: persistencia de `Permission`, `Role` y `LocalUser` con `BCryptPasswordEncoder` en base de datos relacional, login con fallback desde LDAP a `DaoAuthenticationProvider`, anonimización con `IdentityClient`, uso del JWT emitido para invocar `/api/v1/auth/qr/generate` y consulta en `/api/v1/users/permissions/alert:receive_priority`.

#### B. Prompt empleado

```text
Estoy desarrollando la historia de usuario HU-AUTH-02 (DCG-78): Automatizar las pruebas unitarias y de integración de `auth-service` del proyecto CircleGuard ubicado en:
`/home/faiber/Desktop/IngesoftV/final/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`

A partir del diagnóstico que hicimos en HU-AUTH-01 (`docs/arquitectura/diagnostico-auth-service.md`) y de revisar las clases actuales de `services/circleguard-auth-service/`, identifiqué el siguiente estado y los casos de prueba que necesito cubrir para cumplir con el Entregable E1.6 (>= 60% de cobertura en servicios núcleo):

1. Estado actual de pruebas y build (`build.gradle.kts` y `src/test/`):
   - Actualmente solo existe `LoginControllerTest.java` con un único test (`shouldLoginSuccessfullyAndReturnAnonymizedToken`).
   - En `services/circleguard-auth-service/build.gradle.kts` no está configurado el plugin `jacoco` ni las tareas `jacocoTestReport` (XML/HTML para SonarQube y Jenkins) y `jacocoTestCoverageVerification` (mínimo 0.60 de cobertura de líneas).
   - En `src/test/resources/application.yml` falta definir `qr.secret` y `qr.expiration` (requeridos por `QrTokenService` cuando se levanta el contexto completo de Spring) y deshabilitar migraciones/checks externos innecesarios en pruebas aisladas.

2. Casos unitarios que identifiqué por componente (`DCG-90`):
   - `LoginController`: además del login exitoso (200), falta probar credenciales inválidas cuando `AuthenticationManager` lanza `BadCredentialsException` (401), error inesperado cuando falla `IdentityClient` (500), `/api/v1/auth/visitor/handoff` exitoso devolviendo `token` y `handoffPayload` (200), y handoff sin `anonymousId` (400).
   - `QrTokenController`: probar `GET /api/v1/auth/qr/generate` autenticado devolviendo `qrToken` y `expiresIn: "60"` (200), y petición sin autenticar (403).
   - `UserController`: probar `GET /api/v1/users/permissions/{permissionName}` retornando la lista mapeada de `username` y `email` (incluyendo cuando `email` es nulo, que debe mapear a `""`).
   - `DualChainAuthenticationProvider`: probar autenticación exitosa en el proveedor primario (`LdapAuthenticationProvider`), fallback exitoso al proveedor secundario (`DaoAuthenticationProvider`) cuando LDAP lanza `AuthenticationException`, fallo cuando ambos proveedores lanzan excepción, y validación de `supports(UsernamePasswordAuthenticationToken.class)`.
   - `CustomUserDetailsService`: probar carga de usuario activo con roles y permisos aplanados (`ROLE_<name>` + permisos), usuario con `isActive = false` lanzando `DisabledException`, y usuario inexistente lanzando `UsernameNotFoundException`.
   - `JwtTokenService` y `QrTokenService`: probar la generación y firma HS256 de ambos tokens verificando que el `subject` sea el `anonymousId` y que el JWT incluya el claim `permissions`.
   - `JwtAuthenticationFilter`: probar que un header `Authorization: Bearer <jwt válido>` pueble el `SecurityContextHolder` con el `anonymousId` y sus autoridades, que un token inválido/expirado limpie el contexto sin romper la cadena de filtros, y que una petición sin header `Bearer` continúe normalmente.
   - `IdentityClient`: parametrizar la URL con `@Value("${identity.service.url:http://localhost:8083/api/v1/identities/map}")` y permitir inyectar/usar `RestTemplate` para poder probar unitariamente `getAnonymousId(realIdentity)` sin requerir un servidor externo levantado en `localhost:8083`.

3. Prueba de integración y estabilidad en CI (`DCG-92` y `DCG-93`):
   - Implementar una prueba de integración del flujo completo de autenticación contra base de datos relacional y resolución de identidad (`IdentityClient`), asegurando que todas las pruebas pasen con `./gradlew :services:circleguard-auth-service:test :services:circleguard-auth-service:jacocoTestReport :services:circleguard-auth-service:jacocoTestCoverageVerification` usando Java 21 sin depender de un OpenLDAP externo.

---

Con base en este diseño previo y el archivo `Proyecto_Final_CircleGuard.pdf` adjunto, apóyame completando las 4 subtareas de HU-AUTH-02 (`DCG-90`, `DCG-91`, `DCG-92`, `DCG-93`):

1. Configura `jacoco` en `services/circleguard-auth-service/build.gradle.kts` (`DCG-91`) generando reportes XML y HTML y exigiendo una cobertura mínima de líneas >= 60% (`0.60`).
2. Implementa las pruebas unitarias para los controladores, servicios, proveedor dual, filtro JWT y cliente de identidad (`DCG-90`), haciendo el ajuste mínimo necesario en `IdentityClient.java` (inyección de URL/RestTemplate manteniendo compatibilidad) y en `src/test/resources/application.yml` (`DCG-93`).
3. Implementa la prueba de integración del flujo de autenticación (`DCG-92`) de modo que sea 100% reproducible en local y en CI.
4. Ejecuta con Java 21 las tareas `:services:circleguard-auth-service:test`, `:services:circleguard-auth-service:jacocoTestReport` y `:services:circleguard-auth-service:jacocoTestCoverageVerification`, verifica el porcentaje exacto de cobertura alcanzado en el reporte JaCoCo y registra esta interacción en una nueva sección `1.2 HU-AUTH-02 (DCG-78)` dentro de `IAG_REGISTRO.md` manteniendo el mismo formato de Nivel 3.
```

#### C. Validación humana y ajustes realizados

- **Ajuste de contexto `@WebMvcTest` en `QrTokenControllerTest` y `UserControllerTest`:** Durante la primera ejecución de pruebas, se detectó que al importar `SecurityConfig.class` en slices `@WebMvcTest` que no declaran `@MockBean AuthenticationManager`, Spring requiere los beans `JwtAuthenticationFilter` y `DualChainAuthenticationProvider` en el contexto del test. Se ajustó la anotación `@Import({SecurityConfig.class, JwtAuthenticationFilter.class, DualChainAuthenticationProvider.class})` en ambos tests, logrando que las **24 pruebas (unitarias y de integración)** pasen sin errores (`0 failures`).
- **Verificación de métricas reales en el reporte XML/HTML de JaCoCo (`build/reports/jacoco/test/jacocoTestReport.xml`):**
  - **Cobertura de líneas (`LINE`):** **`97.85%`** (`182 / 186` líneas cubiertas; supera ampliamente el mínimo exigido de `60%`).
  - **Cobertura de instrucciones (`INSTRUCTION`):** **`66.95%`** (`1019 / 1522`; las instrucciones no cubiertas corresponden únicamente a métodos `equals`/`hashCode`/`toString` autogenerados por `@Data` de Lombok en las entidades JPA).
  - **Cobertura de clases (`CLASS`):** **`100.00%`** (`17 / 17` clases).
  - **Cobertura de métodos (`METHOD`):** **`75.93%`** (`82 / 108` métodos).
  - Todas las clases de negocio, seguridad y controladores (`LoginController`, `QrTokenController`, `UserController`, `DualChainAuthenticationProvider`, `JwtAuthenticationFilter`, `SecurityConfig`, `CustomUserDetailsService`, `JwtTokenService`, `QrTokenService`, `IdentityClient`) alcanzaron **`100.00%` de cobertura de líneas**.

#### D. Reflexión crítica

El diseño previo caso por caso a partir del diagnóstico de `HU-AUTH-01` permitió orientar a la IAG con precisión sobre qué escenarios de borde probar (como el fallback de LDAP a PostgreSQL cuando `LdapAuthenticationProvider` lanza `AuthenticationException`, el bloqueo de cuentas con `isActive = false`, y la limpieza del `SecurityContextHolder` ante tokens malformados). Además, el ajuste de constructores en `IdentityClient` con `@Value` y `RestTemplate` no solo desbloqueó las pruebas unitarias con `MockRestServiceServer`, sino que dejó listo el cliente para su contenerización en `HU-AUTH-03`.


## HU-GATEWAY-CICD-01 — Plataforma CI/CD del gateway

- **Nivel de uso:** 3 (colaboración con IAG).
- **Herramienta:** OpenCode / DeepSeek.
- **Contenido propio entregado:** estructura del repositorio, hallazgos del análisis del gateway, requisitos del PDF, decisiones de topología (2 instancias, `dev`/`stage`/`prod`).
- **Prompts principales:**
  - Analizar la estructura del microservicio Gateway.
  - Validar el plan de la HU contra el PDF del proyecto.
  - Ajustar la HU a la Parte 1 / Sprint 1 y al microservicio gateway.
  - Generar scripts Bash idempotentes, Docker Compose y pipeline de Jenkins.
- **Resultado:** integración de `e12-bootstrap` (idempotencia) con el aprovisionamiento AWS y el pipeline del gateway; scripts `infra/bootstrap-*.sh`, compose de herramientas y ambientes, Jenkinsfile, Shared Library, correcciones de seguridad del gateway, pruebas y documentación.
- **Verificación humana:** el equipo debe revisar y ejecutar cada script, validar las credenciales y estar en capacidad de explicar la configuración en la sustentación.

https://claude.ai/chat/34eb30b0-e91f-4b8b-afba-8f22890194c6

---

<a id="gw-ci-02"></a>
## GW-CI-02 — Integración Gateway–Redis con Testcontainers (8 de noviembre de 2026)

- **Nivel de uso:** 3 (colaboración con IAG).
- **Herramienta:** OpenCode / Big Pickle.
- **Historia / Entregable:** `GW-CI-02` — E1.6 (pruebas automatizadas).
- **Rama:** `feat/HU-GATEWAY-03-integracion-redis-testcontainers`.

### A. Contenido propio entregado a la IAG

1. **Requisitos de la HU:** leer la sección 5 de `CircleGuard_HU_CICD_Gateway_File.pdf` y contrastarla con `Proyecto_Final_CircleGuard.pdf` (E1.6 exige ≥ 60 % de cobertura en gateway, ≥ 3 integraciones entre servicios y 3 flujos E2E).
2. **Diagnóstico del estado actual:** el repositorio ya tenía `GatewayRedisIntegrationTest` con Testcontainers, pero solo cubría `ACTIVE`, `SUSPECT` y estado ausente; las pruebas de integración corrían dentro de `test`, no existía tarea `integrationTest` ni etapa de Jenkins.
3. **Decisiones acordadas:** imagen `redis:7.2-alpine`, separación por filtro de paquete (`**/integration/**`), JaCoCo solo con unitarias, commits Conventional Commits sin clave `[CG-02]`, sin cambios en lógica de negocio ni contratos de API.
4. **Criterios de aceptación:** casos ACTIVE, RECOVERED, SUSPECT, CONTAGIED, estado ausente y token expirado; tarea Gradle propia; etapa de pipeline; < 3 minutos; limpieza de contenedores; 3 corridas consecutivas; Redis caído solo documentado.
5. **Restricciones del entorno:** WSL2 con el repositorio en `/mnt/c` (9p), sin Java ni Docker instalados inicialmente.

### B. Prompts empleados

```text
Dame el plan completo
```

```text
Ejecuta el plan
```

### C. Validación humana y hallazgos

- **Entorno:** se instaló Temurin JDK 21 en `~/.local/opt/jdk-21` sin `sudo` y Docker Engine 29.1.3 + Compose v2 mediante `wsl.exe -u root`; se habilitó el socket para el usuario.
- **Casos:** se amplió la suite a 9 ejecuciones (2 parametrizadas y 3 individuales), incluyendo `RECOVERED`, `CONTAGIED`, `PROBABLE`, `CONFIRMED`, `POTENTIAL` y token expirado.
- **Hallazgo 1 — Redis no listo:** las primeras corridas fallaban con `RedisConnectionFailureException` porque el contenedor aceptaba conexiones antes de inicializar Redis. Se añadió `Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)`.
- **Hallazgo 2 — timeouts en WSL:** con timeouts de 2 s el arranque de Lettuce en `/mnt/c` agotaba el tiempo; se ajustaron a 10 s en `application-test.yml`.
- **Hallazgo 3 — rendimiento de 9p:** la tarea superaba los 3 minutos por la carga de clases desde `/mnt/c`. Se verificó con `gradle/wsl-ext4.init.gradle.kts`, que mueve `buildDir` a ext4; en Jenkins el checkout está en ext4 y no requiere ajuste.
- **Resultado:** tres corridas consecutivas verdes de 28 s, 25 s y 26 s; 9 pruebas, 0 fallos; contenedores eliminados por Ryuk; JaCoCo unitario se mantiene en 95.92 % de líneas.
- **Sin cambios de producción:** solo se modificaron `build.gradle.kts`, `Jenkinsfile`, `jenkins/vars/ci.groovy`, pruebas y documentación.

### D. Reflexión crítica

El valor de esta HU estuvo menos en escribir pruebas nuevas y más en hacer explícitas las condiciones que las vuelven confiables: esperar la señal real de readiness de Redis, separar la suite de integración de la unitaria y medir el tiempo en el mismo tipo de filesystem que usa el agente. Las primeras corridas fallidas evidenciaron que un contenedor “arriba” no implica un servicio listo. También quedó claro que el límite de 3 minutos es una restricción de infraestructura tanto como de código: el mismo test tarda 3 s de ejecución JUnit pero más de 3 minutos de tarea cuando el classpath vive en 9p. Documentar el comportamiento ante caída de Redis sin implementar resiliencia mantiene el alcance de la Parte 1 y deja preparado el terreno para la Parte 2.

---

<a id="bitacora-e12"></a>
## Bitácora E1.2 — Idempotencia, evidencias y cierre documental (8 de noviembre de 2026)

- **Nivel de uso:** 3 (colaboración con IAG).
- **Herramienta:** OpenCode / DeepSeek.
- **Historia / Entregable:** `HU-GATEWAY-CICD-01` — E1.2 (plataforma CI/CD del gateway).
- **Rama de validación:** `test/HU-INFRA-01-idempotencia`.
- **Rama del PR:** `feat/HU-GATEWAY-01-configurar-servidores` (PR-15).

### A. Contenido propio entregado a la IAG

1. **Decisiones de flujo:** GitHub Flow con PR hacia `main`; sin rama `dev` para código. La validación de idempotencia debía ejecutarse en una rama separada para no alterar PR-15.
2. **Invariantes a comprobar:** IDs, IPs, estado y tipo de las 4 instancias EC2; Elastic IP; Security Groups; key pair; hashes SHA-256 de `.env` local y remoto; IDs e imágenes de contenedores; repositorios y usuarios de Nexus; proyectos y usuarios de SonarQube.
3. **Criterio de evidencia:** no imprimir secretos; comparar hashes; guardar `before`/`after` y un `diffs.txt` vacío como prueba de convergencia.
4. **Criterio documental:** el informe debía vivir en el PR con capturas reales de CLI, Jenkins, SonarQube y Nexus; la rama de prueba queda como respaldo. Se adoptó `.docx` como único formato de entrega.
5. **Endurecimiento solicitado en revisión:** eliminar el workflow vacío, restringir Jenkins/Sonar/Nexus a CIDR administrativo y docente, cerrar `443` y dividir futuros PRs por dominio.

### B. Prompts empleados

```text
Ejecuta el plan
```

```text
Restaura el informe de branching de GitHub FLow y ponlo en el commit,
tambien debo hacer un informe de la idempotencia
```

```text
Ejecuta el Plan Informes-E1.2 — Branching restaurado e idempotencia
```

```text
Agrega captura al informe de idempotencia
```

```text
Añade capturas reales
```

```text
De ahora en adelante quiero solo docx
```

```text
Ahora dame el plan cmpleto con las observations tambien
```

```text
Ejecuta el Plan completo — E1.2, observaciones y bitácora IAG
```

### C. Validación humana y hallazgos

- **Idempotencia:** segunda ejecución de `./infra/bootstrap/bootstrap-all.sh` con `exit=0` y `diffs.txt` de **0 líneas**, repetida tras el endurecimiento.
- **Bug 1 — sincronización de `.env`:** el `.env` remoto se copiaba solo a `infra/state/tools.env`, no a `deploy/compose/tools/.env`; `SONAR_TOKEN` y las contraseñas del profesor se regeneraban. Se corrigió la copia y se añadieron las claves faltantes.
- **Bug 2 — reconstrucción de Jenkins:** `compose_up` usaba `--build` siempre y la etiqueta de imagen quedaba desalineada del contenedor. Ahora solo construye si la imagen no existe.
- **Bug 3 — credenciales del profesor:** SonarQube creaba `professor` con `|| true` y Nexus devolvía 204 sin cambiar la contraseña; ambos usuarios quedaban sin autenticación. Se añadió sincronización condicional mediante `change_password` / `change-password`.
- **Bug 4 — reglas de Security Group:** el bootstrap solo agregaba reglas y nunca revocaba las obsoletas. Se añadió `reconcile_cidr`; `8080`, `9000` y `8081` quedaron limitados a `ADMIN_CIDR` y `TEACHER_CIDR`, `443` se cerró y `80` permanece público para el gateway de dev.
- **Workflow vacío:** se eliminó `require-two-approvals.yml`, que acumulaba 37 runs fallidas. La protección de `main` sigue exigiendo 2 aprobaciones.
- **Squash merge:** se ajustó `enforce-pr.yml` para aceptar commits con formato `título (#N)`.
- **Trigger real:** se verificó que Jenkins no usa webhooks; los builds son “Started by timer” por `cron('H/5 * * * *')`. Por eso no se dejó el allowlist de GitHub en `8080`.
- **CI:** PR-15 se mantuvo en verde con el último build observado en estado `SUCCESS`.
- **Informe:** `docs/informe/E1.2-Idempotencia.docx` con 4 capturas reales embebidas (CLI, Jenkins, SonarQube, Nexus) y secciones 6.3 y 6.4.
- **Observaciones no accionables:** el PR de 447 archivos no se divide retroactivamente; se adopta la convención de separar infraestructura, pipeline, código y evidencia en futuras entregas. La evidencia binaria permanece en el historial.

### D. Reflexión crítica

La validación de idempotencia expuso defectos que una sola ejecución del bootstrap ocultaba: desincronización de secretos, reconstrucción innecesaria de Jenkins y usuarios que existían pero no podían autenticarse. El mismo patrón apareció en los Security Groups: agregar reglas sin revocarlas hacía que el estado convergiera solo en apariencia. La verificación del trigger real de Jenkins evitó añadir una allowlist de GitHub innecesaria y mantuvo `8080` con la menor exposición posible. Queda pendiente obtener las 2 aprobaciones, mergear PR-15 y ejecutar el CD en `main` con evidencia de `dev`, `stage`, `prod` y rollback.
---

## 4. Módulo: `circleguard-promotion-service` (Alejandro Quiñones Caicedo)

### 4.1 HU-PROMO-01 (DCG-304) — Diagnosticar la estructura y dependencias de `promotion-service`

- **Fecha:** 7 de noviembre de 2026
- **Autor:** Alejandro Quiñones Caicedo
- **Historia / Subtareas en Jira:** `DCG-304` (`DCG-322`, `DCG-323`, `DCG-324`)
- **Objetivo de la interacción:** Consolidar el diagnóstico arquitectónico de `circleguard-promotion-service` en `docs/arquitectura/diagnostico-promotion-service.md`, cubriendo estructura, endpoints, modelo de datos, dependencias externas y el estado real de compilación y pruebas.

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Tras revisar manualmente `services/circleguard-promotion-service/`, levanté como insumo base:

1. **Puerto y tecnología:** Java 21 (toolchain de la raíz) con Spring Boot `3.2.4`, puerto `8088`, `jjwt 0.11.5`, Flyway y Spring Data Neo4j.
2. **Controladores:** 11 clases (`AccessPoint`, `Admin`, `Building`, `Circle`, `Encounter`, `Floor`, `HealthStats`, `HealthStatus`, `LocationSignal`, `Mesh`, `SessionHandshake`) con 35 rutas.
3. **Dependencias:** PostgreSQL (`circleguard_promotion`), Neo4j (`bolt://localhost:7687`), Redis (`localhost:6379`), Kafka (`localhost:9092`, grupo `promotion-service-group`).
4. **Migraciones:** `V1` (topología y `system_settings`), `V2` (parámetros de tiempos), `V3` (coordenadas del edificio).
5. **Pruebas:** 8 clases de prueba; al ejecutar la suite, 16 reportados (13 ejecutados + 3 `initializationError`), con 5 fallos.

#### B. Prompt empleado

```text
Estoy desarrollando HU-PROMO-01 (DCG-304): Diagnosticar la estructura y dependencias de
`promotion-service` del proyecto CircleGuard, repositorio local en
`D:\GitHub\circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`.

Levanté manualmente:
- 11 controladores con 35 rutas, seguridad method-level con @PreAuthorize.
- Dependencias: PostgreSQL circleguard_promotion, Neo4j bolt://localhost:7687,
  Redis 6379, Kafka 9092 grupo promotion-service-group.
- Migraciones V1, V2 y V3.
- 8 clases de prueba; la suite reporta 16 tests con 5 fallos.

Contrastá mi levantamiento con el código fuente y completá las tres subtareas:
1. DCG-322: estructura, catálogo de endpoints con su regla de seguridad efectiva y
   modelo de datos con las migraciones reales.
2. DCG-323: matriz de dependencias externas, eventos Kafka producidos y consumidos,
   uso de Redis y variables a externalizar para HU-PROMO-03.
3. DCG-324: evidencia real de compilación y pruebas, con causa raíz de los fallos.
Entregá el resultado como documento Markdown en docs/arquitectura/.
```

#### C. Validación humana y hallazgos adicionales del contraste

- Se verificó la compilación con `./gradlew :services:circleguard-promotion-service:compileJava` (BUILD SUCCESSFUL) y la suite con `:test` (16 casos, 5 fallos).
- La revisión de un compañero detectó imprecisiones en la primera versión del documento: el inventario de endpoints estaba incompleto (faltaban dos rutas de `EncounterController`), la conclusión de seguridad ignoraba `@EnableMethodSecurity`, el modelo ER no coincidía con las migraciones y el flujo de estados atribuía a `StatusLifecycleService` una orquestación que no realiza. Todas se corrigieron contrastando el código fuente.
- La causa de los `403` se identificó como `hasRole('HEALTH_CENTER')` exigiendo `ROLE_HEALTH_CENTER`, no como un problema de `@WithMockUser` contra `STATELESS`.

#### D. Reflexión crítica

La IAG aceleró la redacción y el inventario, pero también produjo afirmaciones plausibles y falsas (un conteo de rutas incompleto y una conclusión de seguridad que contradecía las anotaciones del código). La lección es que el diagnóstico debe cerrarse contra el código y contra una revisión externa: sin ese contraste, el documento habría desviado el trabajo de las HU siguientes.

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
   - [1.3 HU-AUTH-03 (DCG-79) — Empaquetar `auth-service` e integrarlo al pipeline de CI](#13-hu-auth-03-dcg-79--empaquetar-auth-service-e-integrarlo-al-pipeline-de-ci)
   - [1.4 HU-AUTH-04 (DCG-80) — Desplegar `auth-service` en `dev`, `stage` y `prod` y validar flujo E2E de autenticación](#14-hu-auth-04-dcg-80--desplegar-auth-service-en-dev-stage-y-prod-y-validar-flujo-e2e-de-autenticación)
   - [1.5 HU-ID-04 (DCG-85) — Desplegar `identity-service` en `dev`, `stage` y `prod` y validar anonimización E2E](#15-hu-id-04-dcg-85--desplegar-identity-service-en-dev-stage-y-prod-y-validar-anonimización-e2e)
2. [Módulo: `circleguard-gateway-service` e Infraestructura CI/CD (Daniela Castaño)](#2-módulo-circleguard-gateway-service-e-infraestructura-cicd-daniela-castaño)
   - [2.1 HU-GATEWAY-CICD-01 — Plataforma CI/CD del gateway](#21-hu-gateway-cicd-01--plataforma-cicd-del-gateway)
   - [2.2 Bitácora E1.2 — Idempotencia, evidencias y cierre documental](#bitacora-e12)
   - [2.3 E1.2 — Cierre documental con sustentación en lugar de video](#23-e12--cierre-documental-con-sustentación-en-lugar-de-video)
3. [Módulo: `circleguard-promotion-service` (Alejandro Quiñones Caicedo)](#3-módulo-circleguard-promotion-service-alejandro-quiñones-caicedo)
   - [3.1 HU-PROMO-01 (DCG-304) — Diagnosticar la estructura y dependencias de `promotion-service`](#31-hu-promo-01-dcg-304--diagnosticar-la-estructura-y-dependencias-de-promotion-service)

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

---

### 1.3 HU-AUTH-03 (DCG-79) — Empaquetar `auth-service` e integrarlo al pipeline de CI

- **Fecha:** 6 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-79` (`DCG-94`, `DCG-95`, `DCG-96`, `DCG-97`)
- **Objetivo de la interacción:** Sincronizar la base del pipeline CI/CD del equipo (`origin/feature/HU-GATEWAY-CICD-01`), externalizar credenciales, URLs y secretos de `circleguard-auth-service` mediante variables de entorno (`DCG-95`), construir el `Dockerfile` multi-stage con usuario no-root y `HEALTHCHECK` alimentado por Spring Boot Actuator (`DCG-94`), e integrar `auth-service` a las etapas de compilación, cobertura JaCoCo, SonarQube Quality Gate, publicación en Nexus y construcción/publicación de imagen Docker con etiquetado semántico (`DCG-96` y `DCG-97`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

A partir de la matriz de parametrización levantada en `HU-AUTH-01`, la batería de pruebas de `HU-AUTH-02` y la estructura de pipeline creada en `origin/feature/HU-GATEWAY-CICD-01` (`f053ef6`), definí previamente el diseño de implementación para las 4 subtareas:

1. **Sincronización con `origin/feature/HU-GATEWAY-CICD-01`:** Integrar `Jenkinsfile`, `vars/ci.groovy`, `sonar-project.properties`, `.dockerignore` y `gradle/wrapper/gradle-wrapper.jar` en `feature/HU-AUTH-03`, alineando `QrTokenControllerTest`, `QrTokenServiceTest` y `AuthIntegrationTest` con el ajuste de `qr.expiration: 300` (en segundos) introducido en `f053ef6`.
2. **Externalización de configuración y secretos (`DCG-95`):**
   - En `src/main/resources/application.yml`: parametrizar `SERVER_PORT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_LDAP_URLS`, `SPRING_LDAP_BASE`, `SPRING_LDAP_USERNAME`, `SPRING_LDAP_PASSWORD`, `IDENTITY_SERVICE_URL`, `JWT_SECRET`, `JWT_EXPIRATION`, `QR_SECRET`, `QR_EXPIRATION`, `CORS_ALLOWED_ORIGINS` y `MANAGEMENT_HEALTH_LDAP_ENABLED`, exponiendo `/actuator/health` y `/actuator/info`.
   - En `SecurityConfig.java`: eliminar las cadenas quemadas en `contextSource()` y `corsConfigurationSource()` inyectando propiedades `@Value(...)`, y permitir `/actuator/health` y `/actuator/info` en `securityFilterChain`.
3. **Empaquetamiento inmutable con Docker (`DCG-94`):**
   - Declarar `spring-boot-starter-actuator` en `services/circleguard-auth-service/build.gradle.kts`.
   - Crear `services/circleguard-auth-service/Dockerfile` multi-stage (`eclipse-temurin:21-jdk` $\rightarrow$ `eclipse-temurin:21-jre-alpine`), creando el usuario y grupo del sistema `circleguard` (no-root), exponiendo `8180` y configurando `HEALTHCHECK` contra `http://localhost:8180/actuator/health`.
4. **Integración a Jenkins, SonarQube y Nexus (`DCG-96` y `DCG-97`):**
   - Configurar `maven-publish` hacia el repositorio `nexus` en `services/circleguard-auth-service/build.gradle.kts`.
   - Incluir `services/circleguard-auth-service` en `sonar-project.properties`, `Jenkinsfile` y `vars/ci.groovy` (`buildAndTest`, `publish`, `dockerBuildPush` con tag semver `${env.TAG}`).

#### B. Prompt empleado

```text
Estoy desarrollando la historia de usuario HU-AUTH-03 (DCG-79): Empaquetar `auth-service` e integrarlo al pipeline de CI del proyecto CircleGuard ubicado en:
`/home/faiber/Desktop/IngesoftV/final/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`
Actualmente ya me encuentro en la rama `feature/HU-AUTH-03`.

Tras revisar el diagnóstico de `docs/arquitectura/diagnostico-auth-service.md`, el código actual de `services/circleguard-auth-service/` y la infraestructura de CI/CD que mi compañera configuró en la rama remota `origin/feature/HU-GATEWAY-CICD-01` (commit `f053ef6`), definí el siguiente diseño técnico para completar las 4 subtareas de HU-AUTH-03 (`DCG-94`, `DCG-95`, `DCG-96`, `DCG-97`):

1. Sincronización con la base de CI/CD del equipo (`origin/feature/HU-GATEWAY-CICD-01`):
   - En `origin/feature/HU-GATEWAY-CICD-01` ya están creados `Jenkinsfile`, `vars/ci.groovy`, `sonar-project.properties`, `.dockerignore` y `gradle/wrapper/gradle-wrapper.jar` (con la excepción en `.gitignore`).
   - Debemos traer/integrar esos archivos base del pipeline a `feature/HU-AUTH-03` conservando intactos nuestro `docs/arquitectura/diagnostico-auth-service.md`, la bitácora `IAG_REGISTRO.md` (fusionando cualquier entrada si aplica) y la suite de pruebas de `services/circleguard-auth-service/`. Nota: si se incorpora el ajuste de `QrTokenController.java` y `QrTokenService.java` de `f053ef6` (`qr.expiration: 300` en segundos), actualizar `QrTokenControllerTest` y `QrTokenServiceTest` para que todas las pruebas sigan en verde.

2. Subtarea `DCG-95` — Externalización de configuración y secretos en `auth-service`:
   - En `services/circleguard-auth-service/src/main/resources/application.yml`:
     - Parametrizar `server.port: ${SERVER_PORT:8180}`.
     - Parametrizar PostgreSQL: `url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/circleguard_auth}`, `username: ${SPRING_DATASOURCE_USERNAME:admin}`, `password: ${SPRING_DATASOURCE_PASSWORD:password}` y `ddl-auto: ${SPRING_JPA_HIBERNATE_DDL_AUTO:update}`.
     - Parametrizar OpenLDAP: `urls: ${SPRING_LDAP_URLS:ldap://localhost:389}`, `base: ${SPRING_LDAP_BASE:dc=circleguard,dc=edu}`, `username: ${SPRING_LDAP_USERNAME:cn=admin,dc=circleguard,dc=edu}`, `password: ${SPRING_LDAP_PASSWORD:admin}`.
     - Parametrizar `identity.service.url: ${IDENTITY_SERVICE_URL:http://localhost:8083/api/v1/identities/map}`.
     - Parametrizar secretos y expiraciones: `jwt.secret: ${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}`, `jwt.expiration: ${JWT_EXPIRATION:3600000}`, `qr.secret: ${QR_SECRET:5367566B5970337336763979244226452948404D635166546A576E5A72347537}`, `qr.expiration: ${QR_EXPIRATION:300}`.
     - Parametrizar `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:8081,http://localhost:8080}`.
     - Exponer endpoints de Actuator (`management.endpoints.web.exposure.include: health,info` y `management.health.ldap.enabled: ${MANAGEMENT_HEALTH_LDAP_ENABLED:false}` para que el healthcheck no falle cuando LDAP no esté activo).
   - En `SecurityConfig.java`:
     - Eliminar las cadenas quemadas en `contextSource()` e inyectar `@Value("${spring.ldap.urls:ldap://localhost:389}")`, `@Value("${spring.ldap.base:dc=circleguard,dc=edu}")`, `@Value("${spring.ldap.username:cn=admin,dc=circleguard,dc=edu}")` y `@Value("${spring.ldap.password:admin}")`.
     - Eliminar las URLs quemadas en `corsConfigurationSource()` e inyectar `@Value("${cors.allowed-origins:http://localhost:8081,http://localhost:8080}")` separando por comas.
     - Permitir `/actuator/health` y `/actuator/info` en `securityFilterChain`.

3. Subtarea `DCG-94` — `Dockerfile` multi-stage con usuario no root y `HEALTHCHECK`:
   - Agregar `implementation("org.springframework.boot:spring-boot-starter-actuator")` en `services/circleguard-auth-service/build.gradle.kts`.
   - Crear `services/circleguard-auth-service/Dockerfile` siguiendo el estándar multi-stage del equipo:
     - Etapa 1 (`build`): `eclipse-temurin:21-jdk`, compila `./gradlew :services:circleguard-auth-service:bootJar --no-daemon`.
     - Etapa 2 (runtime): `eclipse-temurin:21-jre-alpine`, crea grupo y usuario del sistema no-root `circleguard`, copia el fat JAR (excluyendo `*-plain.jar`) a `/app/app.jar`, asigna permisos a `circleguard:circleguard`, cambia a `USER circleguard`, expone `8180`, define `HEALTHCHECK` contra `http://localhost:8180/actuator/health` y ejecuta `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`.

4. Subtareas `DCG-96` y `DCG-97` — Integración al pipeline de Jenkins, SonarQube, Nexus y publicación Docker con semver:
   - En `services/circleguard-auth-service/build.gradle.kts`:
     - Agregar el plugin `` `maven-publish` `` y el bloque `publishing` hacia el repositorio `nexus` (`NEXUS_URL`, `NEXUS_USER`, `NEXUS_PASSWORD`) siguiendo exactamente la convención usada en `services/circleguard-gateway-service/build.gradle.kts`, y excluir `**/*Application*` en JaCoCo si aplica.
   - En `sonar-project.properties`:
     - Incluir `services/circleguard-auth-service` en `sonar.sources`, `sonar.tests`, `sonar.java.binaries` y `sonar.coverage.jacoco.xmlReportPaths` junto con `gateway-service` para que SonarQube analice ambos módulos y verifique su cobertura JaCoCo >= 60%.
   - En `Jenkinsfile` / `vars/ci.groovy`:
     - Integrar `services/circleguard-auth-service` (imagen `auth`) en las etapas de `Build & Test`, `Package`, `Publish` (Nexus Maven) y `Docker` (`ci.dockerBuildPush('services/circleguard-auth-service', 'auth', env.TAG)`) garantizando que el Quality Gate de SonarQube sea bloqueante y que la imagen se etiquete con versión semántica (`${env.TAG}`, nunca `latest`).

---

Con base en este diseño previo y el archivo `Proyecto_Final_CircleGuard.pdf` adjunto, apóyame ejecutando los cambios en la rama `feature/HU-AUTH-03`:
1. Integra los archivos de CI/CD de `origin/feature/HU-GATEWAY-CICD-01` sin perder el trabajo de `HU-AUTH-01` y `HU-AUTH-02`.
2. Implementa las 4 subtareas (`DCG-94`, `DCG-95`, `DCG-96`, `DCG-97`) en `services/circleguard-auth-service/`, `sonar-project.properties`, `Jenkinsfile` y `vars/ci.groovy`.
3. Ejecuta con Java 21 `./gradlew :services:circleguard-auth-service:clean :services:circleguard-auth-service:test :services:circleguard-auth-service:jacocoTestReport :services:circleguard-auth-service:jacocoTestCoverageVerification :services:circleguard-auth-service:bootJar` y verifica que `docker build -f services/circleguard-auth-service/Dockerfile -t circleguard/auth:1.0.0-test .` construya correctamente (si el daemon de Docker está activo).
4. Registra esta interacción en una nueva sección `1.3 HU-AUTH-03 (DCG-79)` dentro de `IAG_REGISTRO.md` manteniendo el mismo formato de Nivel 3.
```

#### C. Validación humana y ajustes realizados

- **Normalización de rutas de subproyecto Gradle en `vars/ci.groovy`:** En `vars/ci.groovy` original se invocaba `./gradlew :${serviceDir}:clean test ...` donde `serviceDir` tenía formato `services/circleguard-...` (con `/` en vez de `:`). Se normalizó con `def gradleProject = serviceDir.replace('/', ':')` y se prefijaron explícitamente todas las tareas (`:${gradleProject}:clean :${gradleProject}:test :${gradleProject}:jacocoTestReport :${gradleProject}:jacocoTestCoverageVerification` y `:${gradleProject}:publish`) para evitar ejecutar tareas sobre servicios no configurados del monorepo.
- **Verificación de Actuator `/actuator/health` en `AuthIntegrationTest`:** Se añadió una aserción en `AuthIntegrationTest` que realiza `GET /actuator/health` sin token JWT y comprueba HTTP `200 OK` con `$.status == "UP"`, validando que el endpoint del `HEALTHCHECK` de Docker responda correctamente incluso con OpenLDAP deshabilitado (`management.health.ldap.enabled: false`).

#### D. Reflexión crítica

La integración entre las ramas del equipo (`origin/feature/HU-GATEWAY-CICD-01` y `feature/HU-AUTH-03`) demostró la importancia de haber documentado previamente en `HU-AUTH-01` el doble quemado de configuración en `SecurityConfig.java`: externalizar únicamente `application.yml` no habría bastado sin inyectar `@Value` en `contextSource()` y `corsConfigurationSource()`. Asimismo, la verificación del `HEALTHCHECK` de Actuator en las pruebas de integración asegura que el contenedor no entre en estado `unhealthy` cuando se despliegue en `dev`, `stage` y `prod`.

---

### 1.4 HU-AUTH-04 (DCG-80) — Desplegar `auth-service` en `dev`, `stage` y `prod` y validar flujo E2E de autenticación

- **Fecha:** 8 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-80` (`DCG-98`, `DCG-99`, `DCG-100`, `DCG-101`)
- **Objetivo de la interacción:** Configurar los manifiestos de Docker Compose de `auth-service` y su base de datos `postgres-auth` con healthchecks para los ambientes aislados `dev`, `stage` y `prod` (`DCG-98`), integrar el flujo de despliegue automático a `dev`, promoción a `stage` tras superar las pruebas E2E y compuerta de aprobación manual hacia `prod` (`DCG-99`), automatizar y ejecutar la validación E2E con Playwright cubriendo login RBAC, emisión de QR y validación cruzada con el gateway (`DCG-100`), y demostrar el rollback hacia una versión semántica previa documentando las evidencias (`DCG-101`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Tras configurar mi usuario IAM (`Faiber`) con AWS CLI v2, verificar la conectividad SSH hacia las 4 instancias EC2 del equipo (`cg-tools` en `172.31.20.46` / `77.112.223.174`, `cg-dev` en `172.31.19.75` / `3.140.191.217`, `cg-stage` en `172.31.29.207` / `13.58.139.230` y `cg-prod` en `172.31.20.75` / `3.140.253.184`) y revisar la estructura consolidada en `origin/main` (`Jenkinsfile`, `jenkins/vars/ci.groovy`, `jenkins/vars/ciServices.groovy`, `deploy/compose/{dev,stage,prod}/docker-compose.yml`, `deploy/scripts/` y `tests/e2e/`), definí previamente el siguiente diseño técnico:

1. **Sincronización con `origin/main` y catálogo de servicios (`jenkins/vars/ciServices.groovy`):**
   - Habilitar `publish: true` y `docker: true` en la entrada `auth` (`circleguard-auth-service`) de `jenkins/vars/ciServices.groovy` y generalizar la etapa `stage('Docker')` del `Jenkinsfile` para iterar sobre todos los servicios con `service.docker == true` en lugar de filtrar únicamente `gateway`.
2. **Orquestación multi-ambiente con Docker Compose (`DCG-98`):**
   - Añadir en `deploy/compose/{dev,stage,prod}/docker-compose.yml` el servicio `postgres-auth` (`postgres:15-alpine`) con volumen persistente por ambiente y `healthcheck` mediante `pg_isready -U admin -d circleguard_auth`.
   - Añadir el servicio `auth` (`${NEXUS_REGISTRY}/circleguard-docker/auth:${TAG}`) en el puerto `8180:8180` con `depends_on: postgres-auth (condition: service_healthy)`, inyectando `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_*`, `SPRING_FLYWAY_ENABLED=true`, `MANAGEMENT_HEALTH_LDAP_ENABLED=false`, `IDENTITY_SERVICE_URL`, `JWT_SECRET` y `QR_SECRET`, y `healthcheck` contra `http://localhost:8180/actuator/health`.
3. **Flujo de promoción y scripts de despliegue/rollback (`DCG-99` y `DCG-101`):**
   - Actualizar `deploy/scripts/deploy.sh`, `deploy/scripts/rollback.sh` y `deploy/scripts/smoke-test.sh` para que descarguen, levanten y verifiquen el estado `UP` de `gateway` (`:8087`) y `auth` (`:8180`), y sincronizar automáticamente los manifiestos desde `jenkins/vars/ci.groovy` (`deploy`).
4. **Pruebas E2E con Playwright (`DCG-100`):**
   - Ampliar `tests/e2e/access-validation.spec.ts` para verificar salud de `auth-service` (`:8180/actuator/health`), rechazo `401` ante credenciales inválidas, login exitoso de `staff_guard`, generación de QR firmado (`/api/v1/auth/qr/generate`), consulta de permisos RBAC (`/api/v1/users/permissions/alert:receive_priority`) y validación cruzada del QR en `gateway-service` (`POST :8087/api/v1/gate/validate`).

#### B. Prompt empleado

```text
Estoy desarrollando la historia de usuario HU-AUTH-04 (DCG-80): Desplegar `auth-service` en `dev`, `stage` y `prod` y validar flujo E2E de autenticación del proyecto CircleGuard ubicado en:
`/home/faiber/Desktop/IngesoftV/final/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`
Actualmente ya me encuentro en la rama `feat/HU-AUTH-04`.

Tras verificar la conectividad SSH/AWS hacia nuestras 4 instancias EC2 (`cg-tools` en `172.31.20.46` / `77.112.223.174`, `cg-dev` en `172.31.19.75`, `cg-stage` en `172.31.29.207` y `cg-prod` en `172.31.20.75`) y revisar los archivos consolidados por el equipo en `origin/main` (`Jenkinsfile`, `jenkins/vars/ci.groovy`, `jenkins/vars/ciServices.groovy`, `deploy/compose/{dev,stage,prod}/docker-compose.yml`, `deploy/scripts/deploy.sh`, `deploy/scripts/rollback.sh` y `tests/e2e/`), definí el siguiente diseño técnico previo para completar las 4 subtareas de HU-AUTH-04 (`DCG-98`, `DCG-99`, `DCG-100`, `DCG-101`):

1. Sincronización con `origin/main` y catálogo de servicios (`jenkins/vars/ciServices.groovy`):
   - Integrar los últimos cambios de `origin/main` en `feat/HU-AUTH-04` conservando intactos `docs/arquitectura/diagnostico-auth-service.md`, `IAG_REGISTRO.md` y todo el trabajo de `services/circleguard-auth-service/`.
   - En `jenkins/vars/ciServices.groovy`, actualizar la entrada de `circleguard-auth-service` (`short: 'auth'`) habilitando `publish: true` y `docker: true` (además de `jacoco: true`), y crear `services/circleguard-auth-service/sonar-project.properties` para que el pipeline multibranch construya, analice en SonarQube, publique en Nexus Maven y suba la imagen `${NEXUS_REGISTRY}/circleguard-docker/auth:${TAG}` al registro Docker de Nexus (`172.31.20.46:5000`).

2. Subtarea `DCG-98` — Configurar Docker Compose de `auth-service` para `dev`, `stage` y `prod` con healthchecks:
   - En `deploy/compose/dev/docker-compose.yml`, `deploy/compose/stage/docker-compose.yml` y `deploy/compose/prod/docker-compose.yml` (y sus respectivos `.env.example`):
     - Añadir el contenedor `postgres-auth` (`postgres:15-alpine`) con volumen persistente por ambiente, variables `POSTGRES_DB=circleguard_auth`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y `healthcheck` con `pg_isready -U admin -d circleguard_auth`.
     - Añadir el contenedor `auth` (`${NEXUS_REGISTRY}/circleguard-docker/auth:${TAG}`) en el puerto `8180:8180`, dependiendo de `postgres-auth` (`condition: service_healthy`), inyectando por variables de entorno `SPRING_PROFILES_ACTIVE` (`dev`, `stage`, `prod`), `SERVER_PORT=8180`, `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-auth:5432/circleguard_auth`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, `MANAGEMENT_HEALTH_LDAP_ENABLED=false`, `IDENTITY_SERVICE_URL`, `JWT_SECRET`, `QR_SECRET` y `CORS_ALLOWED_ORIGINS`.
     - Configurar el `healthcheck` de `auth` contra `http://localhost:8180/actuator/health` verificando `"status":"UP"`.

3. Subtarea `DCG-99` — Integrar despliegue automático a `dev`, promoción a `stage` tras E2E y aprobación manual a `prod`:
   - Actualizar `deploy/scripts/deploy.sh`, `deploy/scripts/rollback.sh` y `deploy/scripts/smoke-test.sh` para que desplieguen y verifiquen tanto `gateway` (`:8087/actuator/health`) como `auth` (`:8180/actuator/health`) en cada ambiente aislado (`cg-dev`, `cg-stage`, `cg-prod`), sincronizando los archivos compose actualizados hacia `/opt/circleguard/deploy/compose/{dev,stage,prod}` en las instancias EC2.
   - Asegurar que el `Jenkinsfile` y `jenkins/vars/ci.groovy` ejecuten el flujo continuo: `Deploy to Dev` -> `E2E Tests (Playwright)` -> `Promote & Deploy to Stage` -> `Smoke Test Stage` -> `Manual Approval for Prod` (`input` manual registrado) -> `Deploy to Prod` + `Smoke Test Prod`.

4. Subtarea `DCG-100` — Validar flujo E2E de inicio de sesión y generación de QR con Playwright:
   - En `tests/e2e/access-validation.spec.ts` (y configuración de `playwright.config.ts`), ampliar las pruebas E2E para validar contra el entorno desplegado (`AUTH_URL` en `:8180` y `GATEWAY_URL` en `:8087`):
     - Healthcheck de `auth-service` (`GET /actuator/health` == `UP`).
     - Flujo de autenticación y emisión de JWT (`POST /api/v1/auth/login` o `/api/v1/auth/visitor/handoff` con rechazo `401` ante credenciales inválidas y emisión de token válido `200`).
     - Generación de token QR firmado (`GET /api/v1/auth/qr/generate` con `Authorization: Bearer <jwt>`) y validación cruzada de ese QR en el gateway (`POST /api/v1/gate/validate`), verificando además la consulta de permisos RBAC (`GET /api/v1/users/permissions/alert:receive_priority`).

5. Subtarea `DCG-101` — Demostrar rollback de `auth-service` hacia una versión semántica anterior y documentar evidencias:
   - Publicar dos versiones semánticas en el registro de Nexus (por ejemplo `1.0.0-<sha1>` y `1.0.1-<sha2>`), desplegar en los ambientes EC2 y ejecutar `deploy/scripts/rollback.sh` hacia el tag anterior verificando el restablecimiento del servicio con `/actuator/health` en `UP`.
   - Documentar las evidencias verificables del despliegue en `dev`, `stage` y `prod`, las pruebas E2E con Playwright y el procedimiento/ejecución de rollback en `docs/arquitectura/despliegue-auth-service.md` (o actualizando `docs/infra/runbook.md`), y registrar esta interacción en la sección `1.4 HU-AUTH-04 (DCG-80)` de `IAG_REGISTRO.md` manteniendo el estándar de Nivel 3 de la Universidad Icesi.
```

#### C. Validación humana y ajustes realizados

- **Hallazgo crítico de integración RBAC entre `auth-service` y `gateway-service` (`V6__map_gate_staff_permission.sql`):** Al contrastar `services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/config/SecurityConfig.java` con las migraciones Flyway de `auth-service`, se detectó que `POST /api/v1/gate/validate` exige `.hasAuthority("gate:scan")`, pero `V1__initial_auth_schema.sql` solo había asociado permisos al rol `HEALTH_CENTER` y había omitido asociar `gate:scan` al rol `GATE_STAFF` (`staff_guard`). Se creó la migración `V6__map_gate_staff_permission.sql` para vincular `GATE_STAFF` $\rightarrow$ `gate:scan`, permitiendo que el JWT emitido para `staff_guard` autorice directamente la validación de QR en `gateway-service`.
- **Resiliencia controlada en `IdentityClient.java` (`identity.service.fallback-enabled`):** Dado que `identity-service` se despliega en una historia independiente, se añadió la propiedad opcional `identity.service.fallback-enabled` (por defecto `false` para no alterar las pruebas unitarias de fallo, y `true` en Compose mientras `identity-service` no esté activo) que genera un UUID determinístico (`UUID.nameUUIDFromBytes`) cuando `identity-service` no es alcanzable, junto con dos nuevos tests unitarios en `IdentityClientTest.java` manteniendo el `100%` de cobertura en `IdentityClient`.
- **Verificación real sobre AWS EC2 (`cg-tools`, `cg-dev`, `cg-stage`, `cg-prod`):**
  - Se publicaron en el registro Docker de Nexus (`172.31.20.46:5000/circleguard-docker/auth`) las versiones semánticas `1.0.0-ab8c930` (digest `sha256:9833b5734f9af98505959b7e48c62684ccb5ac4e7c713775117122809a79d15f`) y `1.0.1-064ba7b` (digest `sha256:3090800eee615b5dfda3802e870a5556797c30679501e5d9ccdac4a88c3bdc9e`).
  - Se desplegó `1.0.1-064ba7b` en `cg-dev` (`3.140.191.217`), `cg-stage` (`13.58.139.230`) y `cg-prod` (`3.140.253.184`), verificando `Health OK (gateway)` (`:8087`) y `Health OK (auth)` (`:8180`).
  - Se ejecutó la suite E2E de Playwright (`6 tests`) contra los tres ambientes, obteniendo **`6 passed` en `dev` (`8.6s`), `6 passed` en `stage` (`8.6s`) y `6 passed` en `prod` (`8.7s`)**.
  - Se ejecutó `./deploy/scripts/rollback.sh prod 1.0.0-ab8c930` en `cg-prod` y `./deploy/scripts/smoke-test.sh`, comprobando la reversión inmediata al tag `1.0.0-ab8c930` con estado `UP` en ambos servicios.

#### D. Reflexión crítica

La validación E2E real sobre las instancias EC2 demostró por qué las pruebas unitarias aisladas por microservicio no sustituyen a las pruebas de sistema en un entorno tipo producción: aunque `auth-service` y `gateway-service` pasaban sus suites unitarias por separado, el flujo cruzado `POST /api/v1/auth/login` $\rightarrow$ `GET /api/v1/auth/qr/generate` $\rightarrow$ `POST /api/v1/gate/validate` habría fallado con `403 Forbidden` sin la migración `V6__map_gate_staff_permission.sql` que asigna el permiso `gate:scan` al rol `GATE_STAFF`, y habría requerido sincronizar explícitamente `JWT_SECRET` y `QR_SECRET` en el `.env` de cada ambiente.

---

### 1.5 HU-ID-04 (DCG-85) — Desplegar `identity-service` en `dev`, `stage` y `prod` y validar anonimización E2E

- **Fecha:** 9 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-85` (`DCG-118`, `DCG-119`, `DCG-120`, `DCG-121`)
- **Objetivo de la interacción:** Configurar los manifiestos Docker Compose de `circleguard-identity-service` y su base de datos `postgres-identity` (`circleguard_identity`) con healthchecks y variables criptográficas externalizadas (`VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT`) para `dev`, `stage` y `prod` (`DCG-118`), integrar el flujo de despliegue automático a `dev`, promoción a `stage` y aprobación manual a `prod` (`DCG-119`), validar de extremo a extremo con Playwright la anonimización de identidad (`auth-service` $\rightarrow$ `identity-service` `POST /api/v1/identities/map` y `/api/v1/identities/visitor`) comprobando ausencia total de PII en los JWT emitidos (`DCG-120`), y demostrar el rollback hacia una versión semántica previa documentando las evidencias (`DCG-121`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Tras revisar el trabajo previo en `origin/feat/HU-ID-03-empaquetamiento-inmutable` (`services/circleguard-identity-service/Dockerfile`, `build.gradle.kts`, `application.yml`, `IdentityVaultController.java`, `IdentityVaultService.java` e `IdentityEncryptionConverter.java`) y contrastarlo con la arquitectura ya desplegada de `auth-service` (`HU-AUTH-04`), elaboré previamente el siguiente diseño técnico:

1. **Análisis de dependencias y criptografía de la bóveda (`DCG-118`):**
   - `circleguard-identity-service` expone el puerto `8083`, utiliza PostgreSQL (`circleguard_identity`) con la migración Flyway `V1__initial_identity_schema.sql` (tabla `identity_mappings` con `anonymous_id UUID`, `real_identity_encrypted BYTEA`, `identity_hash VARCHAR(64)` e índices únicos) y cifra `realIdentity` mediante `Encryptors.text(secret, salt)` en `IdentityEncryptionConverter.java`.
   - `Encryptors.text(secret, salt)` de Spring Security Crypto exige que `VAULT_SALT` sea una cadena hexadecimal válida de longitud par (por ejemplo `5c0744940b5c369b`), mientras que `VAULT_HASH_SALT` se concatena antes de calcular el índice ciego `SHA-256` en `IdentityVaultService.computeHash()`.
   - El `Dockerfile` de `identity-service` define su `HEALTHCHECK` contra `http://127.0.0.1:8083/actuator/health/liveness`; además, al inyectar `MANAGEMENT_HEALTH_KAFKA_ENABLED=false` en Compose, el servicio arranca en estado `UP (healthy)` sin depender de un broker Kafka activo para las operaciones síncronas `/api/v1/identities/map` y `/api/v1/identities/visitor`.
2. **Acoplamiento estricto `auth-service` $\rightarrow$ `identity-service` (`DCG-118` y `DCG-120`):**
   - En `deploy/compose/{dev,stage,prod}/docker-compose.yml`, el contenedor `auth` pasa a depender explícitamente de `identity` (`condition: service_healthy`) y cambia `IDENTITY_SERVICE_FALLBACK_ENABLED` a `false`, garantizando que todo login en `auth-service` invoque obligatoriamente `http://identity:8083/api/v1/identities/map` y reciba el UUID persistido en la bóveda `postgres-identity`.
3. **Automatización de despliegue, smoke tests y pruebas E2E (`DCG-119`, `DCG-120`, `DCG-121`):**
   - Actualizar `deploy/scripts/deploy.sh`, `deploy/scripts/rollback.sh` y `deploy/scripts/smoke-test.sh` para orquestar y verificar `gateway` (`:8087`), `identity` (`:8083/actuator/health/liveness`) y `auth` (`:8180/actuator/health`).
   - Ampliar `tests/e2e/access-validation.spec.ts` con 3 escenarios dedicados a `identity-service`: salud del servicio, idempotencia de `/api/v1/identities/map` + anonimización de `/api/v1/identities/visitor` + bloqueo `401` en `/api/v1/identities/lookup/{id}`, y verificación cruzada de que el JWT emitido por `auth-service` para `health_user` contenga exactamente `sub = vaultAnonymousId` sin filtrar `health_user` ni `health@univ.edu`.

#### B. Prompt empleado

```text
Implementa `HU-ID-04` (`DCG-85`, subtareas `DCG-118`, `DCG-119`, `DCG-120` y `DCG-121`) en una nueva rama `feat/HU-ID-04-despliegue-validacion-e2e` integrando `origin/feat/HU-ID-03-empaquetamiento-inmutable` y `feat/HU-AUTH-04-despliegue-validacion-e2e`:
1. Configura `postgres-identity`, `kafka` e `identity` con healthchecks y variables externalizadas en `deploy/compose/{dev,stage,prod}/docker-compose.yml` y `.env.example` (`DCG-118`).
2. Actualiza `deploy.sh`, `rollback.sh`, `smoke-test.sh` y el `Jenkinsfile` para desplegar `identity-service` en `cg-dev`, `cg-stage` y `cg-prod` desde el registro Docker de Nexus (`DCG-119`).
3. Verifica con Playwright E2E (`tests/e2e/access-validation.spec.ts`) la anonimización real (`auth-service` -> `identity-service` `POST /api/v1/identities/map`) asegurando que el `sub`/`pseudoId` del JWT no exponga PII (`DCG-120`).
4. Demuestra el rollback en `cg-prod` entre dos tags semánticos, documenta la evidencia en `docs/arquitectura/despliegue-identity-service.md` y registra la iteración en `IAG_REGISTRO.md` (`DCG-121`).
```

#### C. Validación humana y ajustes realizados

- **Registro del proyecto en SonarQube y Quality Gate (`configure-sonar-identity.sh`):** Se ejecutó `infra/bootstrap/configure-sonar-identity.sh` contra `http://77.112.223.174:9000` para aprovisionar el proyecto `circleguard-identity-service` y asociarle el Quality Gate bloqueante `CircleGuard Services` (`line_coverage < 60%`).
- **Configuración de secretos hexadecimales para `Spring Security Crypto` (`VAULT_SALT`):** Se validó que `IdentityEncryptionConverter` instancia `Encryptors.text(secret, salt)`, el cual decodifica `salt` con `Hex.decode()`. Por ello se parametrizó `VAULT_SALT=5c0744940b5c369b` (hexadecimal de 16 caracteres) tanto en `docker-compose.yml` como en `.env` de `cg-dev`, `cg-stage` y `cg-prod`, previniendo fallos `IllegalArgumentException: Non-hex character` al cifrar identidades en PostgreSQL.
- **Desactivación del fallback local en `auth-service` (`IDENTITY_SERVICE_FALLBACK_ENABLED=false`):** Con `cg-identity-{dev,stage,prod}` desplegado y saludable en el puerto `8083`, se desactivó el fallback local de `IdentityClient` en los tres ambientes y se añadió la condición `depends_on: identity: condition: service_healthy` en `auth`.
- **Verificación real sobre AWS EC2 (`cg-tools`, `cg-dev`, `cg-stage`, `cg-prod`):**
  - Se construyeron y publicaron en Nexus (`172.31.20.46:5000/circleguard-docker/identity`) los tags semánticos `1.0.0-ab8c930` y `1.0.1-064ba7b`.
  - Se desplegaron los tres servicios (`gateway`, `identity`, `auth`) junto con sus bases de datos (`redis`, `postgres-identity`, `postgres-auth`) en `cg-dev` (`3.140.191.217`), `cg-stage` (`13.58.139.230`) y `cg-prod` (`3.140.253.184`).
  - Se ejecutó la suite E2E ampliada de Playwright (`9 tests`) contra los tres ambientes, obteniendo **`9 passed` en `dev`, `9 passed` en `stage` y `9 passed` en `prod`**.
  - Se ejecutó `./deploy/scripts/rollback.sh prod 1.0.0-ab8c930` y `./deploy/scripts/smoke-test.sh` en `cg-prod`, comprobando el rollback limpio de `gateway`, `identity` y `auth` hacia `1.0.0-ab8c930` y su posterior restablecimiento a `1.0.1-064ba7b`.

#### D. Reflexión crítica

Integrar `circleguard-identity-service` en el despliegue multi-ambiente permitió cerrar el ciclo arquitectónico de privacidad por diseño (*Privacy by Design*) de CircleGuard: mientras que en `HU-AUTH-04` fue necesario introducir un fallback determinístico temporal en `IdentityClient` porque la bóveda aún no estaba desplegada, en `HU-ID-04` pudimos desactivar dicho fallback (`IDENTITY_SERVICE_FALLBACK_ENABLED=false`) y demostrar con pruebas E2E automatizadas que el identificador `sub` del JWT emitido por `auth-service` proviene directamente del registro cifrado (`AES` + índice ciego `SHA-256`) almacenado por `identity-service` en `postgres-identity`, garantizando que ningún dato personal identificable (`username` ni `email`) salga hacia los tokens ni hacia el gateway.

---

## 2. Módulo: `circleguard-gateway-service` e Infraestructura CI/CD (Daniela Castaño)

### 2.1 HU-GATEWAY-CICD-01 — Plataforma CI/CD del gateway

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
### 2.2 Bitácora E1.2 — Idempotencia, evidencias y cierre documental (8 de noviembre de 2026)

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

### 2.3 E1.2 — Cierre documental con sustentación en lugar de video (11 de noviembre de 2026)

- **Nivel de uso:** 3 (colaboración con IAG).
- **Herramienta:** OpenCode / DeepSeek.
- **Entregable:** E1.2 — Informe Final de la Parte 1 (`docs/informe/E1.2-Informe-Final.pdf`) y `IAG_REGISTRO.md`.
- **Rama del PR:** `docs/HU-INFRA-02-sustentacion-p1.8` (PR-45).

### A. Contenido propio entregado a la IAG

1. **Decisión de equipo:** el video de demostración no aplica para la Parte 1; la demostración se realiza en la sustentación oral ante el docente.
2. **Estado real verificado:** las cinco capturas de Jira ya estaban embebidas en la sección 11.2.1 del informe, aunque el texto todavía las declaraba pendientes.
3. **Estado de releases:** `v1.2.5` corresponde a las imágenes desplegadas; `v1.2.6` y `v1.2.7` son releases documentales.
4. **Restricciones:** no inventar fecha de sustentación, no introducir enlaces de video y no reescribir el archivo histórico `Proyecto_Final_CircleGuard.tex`.

### B. Prompt empleado

```text
El video no aplica, usamos en cambio sustentacion, acomoda documentacion y la bitacora de IAG
```

### C. Validación humana y hallazgos

- Se cambió el estado de P1.8 de **Parcial** a **Cumplido**, conservando el nombre oficial del criterio para no alterar la rúbrica.
- Se eliminaron las afirmaciones de video pendiente y de capturas de Jira pendientes en la matriz, la sección 11.3, la sección 12 y la conclusión.
- La sección 12 pasó de "Qué falta para el cierre documental" a "Actividades de cierre", separando la entrega y la sustentación de la infraestructura.
- Se aclaró que `v1.2.6` y `v1.2.7` son documentales y que las imágenes desplegadas en los tres ambientes siguen siendo `1.2.5`.
- `docs/agil/sprint-1/review.md` pasó de "Video de demostración" a "Sustentación oral".
- Se regeneraron HTML y PDF y se verificaron páginas, tablas, imágenes y la ausencia de secretos e IP personal.

### D. Reflexión crítica

Sustituir el video por la sustentación deja la evidencia de P1.8 en un evento oral que no queda dentro del PDF. El riesgo se mitiga declarando la modalidad de forma explícita, conservando el nombre oficial del criterio y asegurando que el PDF contenga toda la evidencia verificable: URLs, capturas del flujo, capturas de Jira, cobertura y rollback. La lección es que un cambio de modalidad no debe resolverse con un simple cambio de estado: hay que dejar escrito qué se entrega, qué se demuestra en vivo y qué ya está probado.
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

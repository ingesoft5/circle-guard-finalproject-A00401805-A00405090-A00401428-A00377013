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
   - [1.6 HU-AUTH-05 (DCG-81 — Subtareas DCG-102 y DCG-103) — Orquestación en Kubernetes, sondas Actuator y observabilidad con Prometheus](#16-hu-auth-05-dcg-81--subtareas-dcg-102-y-dcg-103--orquestación-en-kubernetes-sondas-actuator-y-observabilidad-con-prometheus)
   - [1.7 HU-AUTH-05 (DCG-81 — Subtareas DCG-104 y DCG-105) — Resiliencia con Resilience4j, logs estructurados y alertas Prometheus](#17-hu-auth-05-dcg-81--subtareas-dcg-104-y-dcg-105--resiliencia-con-resilience4j-logs-estructurados-y-alertas-prometheus)
   - [1.8 HU-ID-05 (DCG-86 — Subtareas DCG-119 y DCG-120) — Orquestación en Kubernetes, HPA, sondas Actuator y observabilidad Prometheus en `identity-service`](#18-hu-id-05-dcg-86--subtareas-dcg-119-y-dcg-120--orquestación-en-kubernetes-hpa-sondas-actuator-y-observabilidad-prometheus-en-identity-service)
   - [1.9 HU-ID-05 (DCG-86 — Subtareas DCG-121 y DCG-122) — Resiliencia Resilience4j en auditoría Kafka, logs estructurados sin PII y alertas Prometheus](#19-hu-id-05-dcg-86--subtareas-dcg-121-y-dcg-122--resiliencia-resilience4j-en-auditoría-kafka-logs-estructurados-sin-pii-y-alertas-prometheus)
   - [1.10 HU-DOCS-07 — Actualización y ampliación del informe final de Parte 2 con evidencias actuales](#110-hu-docs-07--actualización-y-ampliación-del-informe-final-de-parte-2-con-evidencias-actuales)
2. [Módulo: `circleguard-gateway-service`, `circleguard-file-service` e Infraestructura CI/CD (Daniela Castaño)](#modulo-daniela)
   - [2.1 HU-GATEWAY-CICD-01 — Plataforma CI/CD del gateway](#iag-daniela-21)
   - [2.2 GW-CI-02 — Integración Gateway–Redis con Testcontainers](#iag-daniela-22)
   - [2.3 FS-CI-01 — Pruebas unitarias del file-service](#iag-daniela-23)
   - [2.4 HU-FILE-02 — Imagen inmutable y dependencia de MinIO](#iag-daniela-24)
   - [2.5 HU-FILE-05 — Orquestación, observabilidad y seguridad del file-service](#iag-daniela-25)
   - [2.6 HU-FILE-04 — Integración File–MinIO con Testcontainers](#iag-daniela-26)
   - [2.7 Cierre de gates de cobertura y calidad (#158)](#iag-daniela-27)
   - [2.8 Bitácora E1.2 — Idempotencia, evidencias y cierre documental](#iag-daniela-28)
   - [2.9 Lecciones transversales y límites de verificación](#iag-daniela-29)
3. [Módulo: `circleguard-form-service` (Santiago Estrada)](#3-módulo-circleguard-form-service-santiago-estrada)
   - [3.1 HU-FORM-01 — Documentar el funcionamiento y las dependencias de `form-service`](#31-hu-form-01--documentar-el-funcionamiento-y-las-dependencias-de-form-service)
   - [3.2 HU-FORM-02 — Automatizar las pruebas y la cobertura de `form-service`](#32-hu-form-02--automatizar-las-pruebas-y-la-cobertura-de-form-service)
   - [3.3 HU-FORM-03 — Empaquetar `form-service` e integrarlo al pipeline](#33-hu-form-03--empaquetar-form-service-e-integrarlo-al-pipeline)
4. [Módulo: `circleguard-promotion-service` (Alejandro Quiñones Caicedo)](#4-módulo-circleguard-promotion-service-alejandro-quiñones-caicedo)
   - [4.1 HU-PROMO-01 (DCG-304) — Diagnosticar la estructura y dependencias de `promotion-service`](#41-hu-promo-01-dcg-304--diagnosticar-la-estructura-y-dependencias-de-promotion-service)
5. [Módulo: `circleguard-dashboard-service` e integración CI/CD (HU-DASH-03)](#5-módulo-circleguard-dashboard-service-e-integración-cicd-hu-dash-03)
6. [Módulo: `circleguard-dashboard-service` — observabilidad y seguridad (HU-DASH-05)](#6-módulo-circleguard-dashboard-service--observabilidad-y-seguridad-hu-dash-05)
7. [Entrada: Ejecución de HU-DASH-04 (despliegue y rollback on-premise de dashboard-service)](#7-entrada-ejecución-de-hu-dash-04-despliegue-y-rollback-on-premise-de-dashboard-service)

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

El diseño previo caso por caso a partir del diagnóstico de `HU-AUTH-01` permitió orientar a la IAG con precisión sobre qué escenarios de borde probar (como el fallback de LDAP a PostgreSQL cuando `LdapAuthenticationProvider` lanza `AuthenticationException`, la desactivación de cuentas con `isActive = false`, y la limpieza del `SecurityContextHolder` ante tokens malformados). Además, el ajuste de constructores en `IdentityClient` con `@Value` y `RestTemplate` no solo permitió las pruebas unitarias con `MockRestServiceServer`, sino que dejó listo el cliente para su contenerización en `HU-AUTH-03`.

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
     - Integrar `services/circleguard-auth-service` (imagen `auth`) en las etapas de `Build & Test`, `Package`, `Publish` (Nexus Maven) y `Docker` (`ci.dockerBuildPush('services/circleguard-auth-service', 'auth', env.TAG)`) garantizando que el Quality Gate de SonarQube sea obligatorio y que la imagen se etiquete con versión semántica (`${env.TAG}`, nunca `latest`).

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
   - Ampliar `tests/e2e/access-validation.spec.ts` con 3 escenarios dedicados a `identity-service`: salud del servicio, idempotencia de `/api/v1/identities/map` + anonimización de `/api/v1/identities/visitor` + respuesta `401` en `/api/v1/identities/lookup/{id}`, y verificación cruzada de que el JWT emitido por `auth-service` para `health_user` contenga exactamente `sub = vaultAnonymousId` sin filtrar `health_user` ni `health@univ.edu`.

#### B. Prompt empleado

```text
Implementa `HU-ID-04` (`DCG-85`, subtareas `DCG-118`, `DCG-119`, `DCG-120` y `DCG-121`) en una nueva rama `feat/HU-ID-04-despliegue-validacion-e2e` integrando `origin/feat/HU-ID-03-empaquetamiento-inmutable` y `feat/HU-AUTH-04-despliegue-validacion-e2e`:
1. Configura `postgres-identity`, `kafka` e `identity` con healthchecks y variables externalizadas en `deploy/compose/{dev,stage,prod}/docker-compose.yml` y `.env.example` (`DCG-118`).
2. Actualiza `deploy.sh`, `rollback.sh`, `smoke-test.sh` y el `Jenkinsfile` para desplegar `identity-service` en `cg-dev`, `cg-stage` y `cg-prod` desde el registro Docker de Nexus (`DCG-119`).
3. Verifica con Playwright E2E (`tests/e2e/access-validation.spec.ts`) la anonimización real (`auth-service` -> `identity-service` `POST /api/v1/identities/map`) asegurando que el `sub`/`pseudoId` del JWT no exponga PII (`DCG-120`).
4. Demuestra el rollback en `cg-prod` entre dos tags semánticos, documenta la evidencia en `docs/arquitectura/despliegue-identity-service.md` y registra la iteración en `IAG_REGISTRO.md` (`DCG-121`).
```

#### C. Validación humana y ajustes realizados

- **Registro del proyecto en SonarQube y Quality Gate (`configure-sonar-identity.sh`):** Se ejecutó `infra/bootstrap/configure-sonar-identity.sh` contra `http://77.112.223.174:9000` para aprovisionar el proyecto `circleguard-identity-service` y asociarle el Quality Gate obligatorio `CircleGuard Services` (`line_coverage < 60%`).
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

### 1.6 HU-AUTH-05 (DCG-81 — Subtareas DCG-102 y DCG-103) — Orquestación en Kubernetes, sondas Actuator y observabilidad con Prometheus

- **Fecha:** 18 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-81` (`DCG-102`, `DCG-103`)
- **Objetivo de la interacción:** Habilitar métricas en formato Prometheus mediante Micrometer y configurar sondas de ciclo de vida nativas de Actuator (`liveness` y `readiness`) en `circleguard-auth-service` (`DCG-103`), y actualizar los manifiestos de Kubernetes (`Deployment`, `HorizontalPodAutoscaler`, `ConfigMaps` y `Secrets`) en `deploy/k8s/base/` con verificación de sintaxis y renderizado multi-ambiente (`DCG-102`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Tras revisar el avance a la Parte 2 en el monorepo y contrastar la arquitectura de `auth-service` con los manifiestos base de Kubernetes en `deploy/k8s/`, definí como insumo previo:

1. **Requerimientos de Observabilidad y Sondas en `auth-service` (`DCG-103`):**
   - Incorporar la librería `io.micrometer:micrometer-registry-prometheus` en `build.gradle.kts`.
   - En `application.yml`, habilitar las sondas nativas de Spring Boot para Kubernetes (`management.endpoint.health.probes.enabled: true`), exponer `prometheus` junto con `health` e `info` en `management.endpoints.web.exposure.include`, e independizar el healthcheck de LDAP (`management.health.ldap.enabled: false`) para evitar falsos negativos en el estado del pod si el directorio tiene latencia.
   - En `SecurityConfig.java`, asegurar que las rutas `/actuator/health/**`, `/actuator/info` y `/actuator/prometheus` queden abiertas sin exigir Bearer JWT.
2. **Requerimientos de Orquestación en Kubernetes (`DCG-102`):**
   - En `deploy/k8s/base/apps.yml`, migrar las sondas genéricas del `Deployment` de `auth` hacia los endpoints especializados: `readinessProbe` apuntando a `/actuator/health/readiness` (`initialDelaySeconds: 30`, `periodSeconds: 10`, `failureThreshold: 3`) y `livenessProbe` apuntando a `/actuator/health/liveness` (`initialDelaySeconds: 60`, `periodSeconds: 15`, `failureThreshold: 3`).
   - Verificar la inyección de credenciales (`SPRING_DATASOURCE_PASSWORD`, `SPRING_LDAP_PASSWORD`, `JWT_SECRET`, `QR_SECRET`) desde el Secret `cg-secrets`.
   - En `deploy/k8s/base/hpa.yml`, configurar el `HorizontalPodAutoscaler` para `auth` con autoescalado elástico entre 1 y 5 réplicas y objetivo de utilización media de CPU en `75%`.

#### B. Prompt empleado

```text
Estoy desarrollando la primera etapa de la historia HU-AUTH-05 (DCG-81) para el proyecto CircleGuard:
`/home/faiber/Desktop/IngesoftV/final/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`
Ya me encuentro en la rama de trabajo `feat/HU-AUTH-05-k8s-resiliencia-observabilidad`.

Para este avance me enfocaré únicamente en completar las dos primeras subtareas: DCG-103 (Observabilidad, Prometheus y sondas Actuator) y DCG-102 (Manifiestos K8s, ConfigMaps, Secrets, HPA y sondas especializadas). A partir de la revisión del código en `services/circleguard-auth-service/` y de la base K8s en `deploy/k8s/`, definí los siguientes requisitos técnicos:

1. Subtarea DCG-103 — Métricas Prometheus y sondas Actuator en `auth-service`:
   - En `services/circleguard-auth-service/build.gradle.kts`, agregar la dependencia de métricas:
     `implementation("io.micrometer:micrometer-registry-prometheus")`.
   - En `src/main/resources/application.yml`:
     - Habilitar las sondas de Kubernetes nativas de Spring Boot: `management.endpoint.health.probes.enabled: true`.
     - Exponer `prometheus` en los endpoints web de Actuator: `management.endpoints.web.exposure.include: health,info,prometheus`.
     - Mantener deshabilitado el healthcheck de LDAP (`management.health.ldap.enabled: ${MANAGEMENT_HEALTH_LDAP_ENABLED:false}`) para que el estado general de salud no caiga en down si LDAP tiene latencia o no está disponible.
   - En `SecurityConfig.java`:
     - Asegurar que `/actuator/prometheus` y `/actuator/health/**` estén explícitamente permitidos en `authorizeHttpRequests` sin requerir token JWT ni autenticación previa.
   - En las pruebas (`src/test/`):
     - Validar que las pruebas sigan pasando y que `/actuator/health/liveness`, `/actuator/health/readiness` y `/actuator/prometheus` respondan correctamente.

2. Subtarea DCG-102 — Manifiestos K8s, HPA y configuración por ambiente:
   - En `deploy/k8s/base/apps.yml`, actualizar la definición del Deployment de `auth`:
     - Cambiar las sondas genéricas por las sondas especializadas de Kubernetes:
       - `readinessProbe`: `httpGet` a `path: /actuator/health/readiness`, port `8180`, `initialDelaySeconds: 30`, `periodSeconds: 10`, `failureThreshold: 3`.
       - `livenessProbe`: `httpGet` a `path: /actuator/health/liveness`, port `8180`, `initialDelaySeconds: 60`, `periodSeconds: 15`, `failureThreshold: 3`.
     - Verificar que las variables críticas y secretos (`SPRING_DATASOURCE_PASSWORD`, `SPRING_LDAP_PASSWORD`, `JWT_SECRET`, `QR_SECRET`) sigan inyectándose desde `cg-secrets` (`secretKeyRef`).
     - Asegurar que los límites de recursos estén configurados (`requests`: `cpu: 100m`, `memory: 320Mi`; `limits`: `memory: 768Mi`).
   - En `deploy/k8s/base/hpa.yml`:
     - Verificar y ajustar el `HorizontalPodAutoscaler` para `auth` con escala entre `minReplicas: 1` y `maxReplicas: 5`, con umbral de utilización promedio de CPU en `75%`.
   - Verificar la sintaxis y renderizado de los manifiestos ejecutando localmente:
     `kubectl kustomize deploy/k8s/overlays/dev`
     `kubectl kustomize deploy/k8s/overlays/stage`
     `kubectl kustomize deploy/k8s/overlays/prod`

---

Con base en este diseño previo:
1. Aplica las configuraciones de DCG-103 en `services/circleguard-auth-service/` (`build.gradle.kts`, `application.yml`, `SecurityConfig.java`).
2. Actualiza los manifiestos de DCG-102 en `deploy/k8s/base/apps.yml` y `deploy/k8s/base/hpa.yml`, y valida el renderizado con `kubectl kustomize`.
3. Ejecuta con Java 21 `./gradlew :services:circleguard-auth-service:test :services:circleguard-auth-service:jacocoTestReport :services:circleguard-auth-service:jacocoTestCoverageVerification` para garantizar que la compilación y pruebas sigan limpias con cobertura >= 60%.
4. Registra los cambios y el resultado obtenido en `IAG_REGISTRO.md` bajo la sección `1.5 HU-AUTH-05 (DCG-81 — Subtareas DCG-102 y DCG-103)`.
```

#### C. Validación humana y hallazgos adicionales

- **Detección de condición de auto-configuración en Spring Boot 3:** Al ejecutar la primera prueba de integración, se detectó que el endpoint `/actuator/prometheus` retornaba `404 Not Found` a pesar de estar incluido en `management.endpoints.web.exposure.include`. Inspeccionando el bytecode de `PrometheusMetricsExportAutoConfiguration` se constató que la anotación `@ConditionalOnEnabledMetricsExport("prometheus")` requiere la propiedad explícita `management.prometheus.metrics.export.enabled: true`. Al incorporarla en `application.yml`, el endpoint quedó registrado y respondiendo métricas en formato texto plano de Prometheus.
- **Validación de sondas de ciclo de vida:** Se verificó que `/actuator/health/liveness` y `/actuator/health/readiness` respondieron `200 OK` con `{"status":"UP"}` en la suite de pruebas automatizadas `AuthIntegrationTest`.
- **Métricas reales de JaCoCo (`jacocoTestReport.xml`):**
  - **Cobertura de líneas (`LINE`):** **`98.81%`** (`166 / 168` líneas cubiertas, superando ampliamente el umbral del `60%`).
  - **Cobertura de instrucciones (`INSTRUCTION`):** **`98.42%`** (`686 / 697` instrucciones).
  - **Cobertura de clases (`CLASS`):** **`100.00%`** (`10 / 10` clases).
  - **Cobertura de métodos (`METHOD`):** **`97.14%`** (`34 / 35` métodos).
- **Validación de manifiestos K8s:** Se validó la sintaxis YAML de `deploy/k8s/base/apps.yml` (18 documentos) y `deploy/k8s/base/hpa.yml` (3 documentos), comprobando que la definición del Deployment `auth` y su HPA con escala de 1 a 5 réplicas cumplen con el esquema de Kubernetes v1.28+.

#### D. Reflexión crítica

Separar las sondas de salud en `/actuator/health/liveness` y `/actuator/health/readiness` en lugar de apuntar ambas al endpoint genérico `/actuator/health` es una buena práctica crítica para Kubernetes: si un middleware dependiente (como PostgreSQL o LDAP) sufre lentitud transitoria, la sonda de `readiness` simplemente retira temporalmente al pod del balanceador de carga del Service sin provocar que el kubelet mate el contenedor mediante un `CrashLoopBackOff` destructivo. Asimismo, exponer métricas nativas con Micrometer Prometheus prepara al microservicio para el scraping declarativo de Prometheus Operator en el clúster on-premise sin requerir agentes externos ni reinicios del contenedor.

---

### 1.7 HU-AUTH-05 (DCG-81 — Subtareas DCG-104 y DCG-105) — Resiliencia con Resilience4j, logs estructurados y alertas Prometheus

- **Fecha:** 18 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-81` (`DCG-104`, `DCG-105`)
- **Objetivo de la interacción:** Implementar los patrones de resiliencia **Circuit Breaker** y **Retry** con Resilience4j en la comunicación HTTP de `auth-service` hacia `identity-service` (`IdentityClient.java`) con parámetros externalizados por ambiente (`DCG-104`), y estandarizar los logs estructurados SLF4J, las métricas de auditoría de login en Micrometer y las reglas de alerta en Prometheus/Alertmanager (`DCG-105`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

A partir de la revisión de `services/circleguard-auth-service/` tras completar `DCG-102` y `DCG-103`, levanté el siguiente diseño técnico previo:

1. **Tolerancia a fallos con Resilience4j en `IdentityClient` (`DCG-104`):**
   - Incorporar `io.github.resilience4j:resilience4j-spring-boot3:2.2.0` y `org.springframework.boot:spring-boot-starter-aop` en `build.gradle.kts`.
   - Anotar `getAnonymousId(String realIdentity)` con `@CircuitBreaker(name = "identityClient", fallbackMethod = "fallbackAnonymousId")` y `@Retry(name = "identityClient")`.
   - Definir `fallbackAnonymousId(String realIdentity, Throwable ex)` registrando un `WARN` estructurado en SLF4J y retornando el UUID determinista `UUID.nameUUIDFromBytes(("circleguard:" + realIdentity).getBytes(StandardCharsets.UTF_8))` cuando `fallbackEnabled` está activo (o relanzando la excepción cuando está deshabilitado).
   - Externalizar en `application.yml` los parámetros de la instancia `identityClient` (`sliding-window-size: 10`, `minimum-number-of-calls: 5`, `failure-rate-threshold: 50`, `wait-duration-in-open-state: 10s`, `permitted-number-of-calls-in-half-open-state: 3`, y `retry` con `max-attempts: 3` y `wait-duration: 500ms`).
2. **Observabilidad, logs estructurados y alertas de seguridad (`DCG-105`):**
   - Eliminar `System.out.println`, `System.err.println` y `e.printStackTrace()` en `LoginController.java` y `CustomUserDetailsService.java`, sustituyéndolos por `LoggerFactory.getLogger(...)` sin filtrar longitudes de contraseñas ni stack traces crudos a la salida estándar.
   - Instrumentar `LoginController` con `MeterRegistry` (usando `ObjectProvider<MeterRegistry>` con respaldo a `SimpleMeterRegistry` para pruebas `@WebMvcTest`) registrando los contadores `circleguard_auth_login_attempts_total{result="success|failure|error"}` y `circleguard_auth_visitor_handoff_total`.
   - Declarar en `deploy/k8s/base/auth-alerts.yml` las tres reglas de alerta operativas para Prometheus (`AuthServiceDown`, `AuthHighUnauthorizedRate` y `AuthIdentityCircuitBreakerOpen`).

#### B. Prompt empleado

```text
Estoy desarrollando la segunda etapa de la historia HU-AUTH-05 (DCG-81) para el proyecto CircleGuard:
`/home/faiber/Desktop/IngesoftV/final/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`
Ya me encuentro en la rama de trabajo `feat/HU-AUTH-05-k8s-resiliencia-observabilidad`, donde previamente completé las subtareas DCG-102 y DCG-103 (sondas liveness/readiness de Actuator, exposición de `/actuator/prometheus` y manifiestos K8s con HPA).

Para este segundo avance me enfocaré en completar las dos subtareas restantes: DCG-104 (Resiliencia con Resilience4j en `IdentityClient`) y DCG-105 (Logs estructurados, métricas de seguridad/autenticación, reglas de alerta Prometheus y validación de calidad). A partir de la revisión del código en `services/circleguard-auth-service/`, definí el siguiente diseño técnico previo:

1. Subtarea DCG-104 — Patrones de resiliencia (Circuit Breaker y Retry) con Resilience4j hacia `identity-service`:
   - En `services/circleguard-auth-service/build.gradle.kts`, agregar las dependencias:
     `implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")`
     `implementation("org.springframework.boot:spring-boot-starter-aop")`
   - En `src/main/java/com/circleguard/auth/client/IdentityClient.java`:
     - Anotar `getAnonymousId(String realIdentity)` con `@CircuitBreaker(name = "identityClient", fallbackMethod = "fallbackAnonymousId")` y `@Retry(name = "identityClient")`.
     - Implementar el método `public UUID fallbackAnonymousId(String realIdentity, Throwable ex)` que registre un log estructurado `WARN` con SLF4J indicando la activación del circuito/fallback hacia `identity-service` y, cuando `fallbackEnabled` esté activo, retorne el UUID determinista `UUID.nameUUIDFromBytes(("circleguard:" + realIdentity).getBytes(StandardCharsets.UTF_8))` (o relance la excepción si `fallbackEnabled` es `false`).
     - Mantener compatibilidad en invocaciones unitarias directas (sin proxy AOP) para que las pruebas existentes de `IdentityClientTest` sigan funcionando sin regresiones.
   - En `src/main/resources/application.yml` y `src/test/resources/application.yml`:
     - Externalizar la configuración de `resilience4j.circuitbreaker.instances.identityClient` (`register-health-indicator: true`, `sliding-window-type: COUNT_BASED`, `sliding-window-size: ${CB_IDENTITY_SLIDING_WINDOW_SIZE:10}`, `minimum-number-of-calls: ${CB_IDENTITY_MIN_CALLS:5}`, `failure-rate-threshold: ${CB_IDENTITY_FAILURE_RATE_THRESHOLD:50}`, `wait-duration-in-open-state: ${CB_IDENTITY_WAIT_DURATION_OPEN:10s}`, `permitted-number-of-calls-in-half-open-state: ${CB_IDENTITY_HALF_OPEN_CALLS:3}`) y `resilience4j.retry.instances.identityClient` (`max-attempts: ${RETRY_IDENTITY_MAX_ATTEMPTS:3}`, `wait-duration: ${RETRY_IDENTITY_WAIT_DURATION:500ms}`).
   - En `src/test/java/com/circleguard/auth/client/IdentityClientTest.java`:
     - Añadir pruebas unitarias que verifiquen el método `fallbackAnonymousId` y la transición del `CircuitBreaker` (`CLOSED` -> `OPEN`) ante fallos consecutivos de `identity-service`.

2. Subtarea DCG-105 — Logs estructurados, métricas de autenticación y reglas de alerta en Prometheus/Alertmanager:
   - En `LoginController.java` y `CustomUserDetailsService.java`:
     - Reemplazar los `System.out.println`, `System.err.println` y `e.printStackTrace()` por logs estructurados con SLF4J (`LoggerFactory.getLogger(...)`), evitando exponer trazas crudas o datos sensibles en consola.
     - Registrar contadores de Micrometer (`MeterRegistry` opcional/inyectado en `LoginController`) para auditar intentos de login (`circleguard_auth_login_attempts_total` con tag `result=success|failure|error`) y handoffs de visitantes (`circleguard_auth_visitor_handoff_total`).
   - Crear el manifiesto de reglas de alerta de Prometheus para `auth-service` en `deploy/k8s/base/auth-alerts.yml` con al menos 3 alertas operativas:
     - `AuthServiceDown`: `up{app="auth"} == 0` durante `1m` (`severity: critical`).
     - `AuthHighUnauthorizedRate`: tasa elevada de fallos de autenticación 401 (`rate(circleguard_auth_login_attempts_total{result="failure"}[5m]) > 0.5`) durante `2m` (`severity: warning`).
     - `AuthIdentityCircuitBreakerOpen`: circuito abierto en `identityClient` (`resilience4j_circuitbreaker_state{name="identityClient", state="open"} == 1`) durante `30s` (`severity: critical`).

---

Con base en este diseño previo:
1. Implementa los cambios de DCG-104 en `build.gradle.kts`, `IdentityClient.java`, `application.yml` y `IdentityClientTest.java`.
2. Implementa los cambios de DCG-105 en `LoginController.java`, `CustomUserDetailsService.java`, `LoginControllerTest.java` y crea `deploy/k8s/base/auth-alerts.yml`.
3. Ejecuta con Java 21 `./gradlew :services:circleguard-auth-service:test :services:circleguard-auth-service:integrationTest :services:circleguard-auth-service:jacocoTestReport :services:circleguard-auth-service:jacocoTestCoverageVerification` para verificar que todas las pruebas pasen y la cobertura de líneas en JaCoCo se mantenga >= 60%.
4. Registra la interacción en `IAG_REGISTRO.md` bajo la sección `1.7 HU-AUTH-05 (DCG-81 — Subtareas DCG-104 y DCG-105)` siguiendo el formato de Nivel 3 (Insumo previo, Prompt, Validación humana y Reflexión crítica sobre los trade-offs del Circuit Breaker y Retry en la autenticación).
```

#### C. Validación humana y hallazgos adicionales (incluyendo resolución de Code Review en PR)

- **Corrección de propagación de excepciones y orden de aspectos AOP en Resilience4j (`IdentityClient`):**
  - En la primera versión generada, `IdentityClient.getAnonymousId()` capturaba `RuntimeException` en un `try/catch` interno cuando `fallbackEnabled=true`, impidiendo que los aspectos `@Retry` y `@CircuitBreaker` observaran el fallo. Se eliminó el `try/catch` interno para que la excepción se propague por el proxy AOP y el fallback resida exclusivamente en `fallbackMethod = "fallbackAnonymousId"`.
  - Al probar el bean real con `@SpringBootTest` (`IdentityClientResilienceTest`), se detectó que con el orden por defecto de Resilience4j (`Retry` externo y `CircuitBreaker` interno con `fallbackMethod`), el `CircuitBreaker` interno capturaba el primer fallo y devolvía el fallback sin permitir que `Retry` reintentara. Se configuró explícitamente `circuit-breaker-aspect-order: 1` (externo) y `retry-aspect-order: 2` (interno) en `application.yml`, logrando que cada llamada fallida ejecute los 3 intentos de `@Retry` antes de contabilizar el fallo en el `CircuitBreaker` (`10/5/50`) y activar `fallbackAnonymousId`.
- **Eliminación de secretos por defecto (*fail-fast*) y alineación de `IDENTITY_SERVICE_FALLBACK_ENABLED=false`:**
  - Se eliminaron los valores por defecto de `SPRING_DATASOURCE_PASSWORD`, `SPRING_LDAP_PASSWORD`, `JWT_SECRET` y `QR_SECRET` en `src/main/resources/application.yml` y `SecurityConfig.java` para que el contenedor falle inmediatamente si no se inyectan los secretos.
  - Se fijó `IDENTITY_SERVICE_FALLBACK_ENABLED: "false"` en `deploy/k8s/base/apps.yml` y en `deploy/compose/{dev,stage,prod}/docker-compose.yml` para preservar la bóveda de `identity-service` como fuente única del `sub` del JWT en operación normal.
- **Stack completo de monitoreo (`deploy/k8s/monitoring/`) y compuertas de seguridad en Jenkins (`Trivy` + `OWASP ZAP`):**
  - Se añadieron las anotaciones `prometheus.io/scrape: "true"`, `prometheus.io/path: "/actuator/prometheus"` y `prometheus.io/port: "8180"` en `deploy/k8s/base/apps.yml` y se crearon los manifiestos `deploy/k8s/monitoring/{namespace,prometheus,alertmanager,grafana,kustomization}.yml`, montando `auth-prometheus-alerts` en `/etc/prometheus/rules`.
  - Se integró `trivy v0.57.1` en `deploy/compose/tools/jenkins/Dockerfile` y se añadieron en `Jenkinsfile` y `jenkins/vars/ci.groovy` las etapas obligatorias `Trivy IaC Security Gate`, `Trivy Image Security Gate` (`--severity CRITICAL --exit-code 1`) y `OWASP ZAP Security Gate` sobre `cg-stage` antes de `Approval prod` / `Deploy prod`.
  - Se actualizaron `deploy/k8s/K8S.md` y `docs/agil/backlog.md` cruzando los identificadores `DCG-81` (`DCG-102`..`DCG-105`), `P2-02`..`P2-06` y `E2.2`..`E2.5`.
- **Resultados de pruebas y cobertura JaCoCo (`jacocoTestReport.xml`):**
  - **Total de pruebas ejecutadas (`test` + `IdentityClientResilienceTest` + `integrationTest`):** **`32 / 32` aprobadas (`0` fallos)**.
  - **Cobertura de líneas (`LINE`):** **`100.00%`** (`189 / 189` líneas cubiertas, umbral mínimo `60%`).
  - **Cobertura de instrucciones (`INSTRUCTION`):** **`98.91%`** (`815 / 824` instrucciones).
  - **Cobertura de métodos (`METHOD`):** **`100.00%`** (`42 / 42` métodos).
  - **Cobertura de clases (`CLASS`):** **`100.00%`** (`10 / 10` clases).

#### D. Reflexión crítica

Combinar **Retry** (`max-attempts: 3`, `wait-duration: 500ms`) y **Circuit Breaker** (`failure-rate-threshold: 50%`, `wait-duration-in-open-state: 10s`) en la llamada síncrona de `auth-service` hacia `identity-service` implica un trade-off arquitectónico importante entre disponibilidad y latencia de cola (*tail latency*): mientras el circuito permanece `CLOSED`, un fallo transitorio de red puede añadir hasta `1.0s` de espera por los reintentos antes de resolver el login; sin embargo, si `identity-service` cae por completo, el `CircuitBreaker` pasa a estado `OPEN` tras alcanzar el umbral de la ventana deslizante y corta de inmediato las llamadas remotas (*fail-fast*), evitando el agotamiento del pool de hilos de Tomcat en `auth-service` y activando el fallback o la alerta crítica `AuthIdentityCircuitBreakerOpen` en Prometheus en menos de 30 segundos. Asimismo, reemplazar las impresiones crudas en consola (`System.out.println` que exponían la longitud de la contraseña) por logs estructurados SLF4J y contadores Micrometer elimina una vulnerabilidad de fuga de información en logs centralizados (Loki) y permite detectar ataques de fuerza bruta mediante la alerta `AuthHighUnauthorizedRate`.

---

### 1.8 HU-ID-05 (DCG-86 — Subtareas DCG-119 y DCG-120) — Orquestación en Kubernetes, HPA, sondas Actuator y observabilidad Prometheus en `identity-service`

- **Fecha:** 20 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-86` (`DCG-119`, `DCG-120`)
- **Objetivo de la interacción:** Configurar la orquestación de `circleguard-identity-service` en Kubernetes con sondas especializadas de Actuator (`liveness` y `readiness`), anotaciones de scraping Prometheus y autoescalado HPA (`DCG-119`), e integrar Micrometer Prometheus junto con la externalización de CORS en el servicio (`DCG-120`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Tras revisar el estado actual de `services/circleguard-identity-service/` y `deploy/k8s/base/` en `origin/main` (y aplicando las lecciones aprendidas de la revisión del PR de `HU-AUTH-05`), definí el siguiente diseño previo:

1. **Orquestación Kubernetes y HPA (`DCG-119`):**
   - En `deploy/k8s/base/apps.yml`, retirar `replicas: 1` del Deployment `identity` para evitar conflictos con el HPA, añadir anotaciones `prometheus.io/{scrape,path,port}` apuntando al puerto `8083`, y migrar las sondas genéricas `/actuator/health` a `/actuator/health/readiness` (`initialDelaySeconds: 30`, `periodSeconds: 10`, `failureThreshold: 3`) y `/actuator/health/liveness` (`initialDelaySeconds: 60`, `periodSeconds: 15`, `failureThreshold: 3`).
   - En `deploy/k8s/base/hpa.yml`, declarar el `HorizontalPodAutoscaler` para `identity` con `minReplicas: 1`, `maxReplicas: 3`, objetivo de CPU en `75%` y `stabilizationWindowSeconds: 300`.
2. **Observabilidad Prometheus y Seguridad (`DCG-120`):**
   - Agregar `io.micrometer:micrometer-registry-prometheus` en `build.gradle.kts` (eliminando la línea duplicada de `spring-boot-starter-actuator`).
   - Exponer `health,info,prometheus` en `application.yml` con `management.endpoint.prometheus.enabled: true` y `management.prometheus.metrics.export.enabled: true`, manteniendo los secretos en modo *fail-fast* (sin defaults).
   - Permitir `/actuator/health`, `/actuator/health/**`, `/actuator/info` y `/actuator/prometheus` en `SecurityConfig.java` y externalizar `CORS_ALLOWED_ORIGINS`.

#### B. Prompt empleado

```text
Estoy desarrollando la primera etapa de la historia HU-ID-05 (DCG-86) para el proyecto CircleGuard:
`/home/faiber/Desktop/IngesoftV/final/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`
en la rama `feat/HU-ID-05-k8s-resiliencia-observabilidad` (creada desde `origin/main`).

Para esta primera etapa me enfocaré en las subtareas DCG-119 (Manifiestos K8s, HPA y sondas especializadas) y DCG-120 (Métricas Prometheus, sondas Actuator y CORS externalizado):
1. En `services/circleguard-identity-service/build.gradle.kts`: agregar `io.micrometer:micrometer-registry-prometheus` y eliminar la dependencia duplicada de `spring-boot-starter-actuator`.
2. En `application.yml` (main y test): exponer `health,info,prometheus`, habilitar `management.endpoint.prometheus.enabled: true` y `management.prometheus.metrics.export.enabled: true`, y parametrizar `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:8081,http://localhost:8080}`.
3. En `SecurityConfig.java`: permitir `/actuator/info` y `/actuator/prometheus` junto a `/actuator/health/**` sin JWT, e inyectar `cors.allowed-origins` con `@Value`.
4. En `deploy/k8s/base/apps.yml` y `deploy/k8s/base/hpa.yml`: retirar `replicas: 1` de `identity`, agregar anotaciones `prometheus.io/{scrape,path,port}` (`8083`), migrar sondas a `/actuator/health/readiness` y `/actuator/health/liveness`, y agregar HPA `identity` (1–3 réplicas @ 75% CPU).
5. Agregar `IdentityActuatorObservabilityTest.java` verificando `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` y `/actuator/prometheus`, y ejecutar `:test`, `:jacocoTestReport` y `:jacocoTestCoverageVerification`.
```

#### C. Validación humana y hallazgos adicionales

- Se verificó con `IdentityActuatorObservabilityTest` que los cuatro endpoints de observabilidad (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` y `/actuator/prometheus`) responden `200 OK` sin requerir Bearer JWT.
- Se validó la sintaxis YAML de `deploy/k8s/base/apps.yml` (18 documentos) y `deploy/k8s/base/hpa.yml` (4 documentos), corrigiendo una duplicación inicial al agregar el bloque HPA de `identity`.
- Resultado de pruebas y cobertura JaCoCo en Etapa 1: `BUILD SUCCESSFUL`, cobertura de líneas `92.42%` (`61/66` líneas), métodos `100.00%` (`13/13`), clases `100.00%` (`3/3`).

#### D. Reflexión crítica

Aplicar de entrada la configuración explícita `management.prometheus.metrics.export.enabled: true`, la eliminación de `replicas: 1` en el Deployment gestionado por HPA y las anotaciones `prometheus.io/*` evitó repetir los hallazgos de la primera iteración de `HU-AUTH-05`, logrando que la observabilidad y el autoescalado de `identity-service` quedaran validados en la primera ejecución.

---

### 1.9 HU-ID-05 (DCG-86 — Subtareas DCG-121 y DCG-122) — Resiliencia Resilience4j en auditoría Kafka, logs estructurados sin PII y alertas Prometheus

- **Fecha:** 20 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Historia / Subtareas en Jira:** `DCG-86` (`DCG-121`, `DCG-122`)
- **Objetivo de la interacción:** Implementar los patrones de resiliencia **Circuit Breaker** y **Retry** con Resilience4j en la publicación de eventos de auditoría Kafka (`IdentityAuditPublisher.java`) verificados contra el proxy real de Spring AOP (`DCG-121`), y estandarizar los logs estructurados SLF4J sin fuga de PII, métricas Micrometer de bóveda, fail-fast de secretos y reglas de alerta Prometheus (`DCG-122`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Al analizar `IdentityVaultController.java` y `IdentityVaultService.java` identifiqué tres mejoras críticas de resiliencia, observabilidad y seguridad:

1. **Desacoplamiento y resiliencia en la auditoría Kafka (`DCG-121`):**
   - `IdentityVaultController.lookupIdentity()` invocaba directamente `kafkaTemplate.send("audit.identity.accessed", event)` dentro del bloque `finally`: si Kafka cae o presenta alta latencia, el lookup autorizado del personal de salud fallaba con `500` aunque PostgreSQL estuviera sano.
   - Se diseñó el componente `IdentityAuditPublisher` anotado con `@CircuitBreaker(name = "identityAuditKafka", fallbackMethod = "fallbackAuditEvent")` y `@Retry(name = "identityAuditKafka")`, con `circuit-breaker-aspect-order: 1` (externo) y `retry-aspect-order: 2` (interno) en `application.yml`, sin `try/catch` interno que oculte fallos, y con fallback estructurado SLF4J + contador `circleguard_identity_audit_fallback_total`.
2. **Logs sin PII, fail-fast de secretos y alertas Prometheus (`DCG-122`):**
   - Retirar el valor por defecto inseguro `:circleguard-default-salt` en `IdentityVaultService` (`@Value("${vault.hash-salt}")`) para garantizar arranque *fail-fast*.
   - Instrumentar `IdentityVaultController` con `MeterRegistry` (`circleguard_identity_mappings_total{type="user|visitor"}` y `circleguard_identity_lookups_total{status="success|not_found|error"}`) y registrar logs SLF4J referenciando exclusivamente `anonymousId` (nunca `realIdentity`).
   - Declarar las 3 alertas operativas (`IdentityServiceDown`, `IdentityAuditCircuitBreakerOpen` e `IdentityHighLookupFailureRate`) en `deploy/k8s/base/identity-alerts.yml`.

#### B. Prompt empleado

```text
Estoy desarrollando la segunda etapa de la historia HU-ID-05 (DCG-86) en la rama `feat/HU-ID-05-k8s-resiliencia-observabilidad` para completar las subtareas DCG-121 (Resilience4j en auditoría Kafka) y DCG-122 (Logs estructurados sin PII, fail-fast de secretos, métricas Micrometer y alertas Prometheus):

1. Subtarea DCG-121:
   - Agregar `spring-boot-starter-aop` y `io.github.resilience4j:resilience4j-spring-boot3:2.2.0` en `services/circleguard-identity-service/build.gradle.kts`.
   - Crear `IdentityAuditPublisher.java` con `@CircuitBreaker(name = "identityAuditKafka", fallbackMethod = "fallbackAuditEvent")` y `@Retry(name = "identityAuditKafka")`, sin `try/catch` interno para que las excepciones se propaguen por el proxy AOP, registrando en el fallback un log `WARN` estructurado (sin PII) y el contador `circleguard_identity_audit_fallback_total`.
   - Externalizar en `application.yml` `circuit-breaker-aspect-order: 1`, `retry-aspect-order: 2`, ventana `10/5/50` (`wait-duration-in-open-state: 10s`, `half-open: 3`) y `max-attempts: 3`.
   - Crear `IdentityAuditPublisherResilienceTest.java` (`@SpringBootTest` real contra el proxy AOP y `CircuitBreakerRegistry`) verificando reintentos transitorios, apertura del circuito a `OPEN` tras 5 fallos (15 envíos), cortocircuito al fallback y la métrica `resilience4j_circuitbreaker_state{name="identityAuditKafka",state="open",} 1.0` en `/actuator/prometheus`.

2. Subtarea DCG-122:
   - Eliminar el default `:circleguard-default-salt` en `IdentityVaultService.java` y agregar logs SLF4J usando únicamente `anonymousId`.
   - Integrar `IdentityAuditPublisher` y contadores Micrometer (`circleguard_identity_mappings_total`, `circleguard_identity_lookups_total`) en `IdentityVaultController.java` y actualizar `IdentityVaultControllerTest.java`.
   - Crear `deploy/k8s/base/identity-alerts.yml` (y registrarlo en `deploy/k8s/base/kustomization.yml`) con las alertas `IdentityServiceDown`, `IdentityAuditCircuitBreakerOpen` e `IdentityHighLookupFailureRate`.
   - Ejecutar `:test`, `:jacocoTestReport`, `:jacocoTestCoverageVerification` y documentar ambas etapas en `IAG_REGISTRO.md`.
```

#### C. Validación humana y hallazgos adicionales

- **Prueba real de resiliencia sobre Spring AOP (`IdentityAuditPublisherResilienceTest`):** Se comprobó mediante `AopUtils.isAopProxy(auditPublisher)` que las anotaciones `@CircuitBreaker` y `@Retry` operan con la configuración externalizada `10/5/50` y `maxAttempts=3`: 2 fallos transitorios de Kafka reintentan y tienen éxito en el 3er intento manteniendo el circuito `CLOSED`, mientras que 5 fallos sostenidos (`15` intentos a `KafkaTemplate`) abren el circuito a `OPEN`, cortocircuitan la 6ta llamada sin invocar Kafka y exponen `resilience4j_circuitbreaker_state{name="identityAuditKafka",state="open",} 1.0` y `circleguard_identity_audit_fallback_total` en `/actuator/prometheus`.
- **Resultados de pruebas y cobertura JaCoCo (`jacocoTestReport.xml`):**
  - **Total de pruebas ejecutadas:** **`20 / 20` aprobadas (`0` fallos)**.
  - **Cobertura de líneas (`LINE`):** **`93.86%`** (`107 / 114` líneas cubiertas, umbral mínimo `60%`).
  - **Cobertura de instrucciones (`INSTRUCTION`):** **`88.97%`** (`508 / 571` instrucciones).
  - **Cobertura de métodos (`METHOD`):** **`100.00%`** (`23 / 23` métodos).
  - **Cobertura de clases (`CLASS`):** **`100.00%`** (`4 / 4` clases).
- **Validación de manifiestos K8s:** Validada la sintaxis YAML de `deploy/k8s/base/{apps,hpa,identity-alerts,kustomization}.yml`.

#### D. Reflexión crítica

Proteger la publicación de auditoría Kafka en `identity-service` con `@CircuitBreaker` (orden `1`) + `@Retry` (orden `2`) y fallback estructurado resuelve un acoplamiento crítico de disponibilidad: ante una interrupción del bróker Kafka, el personal del Centro de Salud puede seguir resolviendo identidades autorizadas (`GET /api/v1/identities/lookup/{id}`) sin degradación en cascada, mientras que el evento de auditoría queda registrado en los logs estructurados (sin exponer PII) y dispara la alerta crítica `IdentityAuditCircuitBreakerOpen` en Prometheus para intervención operativa inmediata.

---

### 1.10 HU-DOCS-07 — Actualización y ampliación del informe final de Parte 2 con evidencias actuales

- **Fecha:** 21 de noviembre de 2026
- **Autor:** Faiber Piedrahita
- **Rama / Entregable:** `docs/HU-DOCS-07-actualizar-informe-parte2` (`E2.4`, `E2.7`, `E2.8`, `E2.9`)
- **Objetivo de la interacción:** Auditar la Sección 13 de `CircleGuard_INFORME_FINAL.docx` frente a los requisitos de la Parte 2 (`Proyecto_Final_CircleGuard.tex`), corregir imprecisiones factuales, incorporar las 14 capturas de evidencia actuales de la plataforma y redactar las subsecciones faltantes (`13.10` a `13.15`).

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Previo a la actualización del documento Word, definí el alcance y proporcioné las evidencias autenticadas necesarias:

1. **Verificación visual previa:** Solicité exportar primero las capturas a una carpeta local (`Desktop/Capturas`) para revisar manualmente cada imagen antes de incorporarla al informe en una rama dedicada.
2. **Credenciales y capturas autenticadas:** Suministré el acceso de Jenkins y las capturas actualizadas de SonarQube (`5/5 Passed` y detalle de Quality Gate `Passed`) y Jenkins Stage View para reemplazar vistas que requerían sesión o correspondían a ramas ya fusionadas.
3. **Revisión del análisis de brechas:** Revisé y aprobé explícitamente el listado de hallazgos detectados en la Sección 13 antes de autorizar la edición programática de `CircleGuard_INFORME_FINAL.docx`.

#### B. Prompt empleado

```text
Necesito que me ayudes a actualizar y ampliar el informe con capturas actuales, ten en cuenta todo lo que este pide y que ya estamos en la segunda fase del taller/parcial. Antes de agregar las capturas y editar el informe por favor déjalas en una carpeta en Desktop/Capturas para yo verlas y confirmarte si proseguir; en caso de proseguir crea una nueva rama para editar el informe. Primero analiza qué falta por actualizar en el informe y dime.
```

#### C. Validación humana y hallazgos adicionales

- Se corrigió el error factual en la subsección `13.6` (que atribuía `IdentityClient` con Resilience4j al gateway en lugar de `auth-service`, y omitía `IdentityAuditPublisher` en `identity-service`, las 9 reglas de Alertmanager y el stack Loki/Promtail).
- Se alineó la tabla de entregables `13.8` (`P2-01` a `P2-10`) con el estado real de `main` tras los merges de los PRs `#92`, `#93`, `#134`, `#136`, `#137`, `#138` y `#139` (`v1.14.0`), y se corrigió la ruta de `IAG_REGISTRO.md` en `13.9`.
- Se añadieron las subsecciones obligatorias faltantes `13.10` (Patrones de diseño y arquitectura), `13.11` (Informe consolidado de pruebas y calidad), `13.12` (Change management y releases), `13.13` (Sprint 2 y retrospectiva), `13.14` (Análisis de costos AWS vs. on-premise) y `13.15` (Executive Summary en inglés), embebiendo las 14 capturas numeradas (`Figura 2` a `Figura 15`) sin alterar las Secciones 1–12 ni los Anexos A–C.

#### D. Reflexión crítica

Realizar una auditoría previa del documento frente al enunciado del proyecto y validar manualmente las evidencias antes de editar el `.docx` evitó introducir información desactualizada o imprecisa. El uso de un script reproducible con `python-docx` permitió preservar intacto el formato histórico de la Parte 1 mientras se integraban de forma consistente tablas, figuras y texto técnico verificado directamente contra el estado real del repositorio `main`.

---

<a id="modulo-daniela"></a>
## 2. Módulo: `circleguard-gateway-service`, `circleguard-file-service` e Infraestructura CI/CD (Daniela Castaño)

**Nivel de uso declarado:** 3 — colaboración con IAG como herramienta de apoyo. En todas las intervenciones **Daniela Castaño determinó las acciones, decidió qué cambios se aplicaron y validó personalmente cada resultado antes de incorporarlo**. El IAG se usó únicamente para acelerar análisis, borradores y contrastes; ninguna salida se aceptó sin verificación humana, ejecución real y apreciación crítica de la estudiante.

**Criterio de registro aplicado:** para cada entrada se distingue el insumo propio, el apoyo recibido de IAG, la validación y apreciación humana, y la reflexión final. Las decisiones técnicas, los criterios de aceptación y la aceptación o rechazo de lo generado son responsabilidad de la estudiante.

**Alcance de la sección.** Se registran aquí las intervenciones sobre el gateway, el `file-service` y la plataforma CI/CD. Las historias de `dashboard-service` (HU-DASH-03, HU-DASH-04 y HU-DASH-05) tienen entrada propia en las secciones [5](#5-módulo-circleguard-dashboard-service-e-integración-cicd-hu-dash-03), [6](#6-módulo-circleguard-dashboard-service--observabilidad-y-seguridad-hu-dash-05) y [7](#7-entrada-ejecución-de-hu-dash-04-despliegue-y-rollback-on-premise-de-dashboard-service), y se referencian desde esta sección sin duplicar su contenido.

**Convención de registro.** Los prompts marcados como *reconstruidos y contrastados* se derivaron del historial de la sesión y se cotejaron contra los commits, reportes y evidencias versionadas. Los prompts literales que se conservan se identifican como *transcripción de sesión*. Ninguna métrica se tomó de una conversación sin contraste con un artefacto del repositorio.

**Trazabilidad de las intervenciones**

| ID | Fecha | Entregable | Commits / PR | Evidencia principal |
|---|---|---|---|---|
| 2.1 | 2026-11-08 | E1.4 | `7c4d938` (#15), `f055b99` (#38), `c3ac704` (#23) | `docs/informe/HU-GATEWAY-04.md`, `docs/informe/evidencia/HU-GATEWAY-04/` |
| 2.2 | 2026-11-08 | E1.6 | `a7ad652`, `8344497`, `5e3376f`, `0d0222d`, `6432bb8`, `747f88d` | `docs/informe/GW-CI-02-Integracion-Redis.md`, `GW-CI-02-Validacion-Testcontainers.docx` |
| 2.3 | 2026-11-20 | E1.6 | `4e359c1` (#89) | `docs/informe/FS-CI-01-Informe-Pruebas.md`, `docs/informe/evidencia/fs-ci-01/` |
| 2.4 | 2026-11-21 | E1.3 / E1.2 | `2635ca5` (#133) | `docs/informe/evidencia/HU-FILE-02/` |
| 2.5 | 2026-11-22 | E2.4 / E2.5 | `74acc53` (#150) | `docs/informe/HU-FILE-05.md` |
| 2.6 | 2026-11-22 | E1.6 | `251e923` (#144), merge `21c406b` | `docs/informe/HU-FILE-04-Integracion-MinIO.md`, `docs/informe/evidencia/HU-FILE-04/` |
| 2.7 | 2026-11-22 | E1.4 / E1.6 | `af451ff` (#158) | `docs/informe/evidencia/trazabilidad/coverage-reports.md` |
| 2.8 | 2026-11-08 a 2026-11-11 | E1.2 / E1.8 | PR-15, PR-45 | `docs/infra/evidencia/idempotency/IDEMPOTENCIA.md` |
| 2.9 | Transversal | Cierre y sustentación | `e600c02`, `aab6bc0` | `IAG_REGISTRO.md`, `docs/informe/CircleGuard_INFORME_FINAL.docx` |

---

<a id="iag-daniela-21"></a>
### 2.1 HU-GATEWAY-CICD-01 — Plataforma CI/CD del gateway

- **Fecha:** 8 de noviembre de 2026.
- **Autor:** Daniela Castaño.
- **Rama / PR:** `feature/hu-gateway-cicd` → PR #15 y PR #38; cierre documental en PR #23 (`c3ac704`).
- **Commits:** `7c4d938`, `f055b99`, `c3ac704`.
- **Entregable:** E1.4 — pipeline de integración continua con Jenkins.
- **Objetivo:** dejar una plataforma CI/CD multibranch para el gateway, disparada por webhook, con etapas reutilizables y quality gate bloqueante, sin requerir intervención manual para cada rama.

**A. Insumo propio.** Análisis de la estructura del servicio, lectura de los requisitos del enunciado para E1.4 y E1.6, y decisión de topología: dos instancias de gateway, ambientes `dev`/`stage`/`prod`, registro privado y SonarQube on-premise. Se definió que el job debía ser multibranch y que el disparador sería el webhook `/github-webhook/`, no un `cron`.

**B. Apoyo IAG — prompt (reconstruido y contrastado).** «Analiza la estructura del microservicio Gateway, valida el plan de la historia contra los entregables E1.4 y E1.6 del enunciado, y propón la configuración del job multibranch, el webhook y el pipeline con etapas reutilizables en la Shared Library». La sesión de trabajo original se conserva como referencia histórica: `https://claude.ai/chat/34eb30b0-e91f-4b8b-afba-8f22890194c6`.

**C. Validación y apreciación humana.** Se revisaron y ejecutaron los scripts `infra/bootstrap-*.sh`, se verificaron credenciales y se ajustó la configuración del webhook y de la protección de `main`. La salida quedó versionada en `Jenkinsfile`, `jenkins/vars/`, `deploy/compose/tools/jenkins/cac/jenkins.yml` y `docs/informe/HU-GATEWAY-04.md`. Las capturas de Jenkins, SonarQube y GitHub se conservan en `docs/informe/evidencia/HU-GATEWAY-04/`, incluidas la notificación de fallo (`09-failure-notification-github.png`) y la Reproducción del quality gate en rojo (`11-quality-gate-red.png`).

**D. Apreciación humana y reflexión.** La automatización no sustituye la verificación: un webhook configurado sin el check obligatorio en `main` deja el flujo expuesto a fusiones sin gate. La decisión de conservar capturas reales, aunque provengan de la API de GitHub y no de una sesión web, fue preferible a omitir la evidencia.

---

<a id="iag-daniela-22"></a>
### 2.2 GW-CI-02 — Integración Gateway–Redis con Testcontainers

- **Fecha:** 8 de noviembre de 2026 (reverificación posterior al rebase el mismo día).
- **Autor:** Daniela Castaño.
- **Rama / PR:** `feat/HU-GATEWAY-03-integracion-redis-testcontainers` → PR #22.
- **Commits:** `6432bb8`, `747f88d`, `0d0222d`, `8344497`, `a7ad652`; documentación posterior `5e3376f`.
- **Entregable:** E1.6 — pruebas automatizadas.
- **Objetivo:** validar el flujo de validación de QR contra una instancia real y efímera de Redis, sin mocks, y ejecutarlo como tarea Gradle independiente dentro de Jenkins.

**A. Insumo propio.** Lectura de la sección 5 del documento de requerimientos del gateway y diagnóstico de `GatewayRedisIntegrationTest`. Se decidieron la imagen `redis:7.2-alpine`, el filtro `**/integration/**` para separar unitarias de integración, el cálculo de JaCoCo solo sobre unitarias y la convención de commits sin la clave interna `[CG-02]`.

**B. Apoyo IAG — prompt (transcripción de sesión, ortografía normalizada).** «Dame el plan completo» y, tras la revisión, «Ejecuta el plan». El detalle técnico se reconstruyó y contrastó con `docs/informe/GW-CI-02-Integracion-Redis.md` y `docs/informe/Gateway-Cobertura.md`.

**C. Validación y apreciación humana.** Tres ejecuciones consecutivas con 9 pruebas, 0 fallos y 0 errores. La primera serie registró 28 s, 25 s y 26 s; la serie posterior al rebase sobre `main` registró 24 s, 25 s y 24 s, con tiempos JUnit de 3.541 s, 3.628 s y 3.496 s y `count=0` de contenedores tras la ventana de limpieza de Ryuk. El reporte de cobertura unitaria del gateway quedó en 49/49 líneas (100 %) en el consolidado `Gateway-Cobertura.md`; el registro de la sesión reportó 95.92 % sobre una medición intermedia, que se conserva como histórico y no como cifra vigente en el informe. Se documentó el error de WSL (`/mnt/c` montado como 9p), resuelto con `gradle/wsl-ext4.init.gradle.kts` sin subir el límite de 3 minutos de CI.

**D. Apreciación humana y reflexión.** Un contenedor «arriba» no implica un servicio listo: fue necesario esperar `Ready to accept connections`. El límite de 3 minutos de la tarea es una restricción de infraestructura, no una tolerancia a código lento; mover las salidas a ext4 resolvió el síntoma sin relajar el gate.

---

<a id="iag-daniela-23"></a>
### 2.3 FS-CI-01 — Pruebas unitarias del file-service

- **Fecha:** 20 de noviembre de 2026.
- **Autor:** Daniela Castaño.
- **Rama / PR:** `feat/FS-CI-01-file-service-tests` → PR #89.
- **Commit:** `4e359c1`.
- **Entregable:** E1.6 — pruebas automatizadas.
- **Objetivo:** cubrir carga válida, archivo vacío, descarga existente y descarga inexistente en `circleguard-file-service`, con cobertura JaCoCo superior al umbral acordado del 60 %.

**A. Insumo propio.** Diagnóstico del estado previo: `FileStorageService.loadFile` era un stub que devolvía `null`, la interfaz `Resource` era package-private y no existía endpoint de descarga. Se acotó el arreglo a lo estrictamente necesario para hacer comprobables los criterios de la historia.

**B. Apoyo IAG — prompt (reconstruido y contrastado).** «Revisa los criterios de FS-CI-01 sobre el file-service, identifica el mínimo cambio de producción necesario para poder probarlos y genera las pruebas unitarias con JaCoCo; no introduzcas cambios de producción innecesarios».

**C. Validación y apreciación humana.** Se implementó `GET /api/v1/files/download/{filename}` con respuestas 200/404 y un `loadFile` real con validación de path traversal. Resultado: 10 pruebas (6 de servicio y 4 de controlador), 0 fallos y 0 errores; cobertura de 23/29 líneas (79.31 %), 102/119 instrucciones (85.71 %) y 8/10 ramas (80 %). Los XML de JUnit y JaCoCo están restaurados en `docs/informe/evidencia/fs-ci-01/` y el detalle en `docs/informe/FS-CI-01-Informe-Pruebas.md`.

**D. Apreciación humana y reflexión.** La historia era de pruebas, pero era imposible cumplirla sin un arreglo mínimo de producción. Registrar el stub como hallazgo y documentar el parche evitó convertir una limitación del código en una prueba ficticia.

---

<a id="iag-daniela-24"></a>
### 2.4 HU-FILE-02 — Imagen inmutable y dependencia de MinIO

- **Fecha:** 21 de noviembre de 2026.
- **Autor:** Daniela Castaño.
- **Rama / PR:** `feat/HU-FILE-02-imagen-inmutable-minio` → PR #133.
- **Commit:** `2635ca5`.
- **Entregable:** E1.3 — empaquetamiento inmutable.
- **Objetivo:** empaquetar el file-service como imagen no root con healthcheck y metadatos verificables, y dejar la dependencia de MinIO declarada para el entorno de integración.

**A. Insumo propio.** Revisión del Dockerfile del servicio y de las políticas de empaquetamiento del equipo: multi-stage, usuario no root, healthcheck sobre `/actuator/health` y sin secretos en la imagen. Se decidió conservar el puerto 8085 y publicar labels de OCI para trazabilidad.

**B. Apoyo IAG — prompt (reconstruido y contrastado).** «Ajusta el Dockerfile del file-service para que compile en multi-stage, ejecute como usuario no root, exponga healthcheck y labels, y revisa que no haya credenciales embebidas; luego verifica la imagen construida con docker inspect».

**C. Validación y apreciación humana.** Los artefactos reales están en `docs/informe/evidencia/HU-FILE-02/`: `03-docker-inspect-user.txt` reporta `circleguard`, `05b-healthcheck.txt` muestra el `CMD-SHELL` con `wget` contra `/actuator/health`, `09-no-secrets.txt` confirma «OK: no hardcoded credentials in tracked sources» y `12-compose-config.txt` documenta la dependencia de MinIO. La imagen quedó referenciada por tag semver, sin `latest`.

**D. Apreciación humana y reflexión.** El healthcheck del contenedor y la sonda de Kubernetes son mecanismos distintos. Mantener ambos alineados evita que un contenedor «sano» para Docker sea declarative-mente inadecuado para el orquestador.

---

<a id="iag-daniela-25"></a>
### 2.5 HU-FILE-05 — Orquestación, observabilidad y seguridad del file-service

- **Fecha:** 22 de noviembre de 2026.
- **Autor:** Daniela Castaño.
- **Rama / PR:** `feat/HU-FILE-05-orquestar-file-service-en-kubernetes-observabilidad-seguridad`, issue #149 → PR #150.
- **Commit:** `74acc53`.
- **Entregable:** E2.4 / E2.5 — orquestación y observabilidad de la Parte 2.
- **Objetivo:** llevar el file-service a Kubernetes con sondas Actuator nativas, métricas Prometheus, trazas OTLP y un contador propio de subidas, sin versionar secretos ni configuración sensible.

**A. Insumo propio.** Se partió del equivalente ya resuelto para el gateway (HU-GATEWAY-06) y se adaptó al file-service. La decisión propia fue conservar `management.endpoints.web.exposure.include=health,info,prometheus`, habilitar los grupos de probes, fijar `show-details: never` y parametrizar el endpoint OTLP por variable de entorno.

**B. Apoyo IAG — prompt (reconstruido y contrastado).** «Aplica al file-service el mismo patrón de orquestación, sondas y observabilidad usado en el gateway: anotaciones de scrape en el Service, readiness/liveness Actuator en el Deployment, dependencias Micrometer y OTLP, y un contador de subidas con resultado. No versiones secretos».

**C. Validación y apreciación humana.** `./gradlew :services:circleguard-file-service:test :services:circleguard-file-service:jacocoTestCoverageVerification --console=plain --no-daemon` terminó en `BUILD SUCCESSFUL` con 12 pruebas y cobertura por encima del 60 %. `git diff --check` quedó limpio y no se versionaron secretos, `*.tfstate`, `backend.hcl`, `tfplan` ni `.terraform/`. El detalle está en `docs/informe/HU-FILE-05.md`. La validación on-prem con `kubectl diff` en `cg-dev`, `cg-stage` y `cg-prod` quedó declarada como pendiente de autorización, no como ejecutada.

**D. Apreciación humana y reflexión.** Añadir instrumentación cambia el contrato de observabilidad del servicio, no solo sus dependencias. Por eso el contador `circleguard_file_uploads_total{result="success"|"failure"}` se probó con `SimpleMeterRegistry` y se documentó su etiqueta antes de solicitarse al clúster.

---

<a id="iag-daniela-26"></a>
### 2.6 HU-FILE-04 — Integración File–MinIO con Testcontainers

- **Fecha:** 22 de noviembre de 2026.
- **Autor:** Daniela Castaño.
- **Rama / PR:** `feat/HU-FILE-04-integracion-file-minio-con-testcontainers` → PR #144; merge final `21c406b`.
- **Commit:** `251e923` (con integración de `af451ff` de la rama principal).
- **Entregable:** E1.6 — pruebas automatizadas.
- **Objetivo:** validar el contrato S3-compatible real del file-service contra un MinIO efímero, usando el adaptador de producción y ejerciendo los endpoints HTTP de carga, descarga y borrado.

**A. Insumo propio.** Se decidió un adaptador `S3FileStorageService` detrás del puerto `FileStorage`, con `file.storage.mode=local` por defecto para no exigir S3 en la Parte 1 y `s3` en el perfil de integración. Se fijó la imagen `bitnamilegacy/minio:2025.4.22`, espera `GET /minio/health/live → 200`, creación automática del bucket `circleguard-files` y tarea Gradle `integrationTest` con timeout de 3 minutos.

**B. Apoyo IAG — prompt (reconstruido y contrastado a partir de la transcripción de sesión).** «Contrasta la evidencia con el enunciado del proyecto final y agrega un estilo universitario a la portada del informe usando el logo de la Icesi». La parte técnica se contrastó con `docs/informe/HU-FILE-04-Integracion-MinIO.md`; la petición de estilo se atendió en el DOCX, no en el código.

**C. Validación y apreciación humana.** Tres ejecuciones consecutivas sobre `21c406b`: 53 s, 52 s y 52 s; 5 pruebas de integración por corrida, 0 fallos y 0 errores; sin contenedores residuales tras Ryuk. La suite unitaria conservada tras el merge quedó en 32 pruebas y 81.31 % de líneas (87/107). El XML `tests=5 skipped=0 failures=0 errors=0` y los logs están en `docs/informe/evidencia/HU-FILE-04/`. El anexo B.1 del informe incorpora la portada y esta evidencia.

**D. Apreciación humana y reflexión.** El enunciado define al file-service como almacenamiento compatible S3, pero en la Parte 1 solo lo compila, prueba y empaqueta. separating claramente modo por defecto, perfil de integración y despliegue evitó inventar infraestructura fuera de alcance y dejó el mismo artefacto listo para la Parte 2.

---

<a id="iag-daniela-27"></a>
### 2.7 Cierre de gates de cobertura y calidad (#158)

- **Fecha:** 22 de noviembre de 2026.
- **Autor:** Daniela Castaño.
- **Rama / PR:** `chore/jacoco-convention-all-services` → PR #158.
- **Commit:** `af451ff`.
- **Entregable:** E1.4 / E1.6 — gates de cobertura y calidad.
- **Objetivo:** unificar la convención JaCoCo de los servicios y evitar que el merge de HU-FILE-04 dejara el file-service fuera del gate del 60 %.

**A. Insumo propio.** Se detectó que la rama de HU-FILE-04 tenía un bloque JaCoCo local mientras `main` ya usaba `gradle/jacoco-convention.gradle`. La decisión fue conservar ambos sin duplicar reglas: la convención aplica el mínimo y la verificación de pruebas no omitidas para todos los servicios, incluido `file-service`.

**B. Apoyo IAG — prompt (reconstruido y contrastado).** «Revisa por qué el gate de cobertura no aplica igual a todos los servicios y unifica la configuración JaCoCo sin cambiar el umbral acordado ni relajar la verificación de pruebas omitidas».

**C. Validación y apreciación humana.** El merge final `21c406b` conservó `apply(from = "$rootDir/gradle/jacoco-convention.gradle")` junto al bloque local. El índice vigente de cobertura está en `docs/informe/evidencia/trazabilidad/coverage-reports.md`; el XML de form se versionó como `jacoco-form-test-report.xml`. La medición de `promotion` (564/733 líneas, 76.9 %) quedó pendiente de publicación como artefacto versionado y no se presenta como evidencia ya cerrada.

**D. Apreciación humana y reflexión.** Una convención compartida solo es útil si se comprueba en cada servicio. Revisar el merge final, no solo la rama propia, fue lo que evitó una regla escrita pero no aplicada.

---

<a id="iag-daniela-28"></a>
### 2.8 Bitácora E1.2 — Idempotencia, evidencias y cierre documental

- **Fecha:** 8 a 11 de noviembre de 2026.
- **Autor:** Daniela Castaño.
- **Ramas / PR:** `test/HU-INFRA-01-idempotencia` → PR-15; `docs/HU-INFRA-02-sustentacion-p18` → PR-45.
- **Entregable:** E1.2 — infraestructura idempotente y E1.8 — documentación.
- **Objetivo:** demostrar que el bootstrap on-premise es repetible, cerrar la decisión de sustentación oral en lugar de video y conservar la evidencia documental sin duplicados.

**A. Insumo propio.** Revisión del flujo de aprovisionamiento y del pipeline. Se encontraron efectos laterales que rompían la idempotencia: sincronización incompleta de `.env`, reconstrucción innecesaria de Jenkins, credenciales del docente y reglas de Security Group. Se decidió documentar cada corrección y no tocar los pares `before`/`after` de `docs/infra/evidencia/idempotency/`.

**B. Apoyo IAG — prompt (transcripción de sesión, ortografía normalizada).** «Ejecuta el plan», «De ahora en adelante quiero solo docx» y «El video no aplica, usamos en cambio sustentación; acomoda la documentación y la bitácora de IAG».

**C. Validación y apreciación humana.** Segunda ejecución de `./infra/bootstrap/bootstrap-all.sh` con `exit=0` y `diffs.txt` de 0 líneas, registrada en `docs/infra/evidencia/idempotency/IDEMPOTENCIA.md`. Se documentó que el disparador real del pipeline era `cron('H/5 * * * *')` y no un webhook en ese momento, por lo que no se dejó una allowlist injustificada en el puerto 8080. La protección de `main` pasó de dos a un aprobador en Sprint 2. La decisión de sustentación oral sustituyó al video en P1.8 y se registró en el informe.

**D. Apreciación humana y reflexión.** La ausencia de video no puede resolverse escribiendo que «no aplica» sin una decisión trazable. La bitácora dejó claro el cambio de modalidad, y el informe final conserva la limitación de forma explícita en el apartado de presentación.

---

<a id="iag-daniela-29"></a>
### 2.9 Lecciones transversales y límites de verificación

1. **Evidencia antes que afirmación.** Las cifras de esta sección proceden de XML, logs, capturas o commits versionados. Las mediciones intermedias se marcan como históricas y no se presentan como resultado final cuando existe un consolidado posterior.
2. **Prompts como insumo, no como prueba.** La salida de IAG se contrastó siempre con una ejecución local, un reporte publicado o un artefacto del repositorio. Los prompts reconstruidos se identifican como tales.
3. **Separación de responsabilidades.** Las intervenciones de dashboard-service se registran en las secciones 5, 6 y 7; esta sección solo las referencia. El rollback on-premise documentado en la sección 7 se apoya en el transcript restaurado `docs/informe/evidencia/HU-DASH-04/prod/rollback-commands.txt`.
4. **Límites reales.** Siguen pendientes de ejecución o de material externo: Locust y ZAP en stage, la traza Jaeger de extremo a extremo, la grabación de la sustentación, el certificado definitivo con DNS y exposición en 443, la cifra real de facturación y una notificación de fallo adicional a la captura de GitHub ya restaurada. Estos puntos se declaran como pendientes en el informe final; no se han inventado evidencias para cerrarlos.

---

## 3. Módulo: `circleguard-form-service` (Santiago Estrada)

> **Nota de trazabilidad.** Las intervenciones de esta sección corresponden a los commits
> `0aae510`, `5357e26`, `60b5641`, `bce8b8f`, `8382948`, `7d81fde`, `e29d6a7` y `d404769`
> del repositorio (PR #2, PR #3 y PR #34), más los aportes documentales `9331977` y
> `9c69a0c`. Los prompts se reconstruyeron a partir de los artefactos entregados y se
> contrastaron contra el código y los archivos finales; no se transcribe ninguna salida que
> no esté respaldada por el repositorio.

### 3.1 HU-FORM-01 — Documentar el funcionamiento y las dependencias de `form-service`

- **Fecha:** 4–5 de noviembre de 2026
- **Autor:** Santiago Alejandro Estrada Martínez
- **Commits:** `0aae510` (estructura y dependencias) y `5357e26` (flujo de integración), PR #2
- **Objetivo de la interacción:** Producir `services/circleguard-form-service/FORM-SERVICE.md` con la descripción funcional, la organización de paquetes, las rutas HTTP, las migraciones y los puntos de integración del servicio.

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

Antes de redactar, se recorrió el módulo `services/circleguard-form-service/` y se levantó el siguiente inventario:

1. **Paquetes:** `controller`, `service`, `repository` y `model`, con `FormApplication.java` como punto de entrada Spring Boot y las migraciones en `src/main/resources/db/migration/`.
2. **Controladores y rutas:** `QuestionnaireController` (`/api/v1/questionnaires`), `HealthSurveyController` (`/api/v1/surveys`), `AttachmentController` (`/api/v1/attachments`) y `CertificateValidationController` (`/api/v1/certificates`), con ocho rutas en total.
3. **Servicios:** `QuestionnaireService`, `HealthSurveyService`, `SymptomMapper` y `StorageService`.
4. **Modelo de datos:** `Questionnaire`, `Question`, `HealthSurvey`, `QuestionType` y `ValidationStatus`; `HealthSurvey` guarda `anonymousId` y no contiene nombre ni correo.
5. **Migraciones:** `V1` a `V5`, incluida la columna `responses` en JSONB y el estado de validación.
6. **Integraciones:** PostgreSQL (`circleguard_form`) y Kafka con dos publicaciones: `survey.submitted` al guardar una encuesta y `certificate.validated` al aprobarla; `promotion-service` las consume en `SurveyListener` y no existe llamada HTTP directa entre ambos servicios.

#### B. Prompt empleado (reconstruido y contrastado)

```text
Estoy documentando HU-FORM-01 del proyecto CircleGuard. Necesito el README de
`services/circleguard-form-service/` con esta información que ya levanté del código:

- Paquetes: controller, service, repository, model; entrada en FormApplication.
- Controladores: QuestionnaireController (/api/v1/questionnaires), HealthSurveyController
  (/api/v1/surveys), AttachmentController (/api/v1/attachments) y
  CertificateValidationController (/api/v1/certificates).
- Servicios: QuestionnaireService, HealthSurveyService, SymptomMapper, StorageService.
- Entidades: Questionnaire, Question, HealthSurvey (con anonymousId, sin datos personales),
  QuestionType y ValidationStatus.
- Migraciones V1 a V5: esquema inicial, cuestionarios dinámicos, responses JSONB, adjunto y
  estado de validación.
- Integraciones: PostgreSQL (circleguard_form) y Kafka con los temas survey.submitted y
  certificate.validated; promotion-service los consume en SurveyListener.

Redacta el README en español con: cómo funciona el servicio, un diagrama Mermaid del flujo
cliente -> controladores -> servicios -> PostgreSQL/Kafka -> promotion-service, tabla de
controladores y rutas, tabla de servicios, repositorios y modelos, tabla de migraciones y
una sección de dependencias que explique la asincronía de Kafka.

Reglas: no inventes rutas ni temas; si algo no está en el insumo, déjalo registrado para
verificar. Usa los nombres reales de clases y archivos y no incluyas credenciales.
```

#### C. Validación humana y hallazgos

- Se contrastó cada ruta del README contra las anotaciones `@RequestMapping`, `@GetMapping` y `@PostMapping` de los cuatro controladores; las ocho rutas documentadas existen.
- Se verificó que `HealthSurveyService` publica en `survey.submitted` y `certificate.validated` y que `promotion-service` los consume con `@KafkaListener` en `SurveyListener`; no hay cliente HTTP hacia promotion.
- Se añadió explícitamente que la respuesta HTTP de `form-service` no confirma el procesamiento del evento, porque la comunicación es asíncrona. Ese matiz no estaba en el primer borrador y evita una promesa falsa en la documentación.
- Se revisó que el README no incluyera cadenas de conexión ni secretos; solo nombres de variables y de bases de datos.

#### D. Reflexión crítica

El valor de la IAG aquí no fue redactar: fue ordenar un inventario que ya existía y obligar a
explicitar el contrato de eventos. El hallazgo importante fue la asincronía: documentar
`survey.submitted` como si fuera una llamada síncrona habría ocultado el hecho de que una
encuesta puede guardarse correctamente y aun así no reflejarse de inmediato en el estado de
salud del usuario. Ese contrato es justamente el que después se verificó con un broker real
en HU-FORM-02.

---

### 3.2 HU-FORM-02 — Automatizar las pruebas y la cobertura de `form-service`

- **Fecha:** 5–6 de noviembre de 2026
- **Autor:** Santiago Alejandro Estrada Martínez
- **Commits:** `60b5641` (pruebas unitarias), `bce8b8f` (JaCoCo), `8382948` (integración Kafka) y `7d81fde` (contexto con PostgreSQL), PR #3
- **Objetivo de la interacción:** Llevar `form-service` al umbral de cobertura del entregable E1.6 (≥ 60 % de líneas), agregar una prueba de integración del flujo Form → Kafka → Promotion y dejar el reporte disponible para Sonar y Jenkins.

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

1. **Estado inicial:** la suite era mínima (`SymptomMapperTest` y pruebas de controlador incompletas) y `build.gradle.kts` no tenía JaCoCo ni una tarea separada de integración.
2. **Casos diseñados por componente:**
   - `HealthSurveyService`: guardado de encuesta con y sin síntomas, consulta de encuestas en espera, validación aprobada/rechazada y publicación de los dos eventos.
   - `QuestionnaireService`: listado, obtención del cuestionario activo, creación y activación.
   - `SymptomMapper`: preguntas de tipo booleano, selección y texto libre, con respuestas vacías o nulas.
   - `CertificateValidationController`, `HealthSurveyController` y `QuestionnaireController`: códigos 200/201/400/404 y cuerpos de respuesta.
3. **Integración:** publicar una encuesta con síntomas, consumir `survey.submitted` desde un broker temporal y comprobar que `SurveyListener` de promotion invoca el cambio a `SUSPECT`.
4. **Restricción técnica detectada:** incluir el módulo de promotion en el classpath de pruebas arrastraba su `application.yml`, sus migraciones y su cadena de seguridad, lo que rompía el contexto de Form.

#### B. Prompt empleado (reconstruido y contrastado)

```text
Necesito completar HU-FORM-02 de CircleGuard en `services/circleguard-form-service/`.
Estado actual: la suite es mínima y el build no tiene JaCoCo ni tarea de integración.

Objetivo:
1. Configurar JaCoCo con reporte XML y HTML y verificación de cobertura de líneas >= 0.60
   en la tarea check.
2. Crear una tarea `integrationTest` que ejecute solo las pruebas con @Tag("integration") y
   excluya ese tag de `test`.
3. Ampliar las pruebas unitarias de HealthSurveyService, QuestionnaireService, SymptomMapper
   y los controladores de encuestas, cuestionarios y certificados.
4. Escribir una prueba de integración con Testcontainers que publique una encuesta con
   síntomas en Kafka, lea el tema survey.submitted y verifique que el SurveyListener de
   promotion-service solicita el estado SUSPECT.

Restricciones:
- No agregar dependencias de seguridad de promotion al contexto de Form.
- Evitar que promotion aporte su application.yml y sus migraciones al classpath de pruebas.
- La prueba de adjuntos requiere PostgreSQL real: déjala con @Tag("integration").
- No inventes endpoints ni temas; usa los que están en el código.
Explica qué archivos tocas y por qué.
```

#### C. Validación humana y hallazgos

- **Cobertura real medida:** 72.45 % de líneas y 84.21 % de ramas en `jacocoTestReport.xml`, por encima del umbral de 0.60 configurado en `jacocoTestCoverageVerification`.
- **Pruebas:** 33 pruebas unitarias en seis clases (`HealthSurveyServiceTest`, `QuestionnaireServiceTest`, `SymptomMapperTest`, `CertificateValidationControllerTest`, `HealthSurveyControllerTest` y `QuestionnaireControllerTest`) más dos de integración (`FormPromotionKafkaIntegrationTest` y `AttachmentControllerTest`).
- **Aislamiento del classpath:** se excluyó `spring-boot-starter-security` del módulo de promotion y se solicitó el artefacto con `LibraryElements.CLASSES` para no arrastrar `application.yml` ni migraciones. Sin ese ajuste el contexto de Form fallaba al arrancar.
- **Kafka:** la prueba usa `KafkaContainer`, crea el tema `survey.submitted` y espera con `Mockito.timeout` la invocación sobre `HealthStatusService`; se validó que el test falla si se altera el nombre del tema.
- **Adjuntos:** `AttachmentControllerTest` se movió a `@Tag("integration")` porque necesita `PostgreSQLContainer`; así la suite unitaria sigue corriendo sin Docker.

#### D. Reflexión crítica

La prueba de integración fue el punto donde la IAG aportó menos código y más criterio: la
primera versión “funcionaba” en local porque el contexto levantaba con el `application.yml`
de promotion, es decir, ocultaba un acoplamiento. Excluir seguridad y pedir solo las clases
compiladas hizo visible el problema real y dejó una prueba que mide el contrato de eventos y
no la conveniencia del entorno. La lección es que una prueba de integración debe aislar el
módulo bajo prueba; si para pasar necesita la configuración del vecino, no está probando el
vecino, lo está sustituyendo.

---

### 3.3 HU-FORM-03 — Empaquetar `form-service` e integrarlo al pipeline

- **Fecha:** 8 de noviembre de 2026
- **Autor:** Santiago Alejandro Estrada Martínez
- **Commits:** `e29d6a7` (imagen multi-stage no-root) y `d404769` (integración al pipeline), PR #34
- **Objetivo de la interacción:** Construir la imagen inmutable de `form-service`, externalizar su configuración y registrarlo en el catálogo del pipeline compartido para que se construya, publique y despliegue como los demás núcleos.

#### A. Contenido propio entregado a la IAG (Insumo previo del estudiante)

1. **Requisitos de la imagen:** multi-stage, base fijada por digest, usuario sin privilegios, `HEALTHCHECK` contra `/actuator/health`, puerto `8086` y directorio de adjuntos escribible.
2. **Contexto de build:** el módulo depende de `settings.gradle.kts` y de otros módulos, por lo que el contexto no puede limitarse a la carpeta del servicio; se necesitaba un `.dockerignore` específico.
3. **Configuración:** `application.yml` debía tomar PostgreSQL y Kafka de variables de entorno para funcionar en dev, stage y prod.
4. **Pipeline:** el catálogo de servicios (`jenkins/vars/ciServices.groovy`) debía incluir form con sus tareas de test, integración, publicación y despliegue, y el `Jenkinsfile` debía seleccionarlo igual que a los demás.

#### B. Prompt empleado (reconstruido y contrastado)

```text
Necesito empaquetar `circleguard-form-service` para HU-FORM-03 y conectarlo al pipeline.

Contexto que ya definí:
- Imagen multi-stage: compilar con Gradle 8.14 y JDK 21, ejecutar con JRE 21.
- Fijar las imágenes base por digest.
- Usuario no root (UID 10001), directorio /tmp/circleguard-uploads escribible.
- EXPOSE 8086 y HEALTHCHECK con wget contra /actuator/health.
- El build necesita settings.gradle.kts y los módulos, así que no puedo copiar solo el
  servicio: ayúdame con un Dockerfile.dockerignore que excluya pruebas, reportes y basura
  sin romper la compilación.
- application.yml debe leer PostgreSQL y Kafka de variables de entorno.

Además, intégralo al pipeline compartido: revisa Jenkinsfile y jenkins/services.groovy,
agrega la entrada de form y documenta el catálogo en docs/infra/pipeline-servicios.md.
Incluye pruebas Groovy del Jenkinsfile si sirven para evitar regresiones en la selección de
servicios.
```

> **Nota de actualización:** las rutas `jenkins/services.groovy`, `docs/infra/pipeline-servicios.md`
> y `tests/jenkins/` corresponden a la nomenclatura usada en el prompt original. En el árbol
> vigente, el catálogo es `jenkins/vars/ciServices.groovy`, la guía es `docs/infra/ci-cd-setup.md`
> y la prueba es `jenkins/test/TestCiServices.groovy`.

#### C. Validación humana y hallazgos

- El `Dockerfile` final usa `gradle:8.14-jdk21` y `eclipse-temurin:21-jre-jammy` fijadas por digest, ejecuta `bootJar` como usuario `gradle`, copia solo el JAR a la etapa de runtime, corre como `10001:10001` y declara `HEALTHCHECK` con `wget -qO- http://127.0.0.1:8086/actuator/health`.
- `Dockerfile.dockerignore` recorta el contexto sin excluir `settings.gradle.kts` ni los módulos requeridos; se comprobó que el build falla si se elimina esa configuración.
- Se externalizaron las credenciales de PostgreSQL y la dirección de Kafka en `application.yml`; el servicio dejó de depender de valores locales.
- En el pipeline se añadió la entrada de form en `jenkins/vars/ciServices.groovy` y se documentó el catálogo en `docs/infra/ci-cd-setup.md`. La prueba `jenkins/test/TestCiServices.groovy` valida la selección de servicios.
- La verificación de runtime queda cubierta por las etapas de publicación, despliegue y E2E del pipeline, que consumen la misma imagen y el mismo healthcheck.

#### D. Reflexión crítica

La parte menos visible y más delicada fue el contexto de build. Un `.dockerignore` demasiado
agresivo produce imágenes que compilan en local y fallan en Jenkins, y uno demasiado laxo
mete pruebas y reportes en la imagen. Fijar las bases por digest también tiene costo: hay que
actualizarlas a mano cuando aparece un parche de seguridad, pero a cambio el artefacto es
reproducible y la auditoría de Trivy en la Parte 2 puede comparar siempre la misma base. La
lección es que “imagen inmutable” no significa solo etiquetar con semver: significa que el
contenido, el usuario y el healthcheck estén declarados en el repositorio y no en la cabeza
de quien despliega.

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

---

<a id="hu-dash-03"></a>
## 5. Módulo: `circleguard-dashboard-service` e integración CI/CD (HU-DASH-03)

- **Fecha:** 22 de noviembre de 2026
- **Nivel de uso:** 3 (colaboración con IAG).
- **Herramienta:** OpenCode / Big Pickle.
- **Historia / Entregable:** `HU-DASH-03` — E1.3, E1.4, E1.5, E1.6 y E1.7.
- **Rama base:** `main` (`1a9199d`).

### A. Contenido propio entregado a la IAG

1. **Insumo previo:** se revisaron `Proyecto_Final_CircleGuard.tex` (E1.3-E1.7 y la nota de alcance de la Parte 1, que excluye a dashboard del despliegue), el diagnóstico previo de `dashboard-service` y el registro histórico de 19 pruebas con 97,3 % de cobertura (commits `bdf2a14` y predecesores; documentos no versionados en HEAD), `Jenkinsfile`, `jenkins/vars/*.groovy`, `gradle/jacoco-convention.gradle` y los Dockerfile de los otros servicios.
2. **Contraste con el código:** se determinó que multi-stage, usuario no root, catálogo del Shared Library, quality gate, semver y archivado de reportes ya existían; las brechas reales eran los metadatos OCI no usados, el healthcheck no verificado para dashboard y la falta de evidencia documental del empaquetado.
3. **Decisión de diseño:** alinear el Dockerfile con el patrón de `file-service` (UID/GID fijo 10001, `COPY --chown`, cachés BuildKit y labels), mantener `deploy: false` en Parte 1 y añadir `dashboard` a la verificación de healthcheck tanto en el catálogo como en el override local del Jenkinsfile.
4. **Restricción respetada:** no se inventaron evidencias de Jenkins/SonarQube remotas; el informe distingue la verificación local ejecutada de la corrida que debe hacerse en el servidor con `SERVICE=dashboard`.

### B. Prompts empleados

```text
Contrasta con el enunciado del proyecto final para hacer esta HU:
Historia de usuario

Como integrante del equipo DevOps de CircleGuard, quiero contenerizar dashboard-service e integrarlo al pipeline CI/CD compartido, para automatizar su compilación, validación y publicación como una imagen Docker versionada y reproducible.

Dependencias
HU-DASH-01, HU-DASH-02 y pipeline Jenkins compartido

Subtareas previstas

Crear un Dockerfile multi-stage que compile el microservicio utilizando Gradle y genere una imagen de ejecución.
Configurar la imagen para que el servicio se ejecute mediante un usuario no root.
Integrar dashboard-service al Jenkinsfile y a las etapas reutilizables de la Shared Library del proyecto.
Configurar la ejecución automática de compilación, pruebas unitarias, cobertura con JaCoCo y análisis estático con SonarQube.
Verificar que el quality gate de SonarQube y las pruebas sean obligatorias.
Integrar la publicación de la imagen en el registro de artefactos compartido, utilizando versionado semántico automático basado en tags Git y Conventional Commits.
Verificar que los reportes y resultados del servicio queden disponibles en Jenkins.

Criterios de aceptación

El Dockerfile permite construir una imagen reproducible de dashboard-service mediante una construcción multi-stage.
El contenedor inicia correctamente y ejecuta la aplicación con un usuario no root.
Jenkins ejecuta automáticamente las validaciones del servicio mediante el pipeline compartido.
Los resultados de pruebas, cobertura y análisis estático pueden consultarse desde Jenkins y SonarQube.
Un fallo en las pruebas o en el quality gate bloquea la publicación de la imagen.
Las imágenes de release se publican en el registro con una versión semántica trazable a un tag Git, sin utilizar latest.
La imagen publicada queda disponible para su posterior despliegue en la Parte 2.
Subtareas:
Crear Dockerfile multi-stage con compilación Gradle.
Configurar ejecución del contenedor con usuario no root.
Integrar dashboard-service al Jenkinsfile y Shared Library compartida.
Integrar compilación, pruebas, JaCoCo y SonarQube en CI.
Configurar pruebas y quality gate obligatorios.
Publicar imagen Docker versionada con semver, Git tags y Conventional Commits.
Verificar publicación de reportes y resultados en Jenkins.
```

```text
Ejecuta el plan
```

### C. Validación humana y hallazgos

- `./gradlew :services:circleguard-dashboard-service:clean :...:check` terminó en `BUILD SUCCESSFUL`: 19 tests, 0 fallos, 0 omitidos; JaCoCo 73/75 líneas (97.33 %) y `verifyUnitTestsRan` sin hallazgos.
- `jenkins/test/TestCiServices.groovy` terminó con `ALL CASES PASSED` tras modificar el catálogo.
- `docker build` con los `--build-arg` de versión/commit/source terminó correctamente; la imagen resultante ejecuta `id -u` como `10001`, declara `HEALTHCHECK` en 8084, lleva los labels OCI con la revisión `1a9199d...` y no genera tag `latest`.
- Se revisó el `git diff` de los cinco archivos tocados y se corrigió una redacción del informe antes de cerrarlo.

### D. Reflexión crítica

La IAG ayudó a convertir una HU aparentemente nueva en una brecha acotada: reescribir todo el pipeline habría duplicado trabajo ya cubierto y habría introducido riesgo. El punto delicado fue la distinción entre "imagen que compila" e "imagen auditable": sin los labels y el UID fijo, la imagen pasaría los checks funcionales pero no dejaría rastro del commit ni del tag que la originó. También quedó claro que `Jenkinsfile` y la librería comparten responsabilidades de forma intencional: el override local mantiene el PR verde mientras la librería fijada a `main` no incorpora el cambio, y el catálogo compartido lo vuelve permanente tras el merge. Finalmente, no falsificar enlaces de Jenkins o SonarQube evita que la evidencia parezca verificada cuando solo está configurada.

### E. Registro de la corrida remota (23 de noviembre de 2026)

- Con credenciales de administrador de Jenkins se consultó la API y se lanzó `Build with Parameters` sobre `main` con `SERVICE=circleguard-dashboard-service`, `TAG` vacío y `ROLLBACK_TAG` vacío.
- El PR #163 quedó mergeado en `main` (`cbe6cf7`). El build `PR-163/1` terminó en verde, pero el detector de cambios lo clasificó como `CI-only` y omitió Unit Tests, JaCoCo y Sonar.
- El build `main #53` terminó en `FAILURE` en `Terraform Plan`: falta la credencial Jenkins `terraform-minio-creds`. En consecuencia no se publicó imagen ni se generaron `image-digest.txt` o `sonar-links.txt` con contenido.
- La IAG dejó el registro honesto en `docs/informe/evidencia/HU-DASH-03/README.md`, junto con las consolas completas y los pasos para crear las credenciales faltantes y repetir la corrida.

---

<a id="hu-dash-05"></a>
## 6. Módulo: `circleguard-dashboard-service` — observabilidad y seguridad (HU-DASH-05)

- **Fecha:** 23 de noviembre de 2026.
- **Nivel de uso:** 3 (colaboración con IAG).
- **Herramienta:** OpenCode / Big Pickle.
- **Historia / Entregable:** `HU-DASH-05` — E2.2, E2.3, E2.5 y E2.6.
- **Rama base:** `main` (`526219b`, igual que `origin/main`).
- **Rama de trabajo:** `feat/HU-DASH-05-integrar-observabilidad-y-seguridad`.

### A. Contenido propio entregado a la IAG

1. **Insumo previo:** se contrastó la HU con `Proyecto_Final_CircleGuard.tex` (E2.2, E2.3, E2.5, E2.6 y la estructura sugerida `observability/`) y con el estado real de `main`: `dashboard-service` ya tenía Actuator y `HEALTHCHECK`, pero no `micrometer-registry-prometheus`, ni bloque `management:`, ni anotaciones de scrape, ni alertas, ni panel, ni instrumentación del cliente HTTP, ni evidencia Trivy propia.
2. **Restricciones dadas por el estudiante:** la rama debía crearse desde `main` tras `git pull --ff-only`; no se incluirían trazas/OTEL en dashboard porque el enunciado reserva Jaeger para `form → Kafka → promotion → notification`; la integración con monitoring debía seguir lo correcto según el enunciado sin duplicar el stack real de `deploy/k8s/monitoring/`; las credenciales remotas se proveerían por separado, sin pegar secretos en el chat.
3. **Decisión de diseño:** reutilizar `components/form-observability` como plantilla para un componente `dashboard-observability`, con panel de siete fuentes y dos reglas probadas con `promtool`; instrumentar solo métricas y logs con tags acotados; no tocar umbrales del `Jenkinsfile`. Las versiones de dependencias se actualizaron después por petición explícita del estudiante, sin introducir excepciones ni rebajar el gate.
4. **Evidencia diferenciada:** se registró por separado lo ejecutado en local (Gradle, promtool, Kustomize, Trivy) y lo pendiente en clúster/Jenkins (scrape real, panel con datos, Loki, entrega de alertas y build remoto).

### B. Prompts empleados

```text
Necesito hacer esta HU, contraste con el main y el enunciado del proyecto final

Historia de usuario

Como integrante del equipo DevOps de CircleGuard, quiero incorporar métricas, registros y controles de seguridad a dashboard-service, para monitorear su funcionamiento, detectar fallos de comunicación y verificar la seguridad de los artefactos entregados.

Dependencias
HU-DASH-04 y herramientas compartidas de observabilidad y seguridad

Subtareas previstas

Incorporar Spring Boot Actuator y Micrometer para exponer métricas del servicio en formato Prometheus.
Integrar las métricas del servicio con la instancia de Prometheus compartida del proyecto.
Crear un panel en Grafana que permita visualizar estado del servicio, solicitudes, latencia y errores.
Configurar los registros del servicio para que sean enviados al sistema centralizado de logs.
Añadir métricas y registros de errores y demoras en la comunicación con promotion-service.
Configurar al menos una alerta de disponibilidad o errores usando el sistema de alertas compartido.
Integrar el análisis de seguridad de la imagen y la configuración del servicio con Trivy.
Verificar los resultados de los escaneos y asegurar que las vulnerabilidades sobre el umbral bloqueen la entrega.

Criterios de aceptación

Prometheus recoge correctamente las métricas de dashboard-service.
El panel de Grafana muestra información útil y funcional del servicio.
Los logs del servicio pueden consultarse de forma centralizada.
Es posible identificar fallos o demoras en la comunicación con promotion-service.
La alerta configurada se dispara y se puede probar con el sistema compartido.
Los escaneos de imagen y configuración se ejecutan desde el pipeline compartido y sus resultados son verificables.
Las vulnerabilidades que superen el umbral bloquean la entrega del artefacto.
```

```text
La rama donde vas a implementar se llama feat/HU-DASH-05-integrar-observabilidad-y-seguridad. Debes hacer pull primero.
```

```text
Decisiones: solo métricas y logs, sin OTel/Jaeger en dashboard. Para la integración con monitoring, lo que sea correcto según el enunciado del proyecto final. Proveeré credenciales para la validación remota; no pegar secretos en el chat.
```

```text
Sí, ya lo desactivé.
```

La última respuesta corresponde a la autorización para salir del modo Plan y aplicar los cambios en el repositorio.

```text
Arregla lo de seguridad
```

```text
Boot 4.0.10 global (Recommended)
Misma rama HU-DASH-05 (Recommended)
```

```text
Ejecuta el plan
```

```text
Haz merge de main aqui y dime que falta para terminar tu HU
```

```text
Actualiza con main nuevo
```

```text
Haz commit push y PR en ingles si la HU esta lista
Dame el plan completo
Pasate a la rama feat/HU-DASH-05-integrar-observabilidad-y-seguridad
Existe?
Ya esta mergeada?
Haz merge con main y dame el plan compelto para terminar esta HU
Ejecuta el plan
```

```text
Haz un sonido cuando termines
```

Estos prompts posteriores pidieron, en orden: remediar los CRITICAL de Trivy mediante una actualización global a Spring Boot 4.0.10 en la misma rama; mantener la rama sincronizada con `main` en varios puntos; preparar el plan completo de cierre; y avisar con una campana al terminar el rescan de Trivy ejecutado en segundo plano.

### C. Validación humana y hallazgos

- `./gradlew :services:circleguard-dashboard-service:check --no-daemon`: 23 pruebas, 0 fallos, JaCoCo 92/94 líneas (97,87 %) y `verifyUnitTestsRan` sin hallazgos. La primera versión de `PromotionClientMetricsTest` falló porque dos `when(...)` sobre la misma llamada sobrescribían el stub; se corrigió encadenando `thenReturn(...).thenThrow(...)`.
- `promtool check rules` y `promtool test rules` pasaron para las dos reglas; `verify.py` de dashboard y de Form pasaron después de separar el `monitoring-patch.yml` de Form en dos documentos (Kustomize 5.0.4 rechazaba el archivo multi-doc).
- Trivy IaC sobre `deploy/k8s`: 31 archivos, 0 CRITICAL / 74 HIGH tras fusionar `main` (el archivo adicional es `dashboard-config.yml` de HU-DASH-04). El primer Trivy de imagen (antes de la remediación) reportó 9 CRITICAL y 35 HIGH; al revisar `Jenkinsfile:328-347` se confirmó que el gate cuenta CRITICAL con `jq` y no usa `--exit-code 1` en `trivy image`, por lo que ese build habría sido bloqueado. Por petición del estudiante se actualizó la plataforma compartida a Spring Boot `4.0.10` (Kotlin `2.2.21`, Resilience4j `2.4.0`, starters de test separados, Jackson 2 y WebClient explícitos) sin tocar el umbral ni añadir excepciones. El rescan con Trivy `0.57.1` (versión de Jenkins) y `0.70.0` sobre la imagen reconstruida terminó con **0 HIGH / 0 CRITICAL** en ambos casos y quedó registrado en `docs/informe/evidencia/HU-DASH-05/` (`trivy-image-0.57.1.json` y `trivy-image-0.70.0.json`); la corrida oficial en Jenkins sigue como pendiente remoto.
- Se revisaron los JSON de evidencia, el `git diff` y los enlaces del informe. No se crearon capturas ni enlaces remotos falsos.

### D. Reflexión crítica

La IAG tendió inicialmente a proponer infraestructura nueva (otro colector, otro panel global, un gate específico para dashboard). El contraste con el código mostró que casi todo ya existía y que el valor estaba en conectar el servicio al stack compartido y en dejar evidencia honesta. El hallazgo más delicado fue el resultado de Trivy: existía la tentación de reportar “escaneo ejecutado” como éxito, cuando el JSON demuestra 9 CRITICAL y el pipeline bloquearía la entrega. Registrar ese bloqueo y no cambiar el umbral preserva la credibilidad de la HU. También quedó claro que `promtool` y `verify.py` validan lógica y render, pero no prueban que Prometheus, Grafana, Loki o Alertmanager estén sirviendo datos reales; esa distinción quedó explicada en el informe y en la evidencia. Finalmente, separar el parche multi-doc de Form fue un efecto colateral inesperado pero necesario para que la integración de ambos componentes funcionara con la versión de Kustomize disponible. Cuando el estudiante pidió «Arregla lo de seguridad», la IAG evaluó rebajar el gate, añadir excepciones y actualizar dependencias; la decisión correcta fue esta última, pero de forma global y controlada (Boot `4.0.10` en los ocho servicios) para no dejar un servicio desalineado. La migración reveló cambios de API en Spring 7, Kotlin 2, Kafka 4, Resilience4j y Micrometer; cada fallo de test se corrigió en código o dependencias, nunca relajando una aserción de seguridad.

### E. Registro de la corrida remota

Pendiente. El usuario indicó que proveerá credenciales/acceso. Cuando se realice, debe registrarse aquí el build de Jenkins, el target de Prometheus, el panel con datos, la consulta Loki y el disparo/recepción/recuperación de la alerta. Mientras tanto, `docs/informe/HU-DASH-05.md` y su carpeta de evidencia solo reportan verificaciones locales.

## 7. Entrada: Ejecución de HU-DASH-04 (despliegue y rollback on-premise de dashboard-service)

### A. Solicitud del usuario

> Quiero implementar esta HU, contrastala con el enunciado del proyecto final:
> **Historia de usuario**: como integrante del equipo DevOps de CircleGuard, quiero desplegar `dashboard-service` en Kubernetes para los ambientes dev, stage y prod, de manera que el servicio pueda ejecutarse de forma controlada, configurable y recuperable dentro de la infraestructura de CircleGuard. **Dependencias**: HU-DASH-03 e infraestructura Kubernetes compartida. **Subtareas previstas**: crear los recursos de Kubernetes necesarios utilizando los mecanismos de configuración del equipo; externalizar la configuración de PostgreSQL y la URL de promotion-service mediante ConfigMaps y Secrets; incorporar Service e Ingress cuando sea necesario; integrar Spring Boot Actuator y sondas liveness/readiness; definir recursos y escalamiento mediante HPA; integrar el despliegue al pipeline compartido para dev, stage y prod usando la misma imagen versionada; verificar aprobación manual para producción; ejecutar y documentar una prueba de rollback. **Criterios de aceptación**: dashboard-service desplegado y operativo en los tres ambientes; configuración externalizada sin credenciales en el repositorio; sondas que identifiquen el estado del servicio; HPA y acceso según la arquitectura; promoción de la misma imagen versionada; aprobación manual en producción; rollback funcional y documentado.

> 1.Si 2.Si 3. Ya se llama asi

> Para el paso 4, ssh docker@10.147.20.67, pwd:****

> Ejecuta el plan

> No voy a usar cloud

> Ajusta todo a on prem, revisa main

> Haz un sonido cuado termines

> No esperes flujos de jenkins, lo hago yo

> No es que no dispares es que no te quedes wathc

> Te falta mucho?

> Termina

### B. Instrucciones y actuación de la IAG

1. Contrasté la HU con el enunciado: jerarquía Parte 1/Parte 2, E1.5, E2.2,
   E2.3, E2.4, E2.5, E2.6, E2.8 y E2.9. Concluí que no era trabajo desde cero
   porque ya existían Deployment, Service, overlays y `ci.deploy()` con
   `dashboard`, y enumeré las brechas reales.
2. Propuse un plan por bloques (servicio, K8s, pipeline, rollback, documentación
   y verificación) y recogí las decisiones del usuario: rollback real en
   producción, mínimo de Prometheus e ID `HU-DASH-04`.
3. Revisé `main`, `origin/main` y la rama de trabajo (todos en `cbe6cf7`) antes
   de tocar nada. Tras el aviso "No voy a usar cloud", eliminé la comparativa
   EKS/GKE del README de K8s y sustituí la etiqueta OCI `SOURCE_URL` de GitHub
   por `on-premise://10.147.20.67/cg-main-tools`; no se aprovisionó ningún
   recurso cloud.
4. Implementé los cambios en el servicio, los manifiestos base y overlays, el
   catálogo del pipeline y la documentación. Copié el árbol al host on-prem en
   `/tmp/hu-dash-04` sin sobrescribir el checkout ajeno `/home/docker/cg-main-tools`.
5. Regeneré los `secrets.env` desde los Secret ya existentes en cada namespace
   antes de aplicar los overlays, para no introducir credenciales del repo.
6. Corregí la interpretación del mensaje sobre Jenkins: el usuario no prohibía
   disparar flujos, sino evitar comandos de seguimiento de espera activa. Sustituí
   `rollout status` por consultas puntuales en los scripts de despliegue y
   captura de evidencia.
7. Ejecuté el rollback real en `cg-prod` y preparé la documentación final con
   las rutas de evidencia y el sonido de terminación solicitado.

### C. Validación humana y hallazgos

- `./gradlew :services:circleguard-dashboard-service:check --no-daemon --console=plain`: `BUILD SUCCESSFUL`, 20 tests, 0 fallos, 0 omitidos; JaCoCo 73/75 líneas (97.33 %).
- Script de aserciones estáticas sobre manifiestos: `ALL STATIC CHECKS PASSED` (sin secretos hardcodeados, HPA y ruta de Ingress presentes, tag único).
- `jenkins/test/TestCiServices.groovy` con los jars Groovy de Gradle: `ALL CASES PASSED` tras marcar `dashboard.deploy = true`.
- Construcción de la imagen `circleguard-docker/dashboard:1.10.0` en el host on-prem con imágenes base del mirror local e importación directa al nodo servidor de k3d; el push a `localhost:5000` dio *timeout* HTTPS y no fue necesario para el clúster local.
- Aplicación de los overlays dev/stage/prod y captura de evidencia puntual por ambiente; rollback ejecutado en `cg-prod` de `1.10.0` a `1.4.0` con captura de `rollout history`, pods, sondas y endpoint analytics.
- La credencial SSH facilitada por el usuario se usó solo en memoria mediante `SSH_ASKPASS`; no quedó registrada en el repositorio, los informes ni la evidencia.

### D. Reflexión crítica

El mayor riesgo era confundir "la HU ya existe en el repo" con "la HU ya está terminada": el despliegue estaba declarado pero no era funcional (URL de promotion apuntando a `localhost`, sondas genéricas, sin HPA ni Ingress y rollback nunca ejecutado). También fue importante no inventar aprobaciones de Jenkins: la compuerta `input` ya existía y se verificó por código, sin simular una corrida interactiva. El aviso sobre no quedarse en `watch` obligó a separar "disparar una acción" de "esperar pasivamente su stream", dejando comandos acotados y verificaciones puntuales. Finalmente, el requisito on-premise obligó a que la narrativa de la imagen cambiara de GitHub a una etiqueta local verificable, manteniendo la trazabilidad semver sin depender de la nube.

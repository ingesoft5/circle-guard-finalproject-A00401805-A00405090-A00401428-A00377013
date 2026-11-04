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

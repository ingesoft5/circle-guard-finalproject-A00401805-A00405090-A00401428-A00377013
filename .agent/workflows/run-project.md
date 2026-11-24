<!-- CG-DOC: Documentación Markdown: run-project. -->
---
description: Cómo ejecutar el proyecto CircleGuard localmente
---

Este flujo de trabajo te guía para lanzar el ecosistema completo de CircleGuard, incluida la infraestructura, los microservicios y el frontend móvil.

### 1. Configuración de la infraestructura (Docker)
Asegúrate de que Docker Desktop esté ejecutándose en Windows y integrado con WSL.

// turbo
1. Navega hasta la raíz del monorepo:
   `cd <repo-root>`

// turbo
2. Inicia la infraestructura (PostgreSQL, Neo4j, Kafka, Redis, LDAP, MinIO):
   `docker compose --env-file deploy/compose/dev/.env -f deploy/compose/dev/docker-compose.yml up -d`

### 2. Servicios del backend (Spring Boot)
Asegúrate de que el wrapper de Gradle tenga permisos de ejecución:
`chmod +x gradlew`

En un monorepo debes especificar qué servicio se va a ejecutar. Lánzalos en terminales separadas:

// turbo
- **Servicio de autenticación:** `./gradlew :services:circleguard-auth-service:bootRun`
- **Servicio de identidad:** `./gradlew :services:circleguard-identity-service:bootRun`
- **Servicio de promociones:** `./gradlew :services:circleguard-promotion-service:bootRun`
- **Servicio de formularios:** `./gradlew :services:circleguard-form-service:bootRun`
- **Servicio de notificaciones:** `./gradlew :services:circleguard-notification-service:bootRun`
- **Servicio gateway:** `./gradlew :services:circleguard-gateway-service:bootRun`
- **Servicio del panel:** `./gradlew :services:circleguard-dashboard-service:bootRun`
- **Servicio de archivos:** `./gradlew :services:circleguard-file-service:bootRun`

### 3. Frontend móvil (Expo)
Prepara y lanza la aplicación React Native.

1. Navega hasta el directorio móvil:
   `cd mobile`

2. Instala las dependencias:
   `npm install`

3. Inicia el servidor de desarrollo de Expo:
   `npx expo start`
   *Pulsa `w` para abrirlo en el navegador web o `a`/`i` para los simuladores de Android/iOS.*

### 4. Verificación
- **Gateway de API:** [http://localhost:8080](http://localhost:8080)
- **Navegador de Neo4j:** [http://localhost:7474](http://localhost:7474) (Inicio de sesión: `neo4j/password`)
- **API del panel:** [http://localhost:8085/api/v1/analytics/trends/{id}](http://localhost:8085/api/v1/analytics/trends/{id})

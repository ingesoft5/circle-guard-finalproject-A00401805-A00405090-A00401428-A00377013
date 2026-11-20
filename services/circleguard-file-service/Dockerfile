# CG-DOC: Multi-stage, non-root image for circleguard-file-service.
# Build context is the repository root:
#   docker build -f services/circleguard-file-service/Dockerfile .
# CG-DOC: Las imágenes base se sobreescriben desde Jenkins con --build-arg para usar
# el mirror de Nexus (circleguard-base/*) en el host on-premise (sin IPv6 a Docker Hub).
ARG GRADLE_IMAGE=gradle:8.14-jdk21
ARG JDK_IMAGE=eclipse-temurin:21-jdk
ARG ALPINE_IMAGE=eclipse-temurin:21-jre-alpine
ARG JAMMY_IMAGE=eclipse-temurin:21-jre-jammy
ARG NODE_IMAGE=node:22-alpine
ARG WEB_IMAGE=nginxinc/nginx-unprivileged:1.27-alpine
FROM ${GRADLE_IMAGE} AS build
WORKDIR /workspace

COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradlew lombok.config ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle services/circleguard-file-service ./services/circleguard-file-service

RUN ./gradlew :services:circleguard-file-service:bootJar --no-daemon --console=plain

FROM ${ALPINE_IMAGE}

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/services/circleguard-file-service/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8085/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

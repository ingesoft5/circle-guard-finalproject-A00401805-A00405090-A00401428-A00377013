# CG-DOC: Container image definition for the gateway service.
# CG-DOC: Las imágenes base se sobreescriben desde Jenkins con --build-arg para usar
# el mirror de Nexus (circleguard-base/*) en el host on-premise (sin IPv6 a Docker Hub).
ARG GRADLE_IMAGE=gradle:8.14-jdk21
ARG JDK_IMAGE=eclipse-temurin:21-jdk
ARG ALPINE_IMAGE=eclipse-temurin:21-jre-alpine
ARG JAMMY_IMAGE=eclipse-temurin:21-jre-jammy
ARG NODE_IMAGE=node:22-alpine
ARG WEB_IMAGE=nginxinc/nginx-unprivileged:1.27-alpine
FROM ${JDK_IMAGE} AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :services:circleguard-gateway-service:bootJar --no-daemon

FROM ${ALPINE_IMAGE}
RUN addgroup -S circleguard && adduser -S circleguard -G circleguard
WORKDIR /app
COPY --from=build /workspace/services/circleguard-gateway-service/build/libs/ /tmp/libs/
RUN cp "$(find /tmp/libs -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /app/app.jar \
    && chown -R circleguard:circleguard /app
USER circleguard
EXPOSE 8087
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=5 \
  CMD wget -qO- http://localhost:8087/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

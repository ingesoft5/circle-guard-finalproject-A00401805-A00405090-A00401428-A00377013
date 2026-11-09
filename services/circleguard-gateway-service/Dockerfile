# CG-DOC: Multi-stage, non-root image definition for the gateway service.
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace

COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradlew ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle services/circleguard-gateway-service ./services/circleguard-gateway-service

RUN ./gradlew :services:circleguard-gateway-service:bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-alpine

ARG VERSION=dev
LABEL org.opencontainers.image.title="circleguard-gateway-service" \
      org.opencontainers.image.description="CircleGuard campus access gateway" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.source="https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013" \
      org.opencontainers.image.revision="${VERSION}"

RUN addgroup -S -g 10001 circleguard \
    && adduser -S -D -H -u 10001 -G circleguard circleguard

WORKDIR /app

COPY --from=build /workspace/services/circleguard-gateway-service/build/libs/ /tmp/libs/
RUN cp "$(find /tmp/libs -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /app/app.jar \
    && rm -rf /tmp/libs \
    && chown -R circleguard:circleguard /app

USER circleguard
EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8087/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

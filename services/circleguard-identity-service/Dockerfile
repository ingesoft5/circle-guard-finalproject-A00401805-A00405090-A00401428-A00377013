# CG-DOC: Immutable multi-stage container image for the identity service.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY services ./services
RUN chmod +x gradlew \
    && ./gradlew :services:circleguard-identity-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG VERSION=dev
ARG GIT_COMMIT=unknown
ARG SOURCE_URL=unknown
LABEL org.opencontainers.image.title="circleguard-identity-service" \
      org.opencontainers.image.description="CircleGuard identity vault service" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.source="${SOURCE_URL}"

RUN addgroup -S circleguard && adduser -S -G circleguard circleguard
WORKDIR /app
COPY --from=build /workspace/services/circleguard-identity-service/build/libs/ /tmp/libs/
RUN cp "$(find /tmp/libs -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /app/app.jar \
    && chown -R circleguard:circleguard /app

USER circleguard
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8083/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

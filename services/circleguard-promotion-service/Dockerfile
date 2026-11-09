# CG-DOC: Multi-stage image for circleguard-promotion-service.
# Build context is the repository root: docker build -f services/circleguard-promotion-service/Dockerfile .
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace

COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradlew ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle services/circleguard-promotion-service ./services/circleguard-promotion-service

RUN ./gradlew :services:circleguard-promotion-service:bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/services/circleguard-promotion-service/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8088/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

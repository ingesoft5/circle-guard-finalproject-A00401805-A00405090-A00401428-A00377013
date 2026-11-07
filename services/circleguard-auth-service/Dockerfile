FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :services:circleguard-auth-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S circleguard && adduser -S circleguard -G circleguard
WORKDIR /app
COPY --from=build /workspace/services/circleguard-auth-service/build/libs/ /tmp/libs/
RUN cp "$(find /tmp/libs -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /app/app.jar \
    && chown -R circleguard:circleguard /app
USER circleguard
EXPOSE 8180
HEALTHCHECK --interval=30s --timeout=10s --retries=5 \
  CMD wget -qO- http://localhost:8180/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

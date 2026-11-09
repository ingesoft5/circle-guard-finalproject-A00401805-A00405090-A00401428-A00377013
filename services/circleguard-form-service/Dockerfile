# CG-DOC: Multi-stage, non-root image for the form service.
#BUILD
# El digest fija la imagen base exacta utilizada para compilar.
FROM gradle:8.14-jdk21@sha256:930141cf2b9d472f3fd024b2e711856bede114ce116e49fcdfb64427b875982d AS build
# Permite que el usuario gradle escriba los resultados y la caché del proyecto.
WORKDIR /home/gradle/project
RUN chown gradle:gradle /home/gradle/project
# Copia la configuración del proyecto y los módulos incluidos en settings.
# Dockerfile.dockerignore limita las fuentes copiadas al código principal de Form.
COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts ./
COPY --chown=gradle:gradle services/ services/
# Genera el JAR ejecutable de Form; las pruebas se ejecutan en un paso separado.
USER gradle
RUN gradle :services:circleguard-form-service:bootJar --no-daemon --console=plain

# RUNTIME
FROM eclipse-temurin:21-jre-jammy@sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269 AS runtime
# Prepara la ubicación de la aplicación y el directorio de adjuntos con permisos de escritura.
WORKDIR /app
RUN mkdir -p /tmp/circleguard-uploads \
    && chown 10001:10001 /tmp/circleguard-uploads \
    && apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
# Transfiere únicamente el JAR generado; Gradle y las fuentes quedan en la etapa anterior.
COPY --from=build /home/gradle/project/services/circleguard-form-service/build/libs/*.jar /app/app.jar
# Ejecuta el servicio con un usuario sin privilegios de root.
USER 10001:10001
# Documenta el puerto HTTP; su publicación se configura al crear el contenedor.
EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8086/actuator/health | grep -q UP || exit 1
# Inicia Java como proceso principal para recibir las señales de parada de Docker.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

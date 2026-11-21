# CG-DOC: Multi-stage image for the Expo web client served by Nginx.
# CG-DOC: Las imágenes base se sobreescriben desde Jenkins con --build-arg para usar
# el mirror de Nexus (circleguard-base/*) en el host on-premise (sin IPv6 a Docker Hub).
ARG GRADLE_IMAGE=gradle:8.14-jdk21
ARG JDK_IMAGE=eclipse-temurin:21-jdk
ARG ALPINE_IMAGE=eclipse-temurin:21-jre-alpine
ARG JAMMY_IMAGE=eclipse-temurin:21-jre-jammy
ARG NODE_IMAGE=node:22-alpine
ARG WEB_IMAGE=nginxinc/nginx-unprivileged:1.27-alpine
FROM ${NODE_IMAGE} AS build
WORKDIR /app

# Build context is ./mobile: the Expo app is independent of the Gradle modules.
COPY package.json package-lock.json ./
RUN npm ci

COPY . ./
RUN npx expo export --platform web

FROM ${WEB_IMAGE} AS runtime
ARG VERSION=dev
ARG GIT_COMMIT=unknown
ARG SOURCE_URL=unknown
LABEL org.opencontainers.image.title="circleguard-web" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.source="${SOURCE_URL}"
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1

# CG-DOC: Multi-stage image for the Expo web client served by Nginx.
FROM node:22-alpine AS build
WORKDIR /app

# Build context is ./mobile: the Expo app is independent of the Gradle modules.
COPY package.json package-lock.json ./
RUN npm ci

COPY . ./
RUN npx expo export --platform web

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
ARG VERSION=dev
ARG GIT_COMMIT=unknown
ARG SOURCE_URL=unknown
LABEL org.opencontainers.image.title="circleguard-web" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.source="${SOURCE_URL}"
COPY --from=build /app/dist /usr/share/nginx/html

USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1

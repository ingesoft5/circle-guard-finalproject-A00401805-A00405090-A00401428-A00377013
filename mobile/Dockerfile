# CG-DOC: Multi-stage image for the Expo web client served by Nginx.
FROM node:22-alpine AS build
WORKDIR /app

COPY mobile/package.json mobile/package-lock.json ./
RUN npm ci

COPY mobile/ ./
RUN npx expo export --platform web

FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1

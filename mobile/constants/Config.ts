/**
 * CG-DOC: Mobile constants module: Config.
 */
/**
 * Global Configuration for CircleGuard Mobile.
 * Native base URLs use the localhost convention with `adb reverse` during
 * local development. The web export overrides every base URL in
 * `Config.web.ts`, which Metro resolves automatically when bundling for web
 * (the static export is served behind the Traefik ingress, so the client
 * calls the same origin; see deploy/k8s/base/ingress.yml).
 */
export const AUTH_BASE_URL = 'http://localhost:8180'; // Auth Service
export const DASHBOARD_BASE_URL = 'http://localhost:8084'; // Dashboard Service
export const FILE_BASE_URL = 'http://localhost:8085'; // File Service
export const FORM_BASE_URL = 'http://localhost:8086'; // Form Service
export const GATEWAY_BASE_URL = 'http://localhost:8087'; // Gateway Service
export const IDENTITY_BASE_URL = 'http://localhost:8083'; // Identity Service
export const NOTIFICATION_BASE_URL = 'http://localhost:8082'; // Notification Service
export const PROMOTION_BASE_URL = 'http://localhost:8088'; // Promotion Service

/**
 * CG-DOC: Web platform override for the mobile service URL configuration.
 * Expo Router static export is served behind the Traefik ingress:
 * deploy/k8s/base/ingress.yml routes /api/v1/* to each service, so the web
 * client calls the same origin with relative (empty) base URLs. Metro
 * resolves this file automatically on web builds; native keeps Config.ts.
 * Excluded from coverage by mobile/package.json (collectCoverageFrom ignores
 * *.web.ts), same as components/useColorScheme.web.ts.
 */
export const AUTH_BASE_URL = ''; // Auth Service
export const DASHBOARD_BASE_URL = ''; // Dashboard Service
export const FILE_BASE_URL = ''; // File Service
export const FORM_BASE_URL = ''; // Form Service
export const GATEWAY_BASE_URL = ''; // Gateway Service
export const IDENTITY_BASE_URL = ''; // Identity Service
export const NOTIFICATION_BASE_URL = ''; // Notification Service
export const PROMOTION_BASE_URL = ''; // Promotion Service

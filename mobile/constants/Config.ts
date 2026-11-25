/**
 * CG-DOC: Mobile constants module: Config.
 */
/**
 * Global Configuration for CircleGuard Mobile.
 * Web (Expo static export served behind the Traefik ingress): the backend is
 * reached through the same origin, because deploy/k8s/base/ingress.yml routes
 * /api/v1/* to each service. Base URLs stay relative (empty string). Native
 * builds keep the localhost convention for `adb reverse` during development.
 */
import { Platform } from 'react-native';

const web = Platform.OS === 'web';

export const AUTH_BASE_URL = web ? '' : 'http://localhost:8180'; // Auth Service
export const DASHBOARD_BASE_URL = web ? '' : 'http://localhost:8084'; // Dashboard Service
export const FILE_BASE_URL = web ? '' : 'http://localhost:8085'; // File Service
export const FORM_BASE_URL = web ? '' : 'http://localhost:8086'; // Form Service
export const GATEWAY_BASE_URL = web ? '' : 'http://localhost:8087'; // Gateway Service
export const IDENTITY_BASE_URL = web ? '' : 'http://localhost:8083'; // Identity Service
export const NOTIFICATION_BASE_URL = web ? '' : 'http://localhost:8082'; // Notification Service
export const PROMOTION_BASE_URL = web ? '' : 'http://localhost:8088'; // Promotion Service

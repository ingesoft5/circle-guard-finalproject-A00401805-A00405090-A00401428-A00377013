/**
 * CG-DOC: Unit tests for the mobile service URL configuration.
 */
import { Platform } from 'react-native';
import {
  AUTH_BASE_URL,
  DASHBOARD_BASE_URL,
  FILE_BASE_URL,
  FORM_BASE_URL,
  GATEWAY_BASE_URL,
  IDENTITY_BASE_URL,
  NOTIFICATION_BASE_URL,
  PROMOTION_BASE_URL,
} from './Config';

describe('Config', () => {
  const bases: Array<[string, number]> = [
    [AUTH_BASE_URL, 8180],
    [DASHBOARD_BASE_URL, 8084],
    [FILE_BASE_URL, 8085],
    [FORM_BASE_URL, 8086],
    [GATEWAY_BASE_URL, 8087],
    [IDENTITY_BASE_URL, 8083],
    [NOTIFICATION_BASE_URL, 8082],
    [PROMOTION_BASE_URL, 8088],
  ];

  it('exposes the base URL of every backend used by the app', () => {
    bases.forEach(([url, port]) => {
      if (Platform.OS === 'web') {
        // Web export is served behind the Traefik ingress: /api/v1/* is routed
        // by deploy/k8s/base/ingress.yml, so base URLs stay same-origin.
        expect(url).toBe('');
      } else {
        // Native keeps the `adb reverse` localhost convention.
        expect(url).toBe(`http://localhost:${port}`);
      }
    });
  });
});

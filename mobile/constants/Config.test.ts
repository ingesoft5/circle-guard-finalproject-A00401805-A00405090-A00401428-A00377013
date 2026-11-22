/**
 * CG-DOC: Unit tests for the mobile service URL configuration.
 */
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
  it('exposes the base URL of every backend used by the app', () => {
    expect(AUTH_BASE_URL).toContain(':8180');
    expect(DASHBOARD_BASE_URL).toContain(':8084');
    expect(FILE_BASE_URL).toContain(':8085');
    expect(FORM_BASE_URL).toContain(':8086');
    expect(GATEWAY_BASE_URL).toContain(':8087');
    expect(IDENTITY_BASE_URL).toContain(':8083');
    expect(NOTIFICATION_BASE_URL).toContain(':8082');
    expect(PROMOTION_BASE_URL).toContain(':8088');
  });

  it('uses absolute HTTP URLs', () => {
    [
      AUTH_BASE_URL,
      DASHBOARD_BASE_URL,
      FILE_BASE_URL,
      FORM_BASE_URL,
      GATEWAY_BASE_URL,
      IDENTITY_BASE_URL,
      NOTIFICATION_BASE_URL,
      PROMOTION_BASE_URL,
    ].forEach((url) => expect(url).toMatch(/^http:\/\//));
  });
});

/**
 * CG-DOC: Unit tests for the JWT permission hook.
 */
import { renderHook } from '@testing-library/react-native';
import { useAuth } from './useAuth';
import { usePermission } from './usePermission';

jest.mock('./useAuth', () => ({ useAuth: jest.fn() }));

const mockedUseAuth = useAuth as jest.Mock;

const tokenWith = (payload: object) =>
  `header.${Buffer.from(JSON.stringify(payload)).toString('base64url')}.signature`;

describe('usePermission', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('returns no permissions when the user is not authenticated', () => {
    mockedUseAuth.mockReturnValue({ token: null });

    const { result } = renderHook(() => usePermission());

    expect(result.current.permissions).toEqual([]);
    expect(result.current.hasPermission('ADMIN')).toBe(false);
  });

  it('decodes the permissions array from a JWT payload', () => {
    mockedUseAuth.mockReturnValue({ token: tokenWith({ permissions: ['ADMIN', 'STAFF'] }) });

    const { result } = renderHook(() => usePermission());

    expect(result.current.permissions).toEqual(['ADMIN', 'STAFF']);
    expect(result.current.hasPermission('ADMIN')).toBe(true);
    expect(result.current.hasPermission('VISITOR')).toBe(false);
  });

  it('returns an empty list when the token is not a JWT', () => {
    mockedUseAuth.mockReturnValue({ token: 'header.@@@not-base64@@@.signature' });

    const { result } = renderHook(() => usePermission());

    expect(result.current.permissions).toEqual([]);
    expect(console.warn).toHaveBeenCalledWith('Malformed JWT token detected', expect.anything());
  });

  it('returns an empty list when the payload has no permissions claim', () => {
    mockedUseAuth.mockReturnValue({ token: tokenWith({ sub: 'anon-1' }) });

    const { result } = renderHook(() => usePermission());

    expect(result.current.permissions).toEqual([]);
  });
});

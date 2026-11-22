/**
 * CG-DOC: Unit tests for biometric re-authentication and expiry logic.
 */
import { AppState } from 'react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import { renderHook, act } from '@testing-library/react-native';
import { useBiometricAuth } from './useBiometricAuth';

jest.mock('expo-local-authentication', () => ({
  hasHardwareAsync: jest.fn(),
  authenticateAsync: jest.fn(),
}));

const hasHardware = LocalAuthentication.hasHardwareAsync as jest.Mock;
const authenticateAsync = LocalAuthentication.authenticateAsync as jest.Mock;
let stateListener: ((state: string) => void) | undefined;

describe('useBiometricAuth', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-01-01T00:00:00Z'));
    jest.clearAllMocks();
    Object.defineProperty(AppState, 'currentState', {
      value: 'active',
      configurable: true,
      writable: true,
    });
    jest.spyOn(AppState, 'addEventListener').mockImplementation(((_event: string, handler: any) => {
      stateListener = handler;
      return { remove: jest.fn() };
    }) as any);
    jest.spyOn(console, 'log').mockImplementation(() => {});
    jest.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  it('starts unauthenticated', () => {
    const { result } = renderHook(() => useBiometricAuth());
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('authenticates with a passcode fallback when there is no biometric hardware', async () => {
    hasHardware.mockResolvedValue(false);
    const { result } = renderHook(() => useBiometricAuth());

    await act(async () => {
      await expect(result.current.authenticate()).resolves.toBe(true);
    });

    expect(result.current.isAuthenticated).toBe(true);
    expect(authenticateAsync).not.toHaveBeenCalled();
  });

  it('uses the native prompt when biometric hardware is available', async () => {
    hasHardware.mockResolvedValue(true);
    authenticateAsync.mockResolvedValue({ success: true });
    const { result } = renderHook(() => useBiometricAuth());

    await act(async () => {
      await expect(result.current.authenticate('Verify to continue')).resolves.toBe(true);
    });

    expect(authenticateAsync).toHaveBeenCalledWith({
      promptMessage: 'Verify to continue',
      fallbackLabel: 'Use Passcode',
    });
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('does not authenticate when the biometric prompt fails', async () => {
    hasHardware.mockResolvedValue(true);
    authenticateAsync.mockResolvedValue({ success: false });
    const { result } = renderHook(() => useBiometricAuth());

    await act(async () => {
      await expect(result.current.authenticate()).resolves.toBe(false);
    });

    expect(result.current.isAuthenticated).toBe(false);
  });

  it('returns false when the biometric check throws', async () => {
    hasHardware.mockRejectedValue(new Error('plugin missing'));
    const { result } = renderHook(() => useBiometricAuth());

    await act(async () => {
      await expect(result.current.authenticate()).resolves.toBe(false);
    });

    expect(console.error).toHaveBeenCalled();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('clears the session when the app goes to the background', async () => {
    hasHardware.mockResolvedValue(false);
    const { result } = renderHook(() => useBiometricAuth());
    await act(async () => {
      await result.current.authenticate();
    });
    expect(result.current.isAuthenticated).toBe(true);

    act(() => {
      stateListener?.('background');
    });

    expect(result.current.isAuthenticated).toBe(false);
    expect(console.log).toHaveBeenCalledWith('App moved to background, clearing biometric auth');
  });

  it('expires the session after five minutes', async () => {
    hasHardware.mockResolvedValue(false);
    const { result, rerender } = renderHook(() => useBiometricAuth());
    await act(async () => {
      await result.current.authenticate();
    });

    act(() => {
      jest.setSystemTime(new Date('2026-01-01T00:04:00Z'));
    });
    rerender();
    expect(result.current.isAuthenticated).toBe(true);

    act(() => {
      jest.setSystemTime(new Date('2026-01-01T00:05:01Z'));
    });
    rerender();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('clears authentication explicitly', async () => {
    hasHardware.mockResolvedValue(false);
    const { result } = renderHook(() => useBiometricAuth());
    await act(async () => {
      await result.current.authenticate();
    });

    act(() => {
      result.current.clearAuth();
    });

    expect(result.current.isAuthenticated).toBe(false);
  });
});

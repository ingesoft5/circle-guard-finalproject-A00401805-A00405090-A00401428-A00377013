/**
 * CG-DOC: Unit tests for the rotating QR token hook.
 */
import { renderHook, act } from '@testing-library/react-native';
import axios from 'axios';
import { useQrToken } from './useQrToken';
import { AUTH_BASE_URL } from '../constants/Config';

jest.mock('axios');

const mockedGet = axios.get as jest.Mock;

const flushPromises = () => act(async () => {
  await Promise.resolve();
});

describe('useQrToken', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockedGet.mockReset();
    mockedGet.mockResolvedValue({ data: { qrToken: 'token-1', expiresIn: '60' } });
    jest.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('fetches a token and starts the countdown when both identifiers are present', async () => {
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await flushPromises();

    expect(mockedGet).toHaveBeenCalledWith(
      `${AUTH_BASE_URL}/api/v1/auth/qr/generate`,
      { headers: { Authorization: 'Bearer auth-token' } }
    );
    expect(result.current.token).toBe('token-1');
    expect(result.current.timeLeft).toBe(60);
  });

  it('does not fetch when the anonymous id or auth token is missing', async () => {
    const missingId = renderHook(() => useQrToken(null, 'auth-token'));
    const missingToken = renderHook(() => useQrToken('test-id', null));
    await flushPromises();

    expect(missingId.result.current.token).toBeNull();
    expect(missingToken.result.current.token).toBeNull();
    expect(mockedGet).not.toHaveBeenCalled();
  });

  it('decrements the timer every second', async () => {
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await flushPromises();

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    expect(result.current.timeLeft).toBe(59);
  });

  it('rotates the token and resets the countdown when the timer expires', async () => {
    mockedGet
      .mockResolvedValueOnce({ data: { qrToken: 'token-1', expiresIn: '3' } })
      .mockResolvedValueOnce({ data: { qrToken: 'token-2', expiresIn: '45' } });

    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await flushPromises();
    expect(result.current.timeLeft).toBe(3);

    await act(async () => {
      jest.advanceTimersByTime(3000);
      await Promise.resolve();
    });

    expect(mockedGet).toHaveBeenCalledTimes(2);
    expect(result.current.token).toBe('token-2');
    expect(result.current.timeLeft).toBe(45);
  });

  it('keeps the last token when the request fails', async () => {
    mockedGet.mockRejectedValueOnce(new Error('network down'));
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await flushPromises();

    expect(result.current.token).toBeNull();
    expect(console.error).toHaveBeenCalledWith('QR Fetch Failed', expect.any(Error));
  });

  it('ignores responses without a QR token', async () => {
    mockedGet.mockResolvedValueOnce({ data: { expiresIn: '30' } });
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await flushPromises();

    expect(result.current.token).toBeNull();
  });
});

/**
 * CG-DOC: Unit tests for the gate QR validation hook.
 */
import { renderHook, act } from '@testing-library/react-native';
import { useAuth } from './useAuth';
import { useGateValidation } from './useGateValidation';

jest.mock('./useAuth', () => ({ useAuth: jest.fn() }));

const mockedUseAuth = useAuth as jest.Mock;
const fetchMock = jest.fn();

describe('useGateValidation', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = fetchMock as any;
    mockedUseAuth.mockReturnValue({ token: 'auth-token' });
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({ valid: true, status: 'GREEN', message: 'Welcome' }),
    } as Response);
    jest.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('starts idle with no result', () => {
    const { result } = renderHook(() => useGateValidation());
    expect(result.current.status).toBe('idle');
    expect(result.current.result).toBeNull();
  });

  it('rejects validation when staff authentication is missing', async () => {
    mockedUseAuth.mockReturnValue({ token: null });
    const { result } = renderHook(() => useGateValidation());

    await act(async () => {
      await result.current.validateToken('qr-token');
    });

    expect(fetchMock).not.toHaveBeenCalled();
    expect(result.current.status).toBe('error');
    expect(result.current.result).toEqual({
      valid: false,
      status: 'RED',
      message: 'Staff authentication required',
    });
  });

  it('validates a token against the gateway service', async () => {
    const { result } = renderHook(() => useGateValidation());

    await act(async () => {
      await result.current.validateToken('qr-token');
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/gate/validate'),
      expect.objectContaining({
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer auth-token',
        },
        body: JSON.stringify({ token: 'qr-token' }),
      })
    );
    expect(result.current.status).toBe('success');
    expect(result.current.result).toEqual({ valid: true, status: 'GREEN', message: 'Welcome' });
  });

  it('reports a failed HTTP response as an error', async () => {
    fetchMock.mockResolvedValue({ ok: false } as Response);
    const { result } = renderHook(() => useGateValidation());

    await act(async () => {
      await result.current.validateToken('qr-token');
    });

    expect(result.current.status).toBe('error');
    expect(result.current.result?.message).toBe('Network or system error');
    expect(console.error).toHaveBeenCalled();
  });

  it('reports a rejected request as an error', async () => {
    fetchMock.mockRejectedValue(new Error('offline'));
    const { result } = renderHook(() => useGateValidation());

    await act(async () => {
      await result.current.validateToken('qr-token');
    });

    expect(result.current.status).toBe('error');
    expect(result.current.result?.status).toBe('RED');
  });

  it('resets the validation state', async () => {
    const { result } = renderHook(() => useGateValidation());
    await act(async () => {
      await result.current.validateToken('qr-token');
    });
    expect(result.current.status).toBe('success');

    act(() => {
      result.current.reset();
    });

    expect(result.current.status).toBe('idle');
    expect(result.current.result).toBeNull();
  });
});

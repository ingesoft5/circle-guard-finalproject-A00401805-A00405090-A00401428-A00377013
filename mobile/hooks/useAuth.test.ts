/**
 * CG-DOC: Unit tests for the identity/authentication hook.
 */
import { renderHook, act, waitFor } from '@testing-library/react-native';
import { storage } from '../utils/storage';
import { useAuth } from './useAuth';

jest.mock('../utils/storage', () => ({
  storage: {
    getItem: jest.fn(),
    setItem: jest.fn(),
    deleteItem: jest.fn(),
  },
}));

const getItem = storage.getItem as jest.Mock;
const setItem = storage.setItem as jest.Mock;
const deleteItem = storage.deleteItem as jest.Mock;

describe('useAuth', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getItem.mockImplementation(async (key: string) =>
      key === 'circleguard_anon_id' ? 'anon-1' : 'token-1'
    );
    jest.spyOn(console, 'log').mockImplementation(() => {});
    jest.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('loads the persisted identity on mount', async () => {
    const { result } = renderHook(() => useAuth());

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.anonymousId).toBe('anon-1');
    expect(result.current.token).toBe('token-1');
    expect(getItem).toHaveBeenCalledWith('circleguard_anon_id');
    expect(getItem).toHaveBeenCalledWith('circleguard_token');
  });

  it('persists a new identity when enrolling', async () => {
    const { result } = renderHook(() => useAuth());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.enroll('anon-2', 'token-2');
    });

    expect(setItem).toHaveBeenCalledWith('circleguard_anon_id', 'anon-2');
    expect(setItem).toHaveBeenCalledWith('circleguard_token', 'token-2');
    expect(result.current.anonymousId).toBe('anon-2');
    expect(result.current.token).toBe('token-2');
  });

  it('clears storage and state on logout', async () => {
    const { result } = renderHook(() => useAuth());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.logout();
    });

    expect(deleteItem).toHaveBeenCalledWith('circleguard_anon_id');
    expect(deleteItem).toHaveBeenCalledWith('circleguard_token');
    expect(result.current.anonymousId).toBeNull();
    expect(result.current.token).toBeNull();
  });

  it('finishes loading and logs storage errors', async () => {
    getItem.mockRejectedValueOnce(new Error('storage blocked'));
    const { result } = renderHook(() => useAuth());

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.anonymousId).toBeNull();
    expect(console.error).toHaveBeenCalledWith('Failed to load identity', expect.any(Error));
  });
});

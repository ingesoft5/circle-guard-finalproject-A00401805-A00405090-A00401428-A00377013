/**
 * CG-DOC: Unit tests for the mesh statistics hook.
 */
import { renderHook, act } from '@testing-library/react-native';
import axios from 'axios';
import { PROMOTION_BASE_URL } from '../constants/Config';
import { useMeshStats } from './useMeshStats';

jest.mock('axios');

const get = axios.get as jest.Mock;
const flush = () => act(async () => {
  await Promise.resolve();
});

describe('useMeshStats', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    get.mockResolvedValue({ data: { confirmedCount: 2, unconfirmedCount: 5 } });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('starts with zeroed stats and does not fetch without an anonymous id', async () => {
    const { result } = renderHook(() => useMeshStats(null));
    await flush();

    expect(result.current.stats).toEqual({ confirmedCount: 0, unconfirmedCount: 0 });
    expect(get).not.toHaveBeenCalled();
  });

  it('loads the current mesh stats on mount', async () => {
    const { result } = renderHook(() => useMeshStats('anon-1'));
    await flush();

    expect(get).toHaveBeenCalledWith(`${PROMOTION_BASE_URL}/api/v1/mesh/stats/anon-1`);
    expect(result.current.stats).toEqual({ confirmedCount: 2, unconfirmedCount: 5 });
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('stores an error message when the request fails', async () => {
    get.mockRejectedValue(new Error('unavailable'));
    const { result } = renderHook(() => useMeshStats('anon-1'));
    await flush();

    expect(result.current.error).toBe('unavailable');
    expect(result.current.loading).toBe(false);
  });

  it('polls every 30 seconds and supports manual refresh', async () => {
    const { result } = renderHook(() => useMeshStats('anon-1'));
    await flush();
    expect(get).toHaveBeenCalledTimes(1);

    await act(async () => {
      jest.advanceTimersByTime(30000);
      await Promise.resolve();
    });
    expect(get).toHaveBeenCalledTimes(2);

    await act(async () => {
      await result.current.refresh();
    });
    expect(get).toHaveBeenCalledTimes(3);
  });
});

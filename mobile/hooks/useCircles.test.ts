/**
 * CG-DOC: Unit tests for the circles hook.
 */
import { renderHook, act } from '@testing-library/react-native';
import axios from 'axios';
import { PROMOTION_BASE_URL } from '../constants/Config';
import { useCircles, Circle } from './useCircles';

jest.mock('axios');

const get = axios.get as jest.Mock;
const post = axios.post as jest.Mock;
const circle: Circle = { id: 'c1', name: 'Floor 1', inviteCode: 'INVITE1', isActive: true };

describe('useCircles', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    get.mockResolvedValue({ data: [circle] });
    post.mockResolvedValue({ data: circle });
  });

  it('skips fetching when there is no anonymous id', async () => {
    const { result } = renderHook(() => useCircles(''));

    await act(async () => {
      await result.current.fetchCircles();
    });

    expect(get).not.toHaveBeenCalled();
    expect(result.current.circles).toEqual([]);
  });

  it('fetches the circles of the current user', async () => {
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      await result.current.fetchCircles();
    });

    expect(get).toHaveBeenCalledWith(`${PROMOTION_BASE_URL}/api/v1/circles/user/anon-1`);
    expect(result.current.circles).toEqual([circle]);
    expect(result.current.error).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it('stores an error message when fetching fails', async () => {
    get.mockRejectedValue(new Error('offline'));
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      await result.current.fetchCircles();
    });

    expect(result.current.error).toBe('offline');
    expect(result.current.loading).toBe(false);
  });

  it('creates a circle with the default location and refreshes the list', async () => {
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      const created = await result.current.createCircle('Floor 1');
      expect(created).toEqual(circle);
    });

    expect(post).toHaveBeenCalledWith(`${PROMOTION_BASE_URL}/api/v1/circles`, {
      name: 'Floor 1',
      locationId: 'building-1',
    });
    expect(get).toHaveBeenCalledTimes(1);
    expect(result.current.circles).toEqual([circle]);
  });

  it('creates a circle with a custom location', async () => {
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      await result.current.createCircle('Floor 2', 'building-2');
    });

    expect(post).toHaveBeenCalledWith(`${PROMOTION_BASE_URL}/api/v1/circles`, {
      name: 'Floor 2',
      locationId: 'building-2',
    });
  });

  it('propagates create errors', async () => {
    post.mockRejectedValue(new Error('create failed'));
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      await expect(result.current.createCircle('Floor 1')).rejects.toThrow('create failed');
    });

    expect(result.current.error).toBe('create failed');
    expect(result.current.loading).toBe(false);
  });

  it('joins a circle by invite code and refreshes the list', async () => {
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      const joined = await result.current.joinCircle('INVITE1');
      expect(joined).toEqual(circle);
    });

    expect(post).toHaveBeenCalledWith(
      `${PROMOTION_BASE_URL}/api/v1/circles/join/INVITE1/user/anon-1`
    );
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('propagates join errors', async () => {
    post.mockRejectedValue(new Error('join failed'));
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      await expect(result.current.joinCircle('BAD')).rejects.toThrow('join failed');
    });

    expect(result.current.error).toBe('join failed');
  });

  it('adds a member to a circle and refreshes the list', async () => {
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      const added = await result.current.addMember('c1', 'anon-2');
      expect(added).toEqual(circle);
    });

    expect(post).toHaveBeenCalledWith(`${PROMOTION_BASE_URL}/api/v1/circles/c1/members/anon-2`);
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('propagates add-member errors', async () => {
    post.mockRejectedValue(new Error('member failed'));
    const { result } = renderHook(() => useCircles('anon-1'));

    await act(async () => {
      await expect(result.current.addMember('c1', 'anon-2')).rejects.toThrow('member failed');
    });

    expect(result.current.error).toBe('member failed');
  });
});

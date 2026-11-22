/**
 * CG-DOC: Unit tests for the background proximity sync task.
 */
import { Platform } from 'react-native';
import * as TaskManager from 'expo-task-manager';
import * as BackgroundFetch from 'expo-background-fetch';
import * as SecureStore from 'expo-secure-store';
import { ProximityScanner } from './proximityScanner';
import {
  PROXIMITY_SYNC_TASK,
  registerProximityTask,
  unregisterProximityTask,
} from './proximityTask';

jest.mock('expo-task-manager', () => ({ defineTask: jest.fn() }));
jest.mock('expo-background-fetch', () => ({
  BackgroundFetchResult: { NoData: 'NoData', NewData: 'NewData', Failed: 'Failed' },
  registerTaskAsync: jest.fn(),
  unregisterTaskAsync: jest.fn(),
}));
jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn() }));
jest.mock('./proximityScanner', () => ({
  ProximityScanner: { scanForPeers: jest.fn() },
}));

const originalOS = Platform.OS;
const setOS = (os: string) => Object.defineProperty(Platform, 'OS', { value: os, configurable: true });

const getUser = SecureStore.getItemAsync as jest.Mock;
const scan = ProximityScanner.scanForPeers as jest.Mock;
const register = BackgroundFetch.registerTaskAsync as jest.Mock;
const unregister = BackgroundFetch.unregisterTaskAsync as jest.Mock;
const fetchMock = jest.fn();

type Task = () => Promise<string>;

// Captured at import time: beforeEach clears mock call history later on.
const task = (TaskManager.defineTask as jest.Mock).mock.calls[0][1] as Task;

describe('proximityTask', () => {
  beforeEach(() => {
    setOS('ios');
    jest.clearAllMocks();
    global.fetch = fetchMock as any;
    fetchMock.mockResolvedValue({ ok: true } as Response);
    jest.spyOn(console, 'log').mockImplementation(() => {});
    jest.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    setOS(originalOS);
    jest.restoreAllMocks();
  });

  it('registers the task at module load', () => {
    expect(task).toEqual(expect.any(Function));
    expect(PROXIMITY_SYNC_TASK).toBe('PROXIMITY_SYNC_TASK');
  });

  it('does not sync when no anonymous id is stored', async () => {
    getUser.mockResolvedValue(null);

    await expect(task()).resolves.toBe('NoData');
    expect(scan).not.toHaveBeenCalled();
  });

  it('does not sync when no peers are detected', async () => {
    getUser.mockResolvedValue('anon-1');
    scan.mockResolvedValue([]);

    await expect(task()).resolves.toBe('NoData');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('reports every detected encounter', async () => {
    getUser.mockResolvedValue('anon-1');
    scan.mockResolvedValue([{ anonymousId: 'peer-a' }, { anonymousId: 'peer-b' }]);

    await expect(task()).resolves.toBe('NewData');
    expect(scan).toHaveBeenCalledWith(15000);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8088/api/v1/encounters/report', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sourceId: 'anon-1', targetId: 'peer-a', locationId: 'mobile_ble_bg' }),
    });
  });

  it('returns Failed when the task throws', async () => {
    getUser.mockRejectedValue(new Error('storage unavailable'));

    await expect(task()).resolves.toBe('Failed');
    expect(console.error).toHaveBeenCalled();
  });

  it('swallows individual report failures', async () => {
    getUser.mockResolvedValue('anon-1');
    scan.mockResolvedValue([{ anonymousId: 'peer-a' }]);
    fetchMock.mockRejectedValue(new Error('offline'));

    await expect(task()).resolves.toBe('NewData');
    expect(console.error).toHaveBeenCalled();
  });

  it('registers background fetch with a 15 minute interval on native', async () => {
    await registerProximityTask();

    expect(register).toHaveBeenCalledWith(PROXIMITY_SYNC_TASK, {
      minimumInterval: 900,
      stopOnTerminate: false,
      startOnBoot: true,
    });
  });

  it('skips registration on web', async () => {
    setOS('web');
    await registerProximityTask();
    await unregisterProximityTask();

    expect(register).not.toHaveBeenCalled();
    expect(unregister).not.toHaveBeenCalled();
  });

  it('does not throw when registration fails', async () => {
    register.mockRejectedValueOnce(new Error('denied'));
    await expect(registerProximityTask()).resolves.toBeUndefined();
    expect(console.log).toHaveBeenCalledWith('[Task] Registration failed:', expect.any(Error));
  });

  it('unregisters the task on native', async () => {
    await unregisterProximityTask();
    expect(unregister).toHaveBeenCalledWith(PROXIMITY_SYNC_TASK);
  });
});

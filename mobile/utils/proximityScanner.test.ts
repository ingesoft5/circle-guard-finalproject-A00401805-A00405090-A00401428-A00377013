/**
 * CG-DOC: Unit tests for the proximity scanner abstraction.
 */
import { ProximityScanner } from './proximityScanner';

describe('ProximityScanner', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.spyOn(console, 'log').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('returns detected peers after the requested scan duration', async () => {
    const scan = ProximityScanner.scanForPeers(1000);
    expect(console.log).toHaveBeenCalledWith('[Proximity] Starting scan for 1000ms...');

    jest.advanceTimersByTime(999);
    jest.advanceTimersByTime(1);

    const peers = await scan;
    expect(peers).toHaveLength(2);
    peers.forEach((peer) => {
      expect(peer.anonymousId).toMatch(/^peer-(alpha|beta)-\d+$/);
      expect(typeof peer.rssi).toBe('number');
      expect(typeof peer.timestamp).toBe('number');
    });
    expect(console.log).toHaveBeenCalledWith('[Proximity] Scan complete. Found 2 peers.');
  });

  it('uses a 30 second default scan duration', async () => {
    const scan = ProximityScanner.scanForPeers();
    expect(console.log).toHaveBeenCalledWith('[Proximity] Starting scan for 30000ms...');

    jest.advanceTimersByTime(30000);
    await expect(scan).resolves.toHaveLength(2);
  });

  it('logs the advertised anonymous id', async () => {
    await ProximityScanner.advertisePresence('anon-123');
    expect(console.log).toHaveBeenCalledWith('[Proximity] Advertising presence as: anon-123');
  });
});

/**
 * CG-DOC: Unit tests for the spatial administration hook.
 */
import { renderHook, act } from '@testing-library/react-native';
import { FILE_BASE_URL, PROMOTION_BASE_URL } from '../constants/Config';
import { useSpatial, Building, AccessPoint } from './useSpatial';

const fetchMock = jest.fn();
const API_BASE = `${PROMOTION_BASE_URL}/api/v1`;
const FILE_API_BASE = `${FILE_BASE_URL}/api/v1/files`;

const building: Building = {
  id: 'b1',
  name: 'Building A',
  code: 'A',
  description: 'Main building',
};
const accessPoint: AccessPoint = {
  id: 'ap1',
  macAddress: '00:11:22:33:44:55',
  coordinateX: 10,
  coordinateY: 20,
  name: 'AP-1',
  floorId: 'f1',
};

const respondWith = (data: any) => {
  fetchMock.mockResolvedValueOnce({ json: async () => data } as Response);
};

describe('useSpatial', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = fetchMock as any;
  });

  it('fetches buildings and reports the loading flag', async () => {
    respondWith([building]);
    const { result } = renderHook(() => useSpatial());

    let buildings: Building[] = [];
    await act(async () => {
      buildings = await result.current.getBuildings();
    });

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/buildings`);
    expect(buildings).toEqual([building]);
    expect(result.current.loading).toBe(false);
  });

  it('creates, updates and deletes buildings', async () => {
    respondWith(building);
    const { result } = renderHook(() => useSpatial());
    const data = { name: 'Building A', code: 'A', description: 'Main building' };

    await act(async () => {
      await result.current.createBuilding(data);
    });
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/buildings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });

    await act(async () => {
      await result.current.updateBuilding('b1', data);
    });
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/buildings/b1`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });

    await act(async () => {
      await result.current.deleteBuilding('b1');
    });
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/buildings/b1`, { method: 'DELETE' });
  });

  it('manages floors', async () => {
    respondWith([{ id: 'f1', buildingId: 'b1', floorNumber: 1, name: 'Floor 1' }]);
    respondWith({ id: 'f1', floorNumber: 1, name: 'Floor 1' });
    respondWith({ id: 'f1', floorPlanUrl: 'https://plan' });
    const { result } = renderHook(() => useSpatial());

    await act(async () => {
      await result.current.getFloors('b1');
      await result.current.createFloor('b1', { floorNumber: 1, name: 'Floor 1' });
      await result.current.updateFloor('f1', { name: 'Renamed' });
    });

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/buildings/b1/floors`);
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/buildings/b1/floors`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ floorNumber: 1, name: 'Floor 1' }),
    });
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/floors/f1`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Renamed' }),
    });
  });

  it('uploads a floor plan and stores its download URL', async () => {
    respondWith({ filename: 'plan.png' });
    respondWith({ id: 'f1', floorPlanUrl: `${FILE_API_BASE}/download/plan.png` });
    const { result } = renderHook(() => useSpatial());

    await act(async () => {
      await result.current.uploadFloorPlan('f1', 'file-contents');
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, `${FILE_API_BASE}/upload`, {
      method: 'POST',
      body: expect.any(FormData),
    });
    expect(fetchMock).toHaveBeenNthCalledWith(2, `${API_BASE}/floors/f1`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ floorPlanUrl: `${FILE_API_BASE}/download/plan.png` }),
    });
  });

  it('manages access points, choosing POST or PUT by id', async () => {
    respondWith([accessPoint]);
    respondWith(accessPoint);
    respondWith({ ...accessPoint, name: 'Updated' });
    const { result } = renderHook(() => useSpatial());

    await act(async () => {
      await result.current.getAccessPoints('f1');
      await result.current.saveAccessPoint('f1', { ...accessPoint, id: undefined });
      await result.current.saveAccessPoint('f1', accessPoint);
      await result.current.deleteAccessPoint('ap1');
    });

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/floors/f1/access-points`);
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/floors/f1/access-points`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...accessPoint, id: undefined }),
    });
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/access-points/ap1`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(accessPoint),
    });
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/access-points/ap1`, { method: 'DELETE' });
  });
});

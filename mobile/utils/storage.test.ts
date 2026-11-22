/**
 * CG-DOC: Unit tests for the platform-aware storage utility.
 */
import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { storage } from './storage';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

const originalOS = Platform.OS;
const setOS = (os: string) => Object.defineProperty(Platform, 'OS', { value: os, configurable: true });

const localStorageMock = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
};

describe('storage', () => {
  beforeEach(() => {
    setOS('ios');
    jest.clearAllMocks();
    Object.defineProperty(global, 'localStorage', {
      value: localStorageMock,
      configurable: true,
      writable: true,
    });
  });

  afterEach(() => {
    setOS(originalOS);
  });

  it('uses SecureStore on native platforms', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('stored-value');

    await expect(storage.getItem('key')).resolves.toBe('stored-value');
    await storage.setItem('key', 'value');
    await storage.deleteItem('key');

    expect(SecureStore.getItemAsync).toHaveBeenCalledWith('key');
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('key', 'value');
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('key');
    expect(localStorageMock.getItem).not.toHaveBeenCalled();
  });

  it('uses localStorage on web', async () => {
    setOS('web');
    localStorageMock.getItem.mockReturnValue('web-value');

    await expect(storage.getItem('key')).resolves.toBe('web-value');
    await storage.setItem('key', 'value');
    await storage.deleteItem('key');

    expect(localStorageMock.getItem).toHaveBeenCalledWith('key');
    expect(localStorageMock.setItem).toHaveBeenCalledWith('key', 'value');
    expect(localStorageMock.removeItem).toHaveBeenCalledWith('key');
    expect(SecureStore.getItemAsync).not.toHaveBeenCalled();
    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
    expect(SecureStore.deleteItemAsync).not.toHaveBeenCalled();
  });
});

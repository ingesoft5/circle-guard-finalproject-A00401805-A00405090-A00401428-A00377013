/**
 * CG-DOC: Unit tests for the platform color-scheme helpers.
 */
import React from 'react';
import * as ReactNative from 'react-native';
import TestRenderer, { act } from 'react-test-renderer';
import { useColorScheme } from '../useColorScheme';
import { useClientOnlyValue } from '../useClientOnlyValue';

const readScheme = () => {
  let value: string | null | undefined;
  const Probe = () => {
    value = useColorScheme();
    return null;
  };
  act(() => {
    TestRenderer.create(<Probe />);
  });
  return value;
};

describe('useColorScheme', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('maps an unspecified native scheme to light', () => {
    jest.spyOn(ReactNative, 'useColorScheme').mockReturnValue('unspecified');
    expect(readScheme()).toBe('light');
  });

  it('keeps the native scheme when it is specified', () => {
    jest.spyOn(ReactNative, 'useColorScheme').mockReturnValue('dark');
    expect(readScheme()).toBe('dark');
  });
});

describe('useClientOnlyValue', () => {
  it('always returns the client value on native', () => {
    expect(useClientOnlyValue('server', 'client')).toBe('client');
  });
});

/**
 * CG-DOC: Unit tests for the animated mesh visualization.
 */
import React from 'react';
import { render } from '@testing-library/react-native';
import { MeshBackground } from '../MeshBackground';

jest.mock('react-native-svg', () => {
  const ReactModule = require('react');
  const { View } = require('react-native');
  const stub = (name: string) => (props: any) =>
    ReactModule.createElement(View, { testID: name, ...props }, props.children);
  return {
    __esModule: true,
    default: stub('Svg'),
    Line: stub('Line'),
    Circle: stub('Circle'),
    Defs: stub('Defs'),
    RadialGradient: stub('RadialGradient'),
    Stop: stub('Stop'),
    Rect: stub('Rect'),
  };
});

jest.mock('react-native-reanimated', () => ({
  __esModule: true,
  default: {
    createAnimatedComponent: (Component: any) => Component,
  },
  useAnimatedProps: (callback: any) => callback(),
  useSharedValue: (value: any) => ({ value }),
  withRepeat: (value: any) => value,
  withTiming: (value: any) => value,
  Easing: {
    inOut: (fn: any) => (value: number) => fn(value),
    sin: (value: number) => Math.sin(value),
  },
}));

describe('MeshBackground', () => {
  beforeEach(() => {
    jest.spyOn(Math, 'random').mockReturnValue(0.5);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('draws mesh lines and noise for confirmed and unconfirmed peers', () => {
    const { getByTestId, getAllByTestId, toJSON } = render(
      <MeshBackground confirmedCount={3} unconfirmedCount={2} />
    );

    expect(getByTestId('Svg')).toBeTruthy();
    expect(getAllByTestId('Line').length).toBeGreaterThan(0);
    expect(getAllByTestId('Circle').length).toBeGreaterThan(9);
    expect(toJSON()).not.toBeNull();
  });

  it('only draws node circles when there are no connections or noise', () => {
    const empty = render(<MeshBackground confirmedCount={0} unconfirmedCount={0} />);
    const baseCircles = empty.queryAllByTestId('Circle').length;

    expect(empty.queryAllByTestId('Line')).toHaveLength(0);

    const noisy = render(<MeshBackground confirmedCount={0} unconfirmedCount={10} />);
    expect(noisy.queryAllByTestId('Line')).toHaveLength(0);
    expect(noisy.queryAllByTestId('Circle').length).toBeGreaterThan(baseCircles);
  });
});

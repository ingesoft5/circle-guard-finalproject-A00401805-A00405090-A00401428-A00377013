/**
 * CG-DOC: Unit tests for themed primitives and text styling.
 */
import React from 'react';
import { render, renderHook } from '@testing-library/react-native';
import Colors from '../../constants/Colors';
import { useColorScheme } from '../useColorScheme';
import { Text as ThemedText, View as ThemedView, useThemeColor } from '../Themed';
import { MonoText } from '../StyledText';

jest.mock('../useColorScheme', () => ({ useColorScheme: jest.fn() }));

const scheme = useColorScheme as jest.Mock;

describe('Themed components', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    scheme.mockReturnValue('light');
  });

  it('prefers explicit light/dark props over the palette', () => {
    const explicit = renderHook(() => useThemeColor({ light: '#111111', dark: '#222222' }, 'text'));
    expect(explicit.result.current).toBe('#111111');

    scheme.mockReturnValue('dark');
    const dark = renderHook(() => useThemeColor({ light: '#111111', dark: '#222222' }, 'text'));
    expect(dark.result.current).toBe('#222222');
  });

  it('falls back to the active palette color', () => {
    const { result } = renderHook(() => useThemeColor({}, 'tint'));
    expect(result.current).toBe(Colors.light.tint);

    scheme.mockReturnValue('dark');
    const dark = renderHook(() => useThemeColor({}, 'tint'));
    expect(dark.result.current).toBe(Colors.dark.tint);
  });

  it('applies the theme color to Text and View', () => {
    const { getByText, getByTestId } = render(
      <>
        <ThemedText>Hello</ThemedText>
        <ThemedView testID="themed-view" />
      </>
    );

    expect(getByText('Hello')).toHaveStyle({ color: Colors.light.text });
    expect(getByTestId('themed-view')).toHaveStyle({ backgroundColor: Colors.light.background });
  });

  it('respects color overrides on themed components', () => {
    const { getByText, getByTestId } = render(
      <>
        <ThemedText lightColor="#abcdef">Override</ThemedText>
        <ThemedView testID="override-view" lightColor="#123456" />
      </>
    );

    expect(getByText('Override')).toHaveStyle({ color: '#abcdef' });
    expect(getByTestId('override-view')).toHaveStyle({ backgroundColor: '#123456' });
  });

  it('renders monospace text through MonoText', () => {
    const { getByText } = render(<MonoText>Monospace</MonoText>);
    expect(getByText('Monospace')).toHaveStyle({ fontFamily: 'SpaceMono' });
  });
});

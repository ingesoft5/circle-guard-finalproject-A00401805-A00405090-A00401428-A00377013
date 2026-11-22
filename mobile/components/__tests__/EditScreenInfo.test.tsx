/**
 * CG-DOC: Unit tests for the helper components that open external links.
 */
import React from 'react';
import { Platform, Text } from 'react-native';
import { render, fireEvent } from '@testing-library/react-native';
import * as WebBrowser from 'expo-web-browser';
import { useColorScheme } from '../useColorScheme';
import EditScreenInfo from '../EditScreenInfo';
import { ExternalLink } from '../ExternalLink';

jest.mock('expo-router', () => {
  const ReactModule = require('react');
  const { TouchableOpacity } = require('react-native');
  return {
    Link: (props: any) => ReactModule.createElement(
      TouchableOpacity,
      { testID: 'external-link', onPress: props.onPress },
      props.children
    ),
  };
});
jest.mock('expo-web-browser', () => ({ openBrowserAsync: jest.fn() }));
jest.mock('../useColorScheme', () => ({ useColorScheme: jest.fn() }));

const originalOS = Platform.OS;
const setOS = (os: string) => Object.defineProperty(Platform, 'OS', { value: os, configurable: true });

describe('ExternalLink and EditScreenInfo', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    setOS('ios');
    (useColorScheme as jest.Mock).mockReturnValue('light');
  });

  afterEach(() => {
    setOS(originalOS);
  });

  it('renders the development hints with the current file path', () => {
    const { getByText } = render(<EditScreenInfo path="/app/login.tsx" />);

    expect(getByText('/app/login.tsx')).toBeTruthy();
    expect(getByText('Open up the code for this screen:')).toBeTruthy();
    expect(getByText(/Tap here if your app doesn't automatically update/)).toBeTruthy();
  });

  it('opens the link in an in-app browser on native platforms', () => {
    const { getByTestId } = render(
      <ExternalLink href="https://example.com">
        <Text>Visit</Text>
      </ExternalLink>
    );

    fireEvent.press(getByTestId('external-link'), { preventDefault: jest.fn() });
    expect(WebBrowser.openBrowserAsync).toHaveBeenCalledWith('https://example.com');
  });

  it('leaves external links to the platform browser on web', () => {
    setOS('web');
    const { getByTestId } = render(
      <ExternalLink href="https://example.com">
        <Text>Visit</Text>
      </ExternalLink>
    );

    fireEvent.press(getByTestId('external-link'), { preventDefault: jest.fn() });
    expect(WebBrowser.openBrowserAsync).not.toHaveBeenCalled();
  });
});

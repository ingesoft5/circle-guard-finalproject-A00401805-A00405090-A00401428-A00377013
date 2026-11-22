/**
 * CG-DOC: Unit tests for the gate scan result overlay.
 */
import React from 'react';
import { Animated } from 'react-native';
import { render, fireEvent, act } from '@testing-library/react-native';
import { ScanResultOverlay } from '../ScanResultOverlay';

jest.mock('lucide-react-native', () => ({
  CheckCircle2: () => null,
  XCircle: () => null,
  Loader2: () => null,
}));

const green = { valid: true, status: 'GREEN' as const, message: 'Access allowed' };
const red = { valid: false, status: 'RED' as const, message: 'Denied by gate' };

describe('ScanResultOverlay', () => {
  beforeEach(() => {
    jest.spyOn(Animated, 'timing').mockImplementation((() => ({
      start: (callback?: () => void) => callback?.(),
    })) as any);
  });

  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  it('renders nothing while the validation is idle', () => {
    const { toJSON } = render(
      <ScanResultOverlay status="idle" result={null} onDismiss={() => {}} />
    );
    expect(toJSON()).toBeNull();
  });

  it('shows a validating state without a result', () => {
    const { getByText, queryByText } = render(
      <ScanResultOverlay status="validating" result={null} onDismiss={() => {}} />
    );

    expect(getByText('VALIDATING...')).toBeTruthy();
    expect(queryByText('TAP TO RESUME')).toBeNull();
  });

  it('shows a granted access result and dismisses on press', () => {
    const onDismiss = jest.fn();
    const { getByText } = render(
      <ScanResultOverlay status="success" result={green} onDismiss={onDismiss} />
    );

    expect(getByText('ACCESS GRANTED')).toBeTruthy();
    expect(getByText('ACCESS ALLOWED')).toBeTruthy();
    fireEvent.press(getByText('TAP TO RESUME'));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('shows a denied access result', () => {
    const { getByText } = render(
      <ScanResultOverlay status="error" result={red} onDismiss={() => {}} />
    );

    expect(getByText('ACCESS DENIED')).toBeTruthy();
    expect(getByText('DENIED BY GATE')).toBeTruthy();
    expect(getByText('TAP TO RESUME')).toBeTruthy();
  });

  it('auto-dismisses three seconds after a final result', () => {
    jest.useFakeTimers();
    const onDismiss = jest.fn();
    render(<ScanResultOverlay status="success" result={green} onDismiss={onDismiss} />);

    act(() => {
      jest.advanceTimersByTime(3000);
    });

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});

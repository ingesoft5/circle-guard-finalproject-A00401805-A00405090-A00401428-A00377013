/**
 * CG-DOC: Unit tests for the questionnaire input component.
 */
import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { QuestionInput, QuestionType } from '../QuestionInput';

jest.mock('lucide-react-native', () => ({
  Check: () => null,
  Circle: () => null,
}));

const renderInput = (
  type: QuestionType,
  options?: string,
  value?: any,
  onChange: jest.Mock = jest.fn()
) => {
  const view = render(
    <QuestionInput
      question={{ id: 'q1', text: 'Your question?', type, options }}
      value={value}
      onChange={onChange}
    />
  );
  return { ...view, onChange };
};

describe('QuestionInput', () => {
  it('renders the question label', () => {
    const { getByText } = renderInput('YES_NO');
    expect(getByText('Your question?')).toBeTruthy();
  });

  it('reports yes/no selections', () => {
    const { getByText, onChange } = renderInput('YES_NO');

    fireEvent.press(getByText('YES'));
    expect(onChange).toHaveBeenCalledWith('YES');

    fireEvent.press(getByText('NO'));
    expect(onChange).toHaveBeenCalledWith('NO');
  });

  it('renders single-choice options from the JSON string', () => {
    const { getByText, onChange } = renderInput('SINGLE_CHOICE', JSON.stringify(['A', 'B']));

    expect(getByText('A')).toBeTruthy();
    expect(getByText('B')).toBeTruthy();
    fireEvent.press(getByText('B'));
    expect(onChange).toHaveBeenCalledWith('B');
  });

  it('adds and removes multi-choice selections', () => {
    const add = renderInput('MULTI_CHOICE', JSON.stringify(['A', 'B']), ['A']);
    fireEvent.press(add.getByText('B'));
    expect(add.onChange).toHaveBeenCalledWith(['A', 'B']);

    const remove = renderInput('MULTI_CHOICE', JSON.stringify(['A', 'B']), ['A']);
    fireEvent.press(remove.getByText('A'));
    expect(remove.onChange).toHaveBeenCalledWith([]);
  });

  it('reports free-text responses', () => {
    const { getByPlaceholderText, onChange } = renderInput('TEXT');
    const input = getByPlaceholderText('Type your response...');

    fireEvent.changeText(input, 'A long answer');
    expect(onChange).toHaveBeenCalledWith('A long answer');
  });

  it('renders no options when none are configured', () => {
    const { queryByText } = renderInput('SINGLE_CHOICE');
    expect(queryByText('A')).toBeNull();
  });
});

/**
 * CG-DOC: Unit tests for the mobile theme palette.
 */
import Colors from './Colors';

describe('Colors', () => {
  it('defines a complete light palette', () => {
    expect(Colors.light).toEqual({
      text: '#000',
      background: '#fff',
      tint: '#2f95dc',
      tabIconDefault: '#ccc',
      tabIconSelected: '#2f95dc',
    });
  });

  it('defines a complete dark palette', () => {
    expect(Colors.dark).toEqual({
      text: '#fff',
      background: '#000',
      tint: '#fff',
      tabIconDefault: '#ccc',
      tabIconSelected: '#fff',
    });
  });
});

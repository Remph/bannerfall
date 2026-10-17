import { describe, expect, it } from 'vitest';
import { parseVillageView } from './villageView';

describe('parseVillageView', () => {
  it('shows the picture unless the list was chosen', () => {
    expect(parseVillageView(null)).toBe('picture');
    expect(parseVillageView('picture')).toBe('picture');
    expect(parseVillageView('something else')).toBe('picture');
    expect(parseVillageView('list')).toBe('list');
  });
});

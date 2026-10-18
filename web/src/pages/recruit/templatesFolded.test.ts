import { describe, expect, it } from 'vitest';
import { parseTemplatesFolded } from './templatesFolded';

describe('parseTemplatesFolded', () => {
  it('folds only for the stored "folded"', () => {
    expect(parseTemplatesFolded('folded')).toBe(true);
    expect(parseTemplatesFolded(null)).toBe(false);
    expect(parseTemplatesFolded('open')).toBe(false);
  });
});

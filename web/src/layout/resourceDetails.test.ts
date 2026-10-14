import { describe, expect, it } from 'vitest';
import { parseResourceDetails } from './resourceDetails';

describe('parseResourceDetails', () => {
  it('shows the details only when stored as shown', () => {
    expect(parseResourceDetails('shown')).toBe(true);
  });

  it('keeps the slim row otherwise', () => {
    expect(parseResourceDetails(null)).toBe(false);
    expect(parseResourceDetails('')).toBe(false);
    expect(parseResourceDetails('true')).toBe(false);
  });
});

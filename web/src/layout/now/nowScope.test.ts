import { describe, expect, it } from 'vitest';
import { parseNowScope } from './nowScope';

describe('parseNowScope', () => {
  it('shows all villages only when that was chosen', () => {
    expect(parseNowScope('all')).toBe('all');
    expect(parseNowScope('this')).toBe('this');
    expect(parseNowScope(null)).toBe('this');
    expect(parseNowScope('whatever')).toBe('this');
  });
});

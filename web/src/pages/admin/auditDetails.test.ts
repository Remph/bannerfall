import { describe, expect, it } from 'vitest';
import { auditDetails } from './auditDetails';

describe('auditDetails', () => {
  it('lists the values and shows a change as old → new', () => {
    expect(auditDetails({ wood: 1000, clay: 0 })).toBe('wood 1000 · clay 0');
    expect(auditDetails({ speed: [1, 2.5], inviteCode: ['a', 'b'] })).toBe('speed 1 → 2.5 · inviteCode a → b');
    expect(auditDetails({ name: 'Ben', generated: true })).toBe('name Ben · generated true');
    expect(auditDetails({ quietHours: [null, { from: 23 }] })).toBe('quietHours – → {"from":23}');
  });
});

import { describe, expect, it } from 'vitest';
import { validateVillageName } from './validation';

describe('validateVillageName', () => {
  it('accepts 1 to 32 characters after trimming', () => {
    expect(validateVillageName('A')).toBeNull();
    expect(validateVillageName("  ben's village  ")).toBeNull();
    expect(validateVillageName('x'.repeat(32))).toBeNull();
  });

  it('rejects empty and blank names', () => {
    expect(validateVillageName('')).toBe('EMPTY');
    expect(validateVillageName('   ')).toBe('EMPTY');
  });

  it('rejects names longer than 32 characters', () => {
    expect(validateVillageName('x'.repeat(33))).toBe('TOO_LONG');
    expect(validateVillageName(` ${'x'.repeat(32)} `)).toBeNull();
  });
});

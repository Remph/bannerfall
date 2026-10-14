import { describe, expect, it } from 'vitest';
import { parseVillageId, resolveVillageId, villagePath } from './currentVillage';

describe('parseVillageId', () => {
  it('accepts plain numbers only', () => {
    expect(parseVillageId('12')).toBe(12);
    expect(parseVillageId(undefined)).toBeUndefined();
    expect(parseVillageId('abc')).toBeUndefined();
    expect(parseVillageId('1e3')).toBeUndefined();
    expect(parseVillageId('-4')).toBeUndefined();
  });
});

describe('resolveVillageId', () => {
  const own = [7, 9];

  it('prefers the URL, even for a foreign village (the server decides)', () => {
    expect(resolveVillageId('9', 7, own)).toBe(9);
    expect(resolveVillageId('42', 7, own)).toBe(42);
  });

  it('then the remembered village, if it is still ours', () => {
    expect(resolveVillageId(undefined, 9, own)).toBe(9);
    expect(resolveVillageId(undefined, 42, own)).toBe(7);
  });

  it('then the first village, or none', () => {
    expect(resolveVillageId(undefined, null, own)).toBe(7);
    expect(resolveVillageId('abc', null, own)).toBe(7);
    expect(resolveVillageId(undefined, null, [])).toBeUndefined();
  });
});

describe('villagePath', () => {
  it('keeps the screen when switching villages', () => {
    expect(villagePath('/v/1', 2)).toBe('/v/2');
    expect(villagePath('/v/1/hq', 2)).toBe('/v/2/hq');
    expect(villagePath('/v/1/recruit/BARRACKS', 23)).toBe('/v/23/recruit/BARRACKS');
  });

  it('opens the overview from other screens', () => {
    expect(villagePath('/reports', 2)).toBe('/v/2');
    expect(villagePath('/', 2)).toBe('/v/2');
  });
});

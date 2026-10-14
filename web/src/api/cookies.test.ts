import { describe, expect, it } from 'vitest';
import { readCookie } from './cookies';

describe('readCookie', () => {
  it('finds a cookie among others', () => {
    expect(readCookie('a=1; XSRF-TOKEN=abc-123; b=2', 'XSRF-TOKEN')).toBe('abc-123');
  });

  it('keeps "=" inside the value', () => {
    expect(readCookie('token=a=b=', 'token')).toBe('a=b=');
  });

  it('returns undefined when missing', () => {
    expect(readCookie('', 'XSRF-TOKEN')).toBeUndefined();
    expect(readCookie('XSRF-TOKENX=1', 'XSRF-TOKEN')).toBeUndefined();
  });
});

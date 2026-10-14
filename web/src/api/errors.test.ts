import { describe, expect, it } from 'vitest';
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import { ApiError, errorMessage, isPermanentBan, toApiError } from './errors';

describe('toApiError', () => {
  it('reads code, detail and field errors from a ProblemDetail', () => {
    const error = toApiError(400, {
      status: 400,
      detail: 'Invalid request',
      code: 'VALIDATION',
      errors: [{ field: 'username', message: 'bad' }, { nonsense: true }],
    });

    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION');
    expect(error.message).toBe('Invalid request');
    expect(error.fieldErrors).toEqual([{ field: 'username', message: 'bad' }]);
  });

  it('copes with bodies that are not ProblemDetails', () => {
    expect(toApiError(502, null).code).toBe('UNKNOWN');
    expect(toApiError(502, '<html>').code).toBe('UNKNOWN');
    expect(toApiError(500, { status: 500 }).message).toBe('HTTP 500');
  });

  it('reads which order of a "recruit all" failed (M27)', () => {
    expect(toApiError(409, { code: 'NOT_ENOUGH_RESOURCES', unit: 'SWORDSMAN' }).unit).toBe('SWORDSMAN');
    expect(toApiError(409, { code: 'NOT_ENOUGH_RESOURCES' }).unit).toBeNull();
    expect(toApiError(409, { code: 'QUEUE_FULL', unit: 7 }).unit).toBeNull();
  });

  it('reads the player a report send stopped at (M40)', () => {
    expect(toApiError(404, { code: 'PLAYER_NOT_FOUND', name: 'Anna' }).playerName).toBe('Anna');
    expect(toApiError(404, { code: 'PLAYER_NOT_FOUND' }).playerName).toBeNull();
  });
});

describe('bans', () => {
  it('reads the end and the reason of an ACCOUNT_BANNED answer', () => {
    const error = toApiError(403, {
      status: 403,
      code: 'ACCOUNT_BANNED',
      detail: 'banned',
      bannedUntil: '2026-12-24T18:00:00Z',
      reason: 'cheating',
    });

    expect(error.bannedUntil).toBe('2026-12-24T18:00:00Z');
    expect(error.banReason).toBe('cheating');
    expect(toApiError(400, { code: 'VALIDATION' }).bannedUntil).toBeNull();
  });

  it('knows a ban "for good"', () => {
    expect(isPermanentBan('9999-12-31T23:59:59Z')).toBe(true);
    expect(isPermanentBan('2026-12-24T18:00:00Z')).toBe(false);
  });
});

describe('errorMessage', () => {
  it('translates known codes', () => {
    const error = new ApiError(409, 'USERNAME_TAKEN', 'taken');
    expect(errorMessage(en, error)).toBe(en.errors.USERNAME_TAKEN);
    expect(errorMessage(de, error)).toBe(de.errors.USERNAME_TAKEN);
  });

  it('falls back to a generic text', () => {
    expect(errorMessage(en, new ApiError(409, 'SOMETHING_NEW', 'x'))).toBe(en.errors.UNKNOWN);
    expect(errorMessage(en, new Error('boom'))).toBe(en.errors.UNKNOWN);
    // inherited properties of plain objects are not error codes
    expect(errorMessage(en, new ApiError(500, 'toString', 'x'))).toBe(en.errors.UNKNOWN);
  });
});

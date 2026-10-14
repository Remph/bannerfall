import { describe, expect, it } from 'vitest';
import { ApiError, networkError } from './errors';
import { shouldRetry } from './queryClient';

describe('shouldRetry', () => {
  it('does not retry client errors', () => {
    expect(shouldRetry(0, new ApiError(401, 'UNAUTHENTICATED', 'x'))).toBe(false);
    expect(shouldRetry(0, new ApiError(409, 'USERNAME_TAKEN', 'x'))).toBe(false);
  });

  it('retries network and server errors twice', () => {
    expect(shouldRetry(0, networkError())).toBe(true);
    expect(shouldRetry(1, new ApiError(500, 'INTERNAL_ERROR', 'x'))).toBe(true);
    expect(shouldRetry(2, new ApiError(500, 'INTERNAL_ERROR', 'x'))).toBe(false);
  });
});

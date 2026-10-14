import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestCsrfCookie } from './client';
import { ApiError } from './errors';

describe('requestCsrfCookie', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('turns an unreachable server into the network error', async () => {
    vi.stubGlobal('fetch', () => Promise.reject(new TypeError('Failed to fetch')));

    await expect(requestCsrfCookie()).rejects.toMatchObject({ code: 'NETWORK', status: 0 });
  });

  it('turns a refused answer into an ApiError', async () => {
    vi.stubGlobal('fetch', () =>
      Promise.resolve(new Response(JSON.stringify({ code: 'INTERNAL_ERROR' }), { status: 500 })),
    );

    const error: unknown = await requestCsrfCookie().catch((thrown: unknown) => thrown);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ code: 'INTERNAL_ERROR', status: 500 });
  });

  it('is fine with an OK answer', async () => {
    vi.stubGlobal('fetch', () => Promise.resolve(new Response(null, { status: 204 })));

    await expect(requestCsrfCookie()).resolves.toBeUndefined();
  });
});

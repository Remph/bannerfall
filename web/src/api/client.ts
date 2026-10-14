import createClient, { type Middleware } from 'openapi-fetch';
import { serverClock } from '../game/serverTime';
import { ServerVersionWatch } from '../game/version';
import { readCookie } from './cookies';
import { ApiError, networkError, responseToApiError } from './errors';
import type { paths } from './schema';

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/** The CSRF token from the cookie. If there is none yet (first visit, after logout), ask the server for one. */
async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie(document.cookie, CSRF_COOKIE);
  if (existing !== undefined) {
    return existing;
  }
  await requestCsrfCookie();
  return readCookie(document.cookie, CSRF_COOKIE);
}

/**
 * GET /api/auth/csrf, which sets the cookie. It runs inside `onRequest`, where openapi-fetch's
 * `onError` and `onResponse` don't apply: its failures are turned into the same ApiError here, so
 * an offline player sees the network text instead of UNKNOWN, and a refused answer isn't ignored
 * (the request would only fail later with a less helpful CSRF error).
 */
export async function requestCsrfCookie(): Promise<void> {
  let response: Response;
  try {
    response = await fetch('/api/auth/csrf');
  } catch {
    throw networkError();
  }
  if (!response.ok) {
    throw await responseToApiError(response);
  }
}

const serverVersion = new ServerVersionWatch();

/** Runs around every request of `api` (openapi-fetch middleware). */
const middleware: Middleware = {
  async onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method)) {
      const token = await csrfToken();
      if (token !== undefined) {
        request.headers.set(CSRF_HEADER, token);
      }
    }
  },
  async onResponse({ response }) {
    serverClock.record(response.headers.get('X-Server-Time'), Date.now());
    if (serverVersion.changed(response.headers.get('X-Server-Version'))) {
      // An update went live while this page was open: load the new version. The answer is never
      // handed on, so the old code doesn't render the new server's data in the meantime.
      window.location.reload();
      return new Promise<never>(() => {});
    }
    if (!response.ok) {
      throw await responseToApiError(response);
    }
  },
  onError({ error }) {
    // TanStack Query aborts requests it no longer needs; keep that error as it is.
    if (error instanceof Error && error.name === 'AbortError') {
      return error;
    }
    return networkError();
  },
};

/**
 * Typed API client. `paths` is generated from the server's OpenAPI description (`pnpm gen:api`),
 * so `api.POST('/api/auth/login', { body })` only compiles with the right URL and body shape.
 * Failed calls throw an ApiError instead of returning `{ error }`.
 */
export const api = createClient<paths>();
api.use(middleware);

/**
 * Waits for an api call and returns its data. Since errors are thrown by the middleware, `data` is
 * only missing if the server unexpectedly sent an empty body.
 */
export async function expectData<T>(call: Promise<{ data?: T; response: Response }>): Promise<T> {
  const { data, response } = await call;
  if (data === undefined) {
    throw new ApiError(response.status, 'UNKNOWN', 'Empty response');
  }
  return data;
}

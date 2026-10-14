import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { ApiError } from './errors';
import { forgetIfAccountChanged, meQueryKey, rememberBan, type Me } from './queries/auth';

/** Retry network and server errors up to two times, but never 4xx answers (they won't change on retry). */
export function shouldRetry(failureCount: number, error: Error): boolean {
  const isClientError = error instanceof ApiError && error.status >= 400 && error.status < 500;
  return !isClientError && failureCount < 2;
}

function isSessionLost(error: Error): boolean {
  return error instanceof ApiError && (error.code === 'UNAUTHENTICATED' || error.code === 'ACCOUNT_BANNED');
}

function onError(error: Error): void {
  // Any request answered with "not logged in" (session expired, server restarted, or banned meanwhile):
  // mark nobody as logged in, and <RequireLogin> sends the user to /login. The previous account's
  // cached data goes too (before the ban is remembered, which would be cleared with it).
  if (isSessionLost(error)) {
    forgetIfAccountChanged(queryClient, null);
    queryClient.setQueryData<Me | null>(meQueryKey, null);
    rememberBan(queryClient, error);
  }
}

// The explicit type is needed because onError above refers to queryClient itself.
export const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError }),
  mutationCache: new MutationCache({ onError }),
  defaultOptions: { queries: { retry: shouldRetry } },
});

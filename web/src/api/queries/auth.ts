import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { Language } from '../../i18n/language';
import { markVersionSeen } from '../../layout/changelogSeen';
import { leavePush, refreshPush } from '../../push/pushClient';
import { ApiError } from '../errors';
import type { components } from '../schema';
import { fetchPublicWorld, publicWorldQueryKey } from './world';

// Short names for the generated types (from the Java records MeDto, LoginRequest, RegisterRequest).
export type Me = components['schemas']['MeDto'];
export type LoginRequest = components['schemas']['LoginRequest'];
export type RegisterRequest = components['schemas']['RegisterRequest'];
export type AuthOptions = components['schemas']['AuthOptionsDto'];
export type ExternalLogin = components['schemas']['ExternalLoginDto'];
export type ExternalLoginProblem = NonNullable<ExternalLogin['problem']>;

/**
 * Where the browser starts a login through the account server (1.5, 01-architecture §4.9a): a
 * page of the server (Spring's OAuth2 login), not of this app, so links to it reload the page.
 */
export const ACCOUNT_SERVER_LOGIN = '/oauth2/authorization/accounts';

/** `as const` makes the type `readonly ['me']` instead of `string[]`. */
export const meQueryKey = ['me'] as const;

/** The logged-in account, or null when nobody is logged in (the server answered 401 or ACCOUNT_BANNED). */
async function fetchMe(queryClient: QueryClient): Promise<Me | null> {
  let me: Me | null;
  try {
    me = await expectData(api.GET('/api/me'));
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.code === 'ACCOUNT_BANNED')) {
      // forget first: the ban notice is one of the queries that gets cleared
      forgetIfAccountChanged(queryClient, null);
      rememberBan(queryClient, error);
      return null;
    }
    throw error;
  }
  // the session may have changed behind this tab's back (logged out and in as someone else in
  // another tab): the cached villages, reports … belong to the previous account then
  forgetIfAccountChanged(queryClient, me);
  return me;
}

/** Why a session ended because of a ban (M14e): when it ends and the reason, null if none was given. */
export interface BanNotice {
  until: string;
  reason: string | null;
}

export const banNoticeKey = ['banNotice'] as const;

/**
 * A request was answered with ACCOUNT_BANNED (banned while logged in, or a reload afterwards): keep
 * the ban for the login page, which <RequireLogin> sends the user to. Any other error is ignored.
 */
export function rememberBan(queryClient: QueryClient, error: unknown): void {
  if (error instanceof ApiError && error.code === 'ACCOUNT_BANNED' && error.bannedUntil !== null) {
    queryClient.setQueryData<BanNotice>(banNoticeKey, { until: error.bannedUntil, reason: error.banReason });
  }
}

/** The ban that ended this session, or null (cleared with everything else by `switchAccount`). */
export function useBanNotice(): BanNotice | null {
  return useQuery<BanNotice | null>({ queryKey: banNoticeKey, queryFn: () => null, enabled: false }).data ?? null;
}

/**
 * Another account is (or nobody is) logged in now: forget everything cached for the previous one
 * (villages, reports …), then store the new "me". `null` = nobody, which makes <RequireLogin>
 * redirect to /login.
 */
export function switchAccount(queryClient: QueryClient, me: Me | null): void {
  forgetAccountData(queryClient);
  queryClient.setQueryData<Me | null>(meQueryKey, me);
}

/** Removes every cached query except "me" itself. */
function forgetAccountData(queryClient: QueryClient): void {
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== meQueryKey[0] });
}

/**
 * "me" is about to become `next` without this tab's login or logout (`switchAccount`): the session
 * ended elsewhere (logout in another tab, a ban, a password change, an expired session) or another
 * account logged in meanwhile. Forget the previous account's data then, so the next player never
 * sees it. Nothing is cleared while the same account is still logged in, nor when nobody was
 * logged in before (that would also drop the ban notice the login page is showing).
 */
export function forgetIfAccountChanged(queryClient: QueryClient, next: Me | null): void {
  const previous = queryClient.getQueryData<Me | null>(meQueryKey);
  if (previous && previous.accountId !== next?.accountId) {
    forgetAccountData(queryClient);
  }
}

export function useMe() {
  const queryClient = useQueryClient();
  return useQuery({ queryKey: meQueryKey, queryFn: () => fetchMe(queryClient) });
}

/** For screens inside <RequireLogin>: the account is guaranteed to be there. */
export function useCurrentAccount(): Me {
  const { data } = useMe();
  if (!data) {
    throw new Error('useCurrentAccount() must be used inside <RequireLogin>');
  }
  return data;
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: LoginRequest) => expectData(api.POST('/api/auth/login', { body })),
    // The response already is the new "me": put it into the cache instead of fetching it again.
    onSuccess: (me) => switchAccount(queryClient, me),
  });
}

/** A new player of this world is logged in (registered here, or joined through the account server). */
async function startAsNewPlayer(queryClient: QueryClient, me: Me): Promise<void> {
  // A new player starts on the current version: no "new in 1.1.0" banner on the first screen
  // (M28). Asked before the game screens open, so the banner reads the mark when it appears.
  try {
    const world = await queryClient.fetchQuery({
      queryKey: publicWorldQueryKey,
      queryFn: fetchPublicWorld,
      staleTime: 60_000,
    });
    markVersionSeen(world.version);
  } catch {
    // only the banner depends on it: a failed request must not fail the registration
  }
  switchAccount(queryClient, me);
}

export function useRegister() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: RegisterRequest) => expectData(api.POST('/api/auth/register', { body })),
    onSuccess: (me) => startAsNewPlayer(queryClient, me),
  });
}

/**
 * GET /api/auth/options (1.5): whether this world has "Log in with …" and whether its own
 * passwords still work (mode OFF, LINKING or REQUIRED). Set by the host; it changes with a restart.
 */
export function useAuthOptions() {
  return useQuery({
    queryKey: ['authOptions'],
    queryFn: () => expectData(api.GET('/api/auth/options')),
    staleTime: 5 * 60_000,
  });
}

/**
 * GET /api/auth/external (1.5): the name of a login at the account server that waits for the join
 * page, and what went wrong with the last such login. The server forgets the problem once it has
 * told it, so this is asked once per visit of a page (no refetching, dropped when the page closes).
 */
export function useExternalLogin() {
  return useQuery({
    queryKey: ['externalLogin'],
    queryFn: () => expectData(api.GET('/api/auth/external')),
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

/** POST /api/auth/join (1.5): whoever logged in at the account server becomes a player here. */
export function useJoin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (inviteCode: string) => expectData(api.POST('/api/auth/join', { body: { inviteCode } })),
    onSuccess: (me) => startAsNewPlayer(queryClient, me),
  });
}

/**
 * POST /api/me/link (1.5): the player wants his account here to belong to his account at the
 * account server, confirmed with his current password (linking ends it). The browser goes there to
 * log in; the server finishes the link when it comes back.
 */
export function useLinkAccount() {
  return useMutation({
    mutationFn: async (body: { currentPassword: string }) => {
      await api.POST('/api/me/link', { body });
    },
    onSuccess: () => window.location.assign(ACCOUNT_SERVER_LOGIN),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      // the next player on this browser gets none of this player's notifications (1.5)
      await leavePush();
      await api.POST('/api/auth/logout');
    },
    onSuccess: () => switchAccount(queryClient, null),
  });
}

/**
 * POST /api/me/respawn: a player whose last village was conquered (`needsRespawn`) starts again
 * with a new village, next to his guild when `nearGuild` (1.6, `respawnNearGuild`). Afterwards
 * /api/me is fetched again (village list, protection).
 */
export function useRespawn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (nearGuild: boolean) => expectData(api.POST('/api/me/respawn', { body: { nearGuild } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: meQueryKey }),
  });
}

/** POST /api/me/password: 204; WRONG_PASSWORD, or TOO_MANY_LOGIN_ATTEMPTS after five wrong ones. */
export function useChangePassword(language: Language) {
  return useMutation({
    mutationFn: async (body: { currentPassword: string; newPassword: string }) => {
      await api.POST('/api/me/password', { body });
    },
    // a new password ends every device's notifications on the server; this one signs up again (1.5)
    onSuccess: () => void refreshPush(language),
  });
}

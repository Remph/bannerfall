import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../errors';
import { banNoticeKey, forgetIfAccountChanged, meQueryKey, rememberBan, switchAccount, type Me } from './auth';

const anna: Me = {
  accountId: 2,
  username: 'anna',
  role: 'PLAYER',
  playerId: 2,
  points: 39,
  protectedUntil: '2026-10-06T12:00:00Z',
  needsRespawn: false,
  linked: false,
  respawnNearGuild: false,
  villages: [{ id: 2, name: "anna's village", x: 53, y: 59, points: 39, incomingAttacks: 0, tag: null }],
};

describe('switchAccount', () => {
  it('forgets the previous account’s data and stores the new me', () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(['villages', 1], { name: 'old village' });

    switchAccount(queryClient, anna);

    expect(queryClient.getQueryData(['villages', 1])).toBeUndefined();
    expect(queryClient.getQueryData(meQueryKey)).toEqual(anna);
  });

  it('stores null when nobody is logged in', () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(meQueryKey, anna);

    switchAccount(queryClient, null);

    expect(queryClient.getQueryData(meQueryKey)).toBeNull();
  });
});

describe('forgetIfAccountChanged', () => {
  const ben: Me = { ...anna, accountId: 3, username: 'ben', playerId: 3 };

  function withAnnasVillage(): QueryClient {
    const queryClient = new QueryClient();
    queryClient.setQueryData(meQueryKey, anna);
    queryClient.setQueryData(['villages', 2], { name: "anna's village" });
    return queryClient;
  }

  it('forgets the data when the session ended elsewhere', () => {
    const queryClient = withAnnasVillage();

    forgetIfAccountChanged(queryClient, null);

    expect(queryClient.getQueryData(['villages', 2])).toBeUndefined();
    expect(queryClient.getQueryData(meQueryKey)).toEqual(anna);
  });

  it('forgets the data when another account is logged in now', () => {
    const queryClient = withAnnasVillage();

    forgetIfAccountChanged(queryClient, ben);

    expect(queryClient.getQueryData(['villages', 2])).toBeUndefined();
  });

  it('keeps the data while the same account is still logged in', () => {
    const queryClient = withAnnasVillage();

    forgetIfAccountChanged(queryClient, { ...anna, points: 40 });

    expect(queryClient.getQueryData(['villages', 2])).toEqual({ name: "anna's village" });
  });

  it('keeps the ban notice when nobody was logged in before', () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(meQueryKey, null);
    queryClient.setQueryData(banNoticeKey, { until: '2026-10-20T10:00:00Z', reason: null });

    forgetIfAccountChanged(queryClient, null);

    expect(queryClient.getQueryData(banNoticeKey)).toEqual({ until: '2026-10-20T10:00:00Z', reason: null });
  });
});

describe('rememberBan', () => {
  it('keeps the end and the reason of a ban for the login page', () => {
    const queryClient = new QueryClient();

    rememberBan(
      queryClient,
      new ApiError(403, 'ACCOUNT_BANNED', 'x', [], { until: '2026-10-20T10:00:00Z', reason: 'cheating' }),
    );

    expect(queryClient.getQueryData(banNoticeKey)).toEqual({ until: '2026-10-20T10:00:00Z', reason: 'cheating' });
  });

  it('ignores every other error', () => {
    const queryClient = new QueryClient();

    rememberBan(queryClient, new ApiError(401, 'UNAUTHENTICATED', 'x'));
    rememberBan(queryClient, new Error('boom'));

    expect(queryClient.getQueryData(banNoticeKey)).toBeUndefined();
  });

  it('is forgotten when someone logs in', () => {
    const queryClient = new QueryClient();
    rememberBan(
      queryClient,
      new ApiError(403, 'ACCOUNT_BANNED', 'x', [], { until: '2026-10-20T10:00:00Z', reason: '' }),
    );

    switchAccount(queryClient, anna);

    expect(queryClient.getQueryData(banNoticeKey)).toBeUndefined();
  });
});

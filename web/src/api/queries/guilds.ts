import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { mapQueryKey } from './map';
import { playerQueryKey, rankingQueryKey } from './ranking';

export type MyGuild = components['schemas']['MyGuildDto'];
export type Guild = components['schemas']['GuildDto'];
export type GuildSummary = components['schemas']['GuildSummaryDto'];
export type GuildMember = components['schemas']['GuildMemberDto'];
export type GuildInvitation = components['schemas']['GuildInvitationDto'];

/** Prefix of everything about guilds: the list, single guilds and the player's own state. */
export const guildsQueryKey = ['guilds'] as const;
export const myGuildQueryKey = [...guildsQueryKey, 'mine'] as const;
export const guildListQueryKey = [...guildsQueryKey, 'list'] as const;

export function guildQueryKey(guildId: number) {
  return [...guildsQueryKey, guildId] as const;
}

/** GET /api/guilds/mine: the player's guild (or none) and the invitations he has received. */
export function useMyGuild() {
  return useQuery({
    queryKey: myGuildQueryKey,
    queryFn: () => expectData(api.GET('/api/guilds/mine')),
  });
}

/** GET /api/guilds: every guild, strongest first. */
export function useGuildList() {
  return useQuery({
    queryKey: guildListQueryKey,
    queryFn: () => expectData(api.GET('/api/guilds')),
  });
}

/** GET /api/guilds/{id}: 404 GUILD_NOT_FOUND for a guild that does not exist (any more). */
export function useGuild(guildId: number) {
  return useQuery({
    queryKey: guildQueryKey(guildId),
    queryFn: () => expectData(api.GET('/api/guilds/{guildId}', { params: { path: { guildId } } })),
    retry: false,
  });
}

/**
 * A change of the guild puts the answer (the new state of the player) straight into the cache and
 * makes everything that shows guild tags outdated: the lists, single guilds, the ranking, profiles
 * and the map. Only what is on screen is fetched again right away.
 */
export function refreshGuilds(queryClient: QueryClient, mine?: MyGuild): Promise<unknown> {
  if (mine !== undefined) {
    queryClient.setQueryData(myGuildQueryKey, mine);
  }
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: guildsQueryKey, refetchType: mine === undefined ? 'active' : 'all' }),
    queryClient.invalidateQueries({ queryKey: rankingQueryKey }),
    queryClient.invalidateQueries({ queryKey: playerQueryKey }),
    queryClient.invalidateQueries({ queryKey: mapQueryKey }),
  ]);
}

/** One request that changes the player's guild state; every one answers with the new state. */
function useGuildMutation<Variables>(request: (variables: Variables) => Promise<MyGuild>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (mine) => refreshGuilds(queryClient, mine),
    // also after an error: the screen was probably out of date (somebody else changed the guild)
    onError: () => refreshGuilds(queryClient),
  });
}

/** POST /api/guilds: found a guild; the player leads it. */
export function useCreateGuild() {
  return useGuildMutation((body: { name: string; tag: string }) => expectData(api.POST('/api/guilds', { body })));
}

/** PUT /api/guilds/mine/description (leader). */
export function useDescribeGuild() {
  return useGuildMutation((description: string) =>
    expectData(api.PUT('/api/guilds/mine/description', { body: { description } })),
  );
}

/** POST /api/guilds/mine/invitations (leader): invite a player by name. */
export function useInvitePlayer() {
  return useGuildMutation((playerName: string) =>
    expectData(api.POST('/api/guilds/mine/invitations', { body: { playerName } })),
  );
}

/** DELETE /api/guilds/mine/invitations/{id} (leader). */
export function useWithdrawInvitation() {
  return useGuildMutation((invitationId: number) =>
    expectData(api.DELETE('/api/guilds/mine/invitations/{invitationId}', { params: { path: { invitationId } } })),
  );
}

/** POST /api/guilds/invitations/{id}/accept. */
export function useAcceptInvitation() {
  return useGuildMutation((invitationId: number) =>
    expectData(api.POST('/api/guilds/invitations/{invitationId}/accept', { params: { path: { invitationId } } })),
  );
}

/** DELETE /api/guilds/invitations/{id}: say no. */
export function useDeclineInvitation() {
  return useGuildMutation((invitationId: number) =>
    expectData(api.DELETE('/api/guilds/invitations/{invitationId}', { params: { path: { invitationId } } })),
  );
}

/** POST /api/guilds/mine/leave. */
export function useLeaveGuild() {
  return useGuildMutation<void>(() => expectData(api.POST('/api/guilds/mine/leave')));
}

/** DELETE /api/guilds/mine/members/{playerId} (leader). */
export function useKickMember() {
  return useGuildMutation((playerId: number) =>
    expectData(api.DELETE('/api/guilds/mine/members/{playerId}', { params: { path: { playerId } } })),
  );
}

/** POST /api/guilds/mine/leader (leader): give the lead to a member. */
export function useHandLeadOver() {
  return useGuildMutation((playerId: number) =>
    expectData(api.POST('/api/guilds/mine/leader', { body: { playerId } })),
  );
}

/** DELETE /api/guilds/mine (leader): end the guild for everybody. */
export function useDisbandGuild() {
  return useGuildMutation<void>(() => expectData(api.DELETE('/api/guilds/mine')));
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, expectData } from '../client';
import { mapQueryKey } from './map';
import { playerQueryKey, rankingQueryKey } from './ranking';
import { removeChatMessage } from './chat';
import type { components } from '../schema';

/*
 * The admin endpoints for NPC lords (04-api, "Admin"); the server answers 403 to everyone but an
 * ADMIN account, so the page behind <RequireAdmin> is only a courtesy.
 */

export type NpcLord = components['schemas']['NpcLordDto'];
export type NpcPersonality = NpcLord['personality'];
export type NpcDifficulty = components['schemas']['NpcSettingsDto']['difficulty'];
/** The values of one difficulty: also what the admin edits for CUSTOM. */
export type NpcDifficultyValues = components['schemas']['Difficulty'];

export const npcPersonalities: readonly NpcPersonality[] = ['BALANCED', 'OFFENSIVE', 'DEFENSIVE'];
export const npcDifficulties: readonly NpcDifficulty[] = ['EASY', 'NORMAL', 'HARD', 'CUSTOM'];

const npcLordsKey = ['admin', 'npc-lords'] as const;
const npcSettingsKey = ['admin', 'npc-settings'] as const;
const npcCustomKey = ['admin', 'npc-custom'] as const;

/** GET /api/admin/npc-lords: newest first. Refreshed every minute, rounds change it all the time. */
export function useNpcLords() {
  return useQuery({
    queryKey: npcLordsKey,
    queryFn: () => expectData(api.GET('/api/admin/npc-lords')),
    refetchInterval: 60_000,
  });
}

/** POST /api/admin/npc-lords: spawns one lord of this personality (WORLD_FULL if there's no room). */
export function useSpawnNpcLord() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (personality: NpcPersonality) =>
      expectData(api.POST('/api/admin/npc-lords', { body: { personality } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: npcLordsKey }),
  });
}

/** GET /api/admin/npc-settings: the difficulty the lords play at. */
export function useNpcSettings() {
  return useQuery({
    queryKey: npcSettingsKey,
    queryFn: () => expectData(api.GET('/api/admin/npc-settings')),
  });
}

/** PUT /api/admin/npc-settings: picking CUSTOM the first time stores a copy of NORMAL. */
export function useSetNpcDifficulty() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (difficulty: NpcDifficulty) => expectData(api.PUT('/api/admin/npc-settings', { body: { difficulty } })),
    onSuccess: (settings) => {
      queryClient.setQueryData(npcSettingsKey, settings);
      return queryClient.invalidateQueries({ queryKey: npcCustomKey });
    },
  });
}

/** GET /api/admin/npc-balance/custom: the CUSTOM values (a copy of NORMAL until stored). */
export function useNpcCustom() {
  return useQuery({
    queryKey: npcCustomKey,
    queryFn: () => expectData(api.GET('/api/admin/npc-balance/custom')),
  });
}

/** PUT /api/admin/npc-balance/custom: 400 VALIDATION for a value out of range. */
export function useSetNpcCustom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: NpcDifficultyValues) => expectData(api.PUT('/api/admin/npc-balance/custom', { body })),
    onSuccess: (values) => {
      queryClient.setQueryData(npcCustomKey, values);
    },
  });
}

export type AdminWorldSettings = components['schemas']['AdminWorldSettingsDto'];
/** What PUT /api/admin/world-settings takes: the settings that can change in a running world. */
export type WorldSettingsUpdate = components['schemas']['WorldSettingsUpdateRequest'];
export type FailedEvent = components['schemas']['FailedEventDto'];

export const worldSettingsKey = ['admin', 'world-settings'] as const;
const failedEventsKey = ['admin', 'failed-events'] as const;

/** GET /api/admin/world-settings: every setting including the invite code. */
export function useAdminWorldSettings() {
  return useQuery({
    queryKey: worldSettingsKey,
    queryFn: () => expectData(api.GET('/api/admin/world-settings')),
  });
}

/** PUT /api/admin/world-settings: 400 VALIDATION for a value out of range. The public `/api/world` follows. */
export function useUpdateWorldSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: WorldSettingsUpdate) => expectData(api.PUT('/api/admin/world-settings', { body })),
    onSuccess: (settings) => {
      queryClient.setQueryData(worldSettingsKey, settings);
      return queryClient.invalidateQueries({ queryKey: ['world'] });
    },
  });
}

/** PUT /api/admin/world-opening: the pre-game's opening time, null takes it back (409 WORLD_ALREADY_OPEN once open). */
export function useSetWorldOpening() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (opensAt: string | null) => expectData(api.PUT('/api/admin/world-opening', { body: { opensAt } })),
    onSuccess: (settings) => {
      queryClient.setQueryData(worldSettingsKey, settings);
      return queryClient.invalidateQueries({ queryKey: ['world'] });
    },
  });
}

/** POST /api/admin/world-opening/now: opens the world within a moment. */
export function useOpenWorldNow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => expectData(api.POST('/api/admin/world-opening/now')),
    onSuccess: (settings) => {
      queryClient.setQueryData(worldSettingsKey, settings);
      return queryClient.invalidateQueries({ queryKey: ['world'] });
    },
  });
}

/**
 * PUT /api/admin/world-end (v1.3): the end date, null takes it back; a time in the past ends the world
 * at once (409 WORLD_ENDED once it has ended).
 */
export function useSetWorldEnd() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (endsAt: string | null) => expectData(api.PUT('/api/admin/world-end', { body: { endsAt } })),
    onSuccess: (settings) => {
      queryClient.setQueryData(worldSettingsKey, settings);
      return queryClient.invalidateQueries({ queryKey: ['world'] });
    },
  });
}

/** POST /api/admin/world-end/now: ends the world within a moment; the most points win. */
export function useEndWorldNow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => expectData(api.POST('/api/admin/world-end/now')),
    onSuccess: (settings) => {
      queryClient.setQueryData(worldSettingsKey, settings);
      return queryClient.invalidateQueries({ queryKey: ['world'] });
    },
  });
}

/** POST /api/admin/world-end/reopen: takes the end back (409 WORLD_NOT_ENDED while it runs). */
export function useReopenWorld() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => expectData(api.POST('/api/admin/world-end/reopen')),
    onSuccess: (settings) => {
      queryClient.setQueryData(worldSettingsKey, settings);
      return queryClient.invalidateQueries({ queryKey: ['world'] });
    },
  });
}

/** GET /api/admin/events/failed: the newest 100. */
export function useFailedEvents() {
  return useQuery({
    queryKey: failedEventsKey,
    queryFn: () => expectData(api.GET('/api/admin/events/failed')),
  });
}

/** POST /api/admin/events/{id}/retry: back to PENDING with fresh attempts. */
export function useRetryEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    // 204: no body, so no expectData (a failed call still throws the ApiError)
    mutationFn: async (id: number) => {
      await api.POST('/api/admin/events/{id}/retry', { params: { path: { id } } });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: failedEventsKey }),
  });
}

/** POST /api/admin/players/{id}/delete: the villages become barbarian, the player leaves the ranking. */
export function useDeletePlayer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => expectData(api.POST('/api/admin/players/{id}/delete', { params: { path: { id } } })),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: rankingQueryKey }),
        queryClient.invalidateQueries({ queryKey: playerQueryKey }),
        queryClient.invalidateQueries({ queryKey: mapQueryKey }),
      ]),
  });
}

/** DELETE /api/admin/chat/{id}: hides a message from the history; the server tells the open chats. */
export function useHideChatMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.DELETE('/api/admin/chat/{messageId}', { params: { path: { messageId: id } } });
      return id;
    },
    // the chat query is never refetched by itself (and a merge would keep the message), so cut it out
    onSuccess: (id) => removeChatMessage(queryClient, id),
  });
}

// ---- host console (M14d, 08-host-console) ----

export type AdminStatus = components['schemas']['AdminStatusDto'];
export type AdminPlayer = components['schemas']['AdminPlayerDto'];
export type AdminAction = components['schemas']['AdminActionDto'];
export type AdminSuspicious = components['schemas']['SuspiciousAccountDto'];
export type AccountRole = AdminPlayer['role'];

const statusKey = ['admin', 'status'] as const;
const playersKey = ['admin', 'players'] as const;
const auditKey = ['admin', 'audit'] as const;

/** GET /api/admin/status: refreshed every 30 s while the page is open. */
export function useAdminStatus() {
  return useQuery({
    queryKey: statusKey,
    queryFn: () => expectData(api.GET('/api/admin/status')),
    refetchInterval: 30_000,
  });
}

/** GET /api/admin/players: the human players with their villages, by name. */
export function useAdminPlayers() {
  return useQuery({
    queryKey: playersKey,
    queryFn: () => expectData(api.GET('/api/admin/players')),
  });
}

/** GET /api/admin/audit: the newest 100 admin actions. */
export function useAudit() {
  return useQuery({
    queryKey: auditKey,
    queryFn: () => expectData(api.GET('/api/admin/audit', { params: { query: { limit: 100 } } })),
  });
}

/** Every change writes an audit row: the audit tab must show it the next time it is opened. */
function invalidateAudit(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: auditKey });
}

/** POST /api/admin/players/{id}/role: LAST_ADMIN for the last admin. */
export function useChangeRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role }: { id: number; role: AccountRole }) =>
      expectData(api.POST('/api/admin/players/{id}/role', { params: { path: { id } }, body: { role } })),
    onSuccess: () =>
      Promise.all([queryClient.invalidateQueries({ queryKey: playersKey }), invalidateAudit(queryClient)]),
  });
}

/** POST /api/admin/players/{id}/ban: `until` null = for good (M14e). */
export function useBanPlayer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, until, reason }: { id: number; until: string | null; reason: string }) =>
      expectData(api.POST('/api/admin/players/{id}/ban', { params: { path: { id } }, body: { until, reason } })),
    onSuccess: () =>
      Promise.all([queryClient.invalidateQueries({ queryKey: playersKey }), invalidateAudit(queryClient)]),
  });
}

/** POST /api/admin/players/{id}/unban. */
export function useUnbanPlayer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => expectData(api.POST('/api/admin/players/{id}/unban', { params: { path: { id } } })),
    onSuccess: () =>
      Promise.all([queryClient.invalidateQueries({ queryKey: playersKey }), invalidateAudit(queryClient)]),
  });
}

/** GET /api/admin/suspicious: accounts with requests the game never sends (last 30 days). */
export function useSuspicious() {
  return useQuery({
    queryKey: ['admin', 'suspicious'] as const,
    queryFn: () => expectData(api.GET('/api/admin/suspicious')),
  });
}

/** POST /api/admin/invariants: runs the check of impossible states now. */
export function useInvariantCheck() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => expectData(api.POST('/api/admin/invariants')),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: statusKey }),
  });
}

/** POST /api/admin/players/{id}/reset-password: without a password the answer carries a generated one. */
export function useResetPassword() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, password }: { id: number; password: string | null }) =>
      expectData(api.POST('/api/admin/players/{id}/reset-password', { params: { path: { id } }, body: { password } })),
    onSuccess: () => invalidateAudit(queryClient),
  });
}

/** POST /api/admin/broadcast: a chat message from the host to everybody. */
export function useBroadcast() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (text: string) => expectData(api.POST('/api/admin/broadcast', { body: { text } })),
    onSuccess: () => invalidateAudit(queryClient),
  });
}

/** POST /api/admin/barbarians: FIELD_NOT_FREE if the field is taken or off the map. */
export function useSpawnBarbarian() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (field: { x: number; y: number }) => expectData(api.POST('/api/admin/barbarians', { body: field })),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: mapQueryKey }),
        queryClient.invalidateQueries({ queryKey: statusKey }),
        invalidateAudit(queryClient),
      ]),
  });
}

export type HostCheat =
  | { kind: 'resources'; villageId: number; amount: number }
  | { kind: 'units'; villageId: number; units: Record<string, number> }
  | { kind: 'finish-orders' | 'finish-movements'; villageId: number };

/**
 * The host cheats /api/admin/cheats/villages/{id}/… (only while the server has them switched on):
 * 204, no body. The village may be anyone's (the admin's own too), so everything cached is refreshed.
 */
export function useHostCheat() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (cheat: HostCheat) => {
      const params = { path: { villageId: cheat.villageId } };
      switch (cheat.kind) {
        case 'resources': {
          const { amount } = cheat;
          await api.POST('/api/admin/cheats/villages/{villageId}/resources', {
            params,
            body: { wood: amount, clay: amount, iron: amount },
          });
          return;
        }
        case 'units':
          await api.POST('/api/admin/cheats/villages/{villageId}/units', { params, body: { units: cheat.units } });
          return;
        case 'finish-orders':
          await api.POST('/api/admin/cheats/villages/{villageId}/finish-orders', { params });
          return;
        case 'finish-movements':
          await api.POST('/api/admin/cheats/villages/{villageId}/finish-movements', { params });
          return;
      }
    },
    onSuccess: () => queryClient.invalidateQueries(),
  });
}

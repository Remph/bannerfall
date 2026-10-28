import { useMutation, useQuery, useQueryClient, type QueryClient, type QueryKey } from '@tanstack/react-query';
import { appendLive, mergeLatest, olderPageBefore, prependOlder, withoutMessage, type ChatLog } from '../../game/chat';
import { api, expectData } from '../client';
import type { components } from '../schema';
import { myGuildQueryKey, useMyGuild, type MyGuild } from './guilds';
import { useWorld } from './world';

/** A chat message as `GET /api/chat/messages` and `/topic/chat` send it. */
export type ChatMessage = components['schemas']['ChatMessageDto'];
/** `/topic/chat` when the admin hid a message (M14a): open chats remove it. */
export interface ChatHidden {
  hiddenId: number;
}
/** A refused chat message on `/user/queue/errors`. */
export type ChatError = components['schemas']['ChatErrorDto'];

/**
 * The chat shown: one entry with all loaded messages (newest page, older pages scrolled to, live
 * messages). Not an infinite query: live messages are added to it, and after a reconnect the newest
 * page is merged in (game/chat.ts mergeLatest), which TanStack's page groups can't express.
 */
export const chatQueryKey = ['chat'] as const;

/** The two channels of the chat panel: everybody, and (M15b) the player's guild. */
export type ChatChannel = 'world' | 'guild';

/**
 * The guild's chat, kept per guild id: after leaving or being kicked, nothing of the old guild is
 * shown in the next one.
 */
export function guildChatQueryKey(guildId: number) {
  return ['guildChat', guildId] as const;
}

/** The id of the player's guild as the cache knows it (what the realtime layer files guild messages under). */
export function myGuildIdIn(queryClient: QueryClient): number | undefined {
  return queryClient.getQueryData<MyGuild>(myGuildQueryKey)?.guild?.id;
}

function fetchWorldMessages(before?: number): Promise<ChatMessage[]> {
  return expectData(api.GET('/api/chat/messages', { params: { query: { before } } }));
}

function fetchGuildMessages(before?: number): Promise<ChatMessage[]> {
  return expectData(api.GET('/api/guilds/mine/chat', { params: { query: { before } } }));
}

function useChannelLog(
  queryKey: QueryKey,
  fetchMessages: (before?: number) => Promise<ChatMessage[]>,
  enabled: boolean,
) {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey,
    queryFn: async () => {
      const latest = await fetchMessages();
      // read after the answer: live messages may have been added meanwhile
      return mergeLatest(queryClient.getQueryData<ChatLog<ChatMessage>>(queryKey), latest);
    },
    staleTime: Infinity,
    enabled,
  });
}

/**
 * GET /api/chat/messages: the newest 100, merged with what is already shown. Live messages keep it
 * up to date, so it is never refetched by itself (`staleTime: Infinity`); after a reconnect the
 * realtime layer invalidates everything, which fetches it again.
 */
export function useChat() {
  return useChannelLog(chatQueryKey, fetchWorldMessages, true);
}

/**
 * The id of the guild whose chat the player can use now: he is in a guild and the host has guilds
 * on (writing needs both). undefined otherwise, then there is no guild channel: no tab, no unread
 * count (the server still serves the history while guilds are off, but nobody could read it).
 */
export function useGuildChatId(): number | undefined {
  const guildsEnabled = useWorld().data?.guildsEnabled === true;
  const guildId = useMyGuild().data?.guild?.id;
  return guildsEnabled ? guildId : undefined;
}

/** GET /api/guilds/mine/chat (M15b): the same for the player's guild; waits while he has no guild channel. */
export function useGuildChat() {
  const guildId = useGuildChatId();
  return useChannelLog(guildChatQueryKey(guildId ?? 0), fetchGuildMessages, guildId !== undefined);
}

/**
 * A message from `/topic/chat` (or, with the guild's key, from `/user/queue/guild-chat`). While the
 * history hasn't loaded yet, its answer may have been made before the message was saved: the
 * history is fetched again, which then has it.
 */
export function addChatMessage(
  queryClient: QueryClient,
  message: ChatMessage,
  queryKey: QueryKey = chatQueryKey,
): void {
  if (queryClient.getQueryData<ChatLog<ChatMessage>>(queryKey) === undefined) {
    void queryClient.invalidateQueries({ queryKey });
    return;
  }
  queryClient.setQueryData<ChatLog<ChatMessage>>(queryKey, (log) =>
    log === undefined ? log : appendLive(log, message),
  );
}

function useLoadOlder(queryKey: QueryKey, fetchMessages: (before?: number) => Promise<ChatMessage[]>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const log = queryClient.getQueryData<ChatLog<ChatMessage>>(queryKey);
      const before = log === undefined ? undefined : olderPageBefore(log);
      return before === undefined ? undefined : fetchMessages(before);
    },
    onSuccess: (older) => {
      if (older !== undefined) {
        queryClient.setQueryData<ChatLog<ChatMessage>>(queryKey, (log) =>
          log === undefined ? log : prependOlder(log, older),
        );
      }
    },
  });
}

/** The 100 messages before the oldest one shown. */
export function useLoadOlderChat() {
  return useLoadOlder(chatQueryKey, fetchWorldMessages);
}

/** The same in the guild chat. */
export function useLoadOlderGuildChat() {
  const guildId = useMyGuild().data?.guild?.id;
  return useLoadOlder(guildChatQueryKey(guildId ?? 0), fetchGuildMessages);
}

/** A message was hidden (the admin's or the leader's click, or `{hiddenId}` on a chat queue): it leaves the chat shown here. */
export function removeChatMessage(
  queryClient: QueryClient,
  messageId: number,
  queryKey: QueryKey = chatQueryKey,
): void {
  queryClient.setQueryData<ChatLog<ChatMessage>>(queryKey, (log) =>
    log === undefined ? log : withoutMessage(log, messageId),
  );
}

/** DELETE /api/guilds/mine/chat/{id} (leader): hides a message of the guild chat; the members' chats follow. */
export function useHideGuildChatMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.DELETE('/api/guilds/mine/chat/{messageId}', { params: { path: { messageId: id } } });
      return id;
    },
    onSuccess: (id) => {
      const guildId = myGuildIdIn(queryClient);
      if (guildId !== undefined) {
        removeChatMessage(queryClient, id, guildChatQueryKey(guildId));
      }
    },
  });
}

/** A read marker as `GET/PUT /api/chat/seen` and `/user/queue/chat-seen` send it (1.7). */
export type ChatSeen = components['schemas']['ChatSeenDto'];

/** What the server knows of the chats the player has read, on any of his devices (`ChatSeen[]`). */
export const chatSeenQueryKey = ['chatSeen'] as const;

export function fetchChatSeen(): Promise<ChatSeen[]> {
  return expectData(api.GET('/api/chat/seen'));
}

/** PUT /api/chat/seen: everything up to `id` is read (the world chat with `guildId` undefined). The answer is the marker as it stands. */
export function putChatSeen(guildId: number | undefined, id: number): Promise<ChatSeen> {
  return expectData(api.PUT('/api/chat/seen', { body: { guildId: guildId ?? null, id } }));
}

import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { mergeMessages, prependOlderMessages } from '../../game/messages';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type PrivateMessage = components['schemas']['MessageDto'];
export type Conversation = components['schemas']['ConversationDto'];
export type BlockedPlayer = components['schemas']['BlockedPlayerDto'];

/**
 * Everything about private messages: the inbox (`['messages', 'inbox']`), the unread counter
 * (`['messages', 'unread']`) and the conversations (`['messages', 'with', playerId]`). Invalidating
 * the prefix refreshes what is on screen – on a MESSAGE_RECEIVED notification, after a reconnect.
 */
export const messagesQueryKey = ['messages'] as const;
const inboxQueryKey = [...messagesQueryKey, 'inbox'] as const;
const unreadQueryKey = [...messagesQueryKey, 'unread'] as const;
export const blocksQueryKey = ['blocks'] as const;

export function conversationQueryKey(playerId: number) {
  return [...messagesQueryKey, 'with', playerId] as const;
}

/** GET /api/messages/conversations: the people the player talked to, newest first. */
export function useConversations() {
  return useQuery({
    queryKey: inboxQueryKey,
    queryFn: () => expectData(api.GET('/api/messages/conversations')),
  });
}

/** GET /api/messages/unread: the number on the navigation's Messages entry. */
export function useUnreadMessages() {
  return useQuery({
    queryKey: unreadQueryKey,
    queryFn: () => expectData(api.GET('/api/messages/unread')),
    select: (data) => data.unread,
  });
}

function fetchMessages(playerId: number, before?: number): Promise<PrivateMessage[]> {
  return expectData(api.GET('/api/messages/with/{playerId}', { params: { path: { playerId }, query: { before } } }));
}

/**
 * GET /api/messages/with/{playerId}: the newest page. It is merged into what is loaded already, so
 * older pages scrolled to survive a refresh. 404 PLAYER_NOT_FOUND for a player that doesn't exist.
 */
export function useConversation(playerId: number) {
  const queryClient = useQueryClient();
  const queryKey = conversationQueryKey(playerId);
  return useQuery({
    queryKey,
    queryFn: async () => {
      const latest = await fetchMessages(playerId);
      // read after the answer: a message may have been added meanwhile
      return mergeMessages(queryClient.getQueryData<PrivateMessage[]>(queryKey), latest);
    },
    retry: false,
  });
}

/** Scrolls back: the page before `before` goes in front of what is loaded. Resolves with its length. */
export async function loadOlderMessages(queryClient: QueryClient, playerId: number, before: number): Promise<number> {
  const older = await fetchMessages(playerId, before);
  queryClient.setQueryData<PrivateMessage[]>(conversationQueryKey(playerId), (existing) =>
    prependOlderMessages(existing ?? [], older),
  );
  return older.length;
}

/** POST /api/messages/with/{playerId}: the sent message is added to the conversation at once. */
export function useSendMessage(playerId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (text: string) =>
      expectData(api.POST('/api/messages/with/{playerId}', { params: { path: { playerId } }, body: { text } })),
    onSuccess: (message) => {
      queryClient.setQueryData<PrivateMessage[]>(conversationQueryKey(playerId), (existing) =>
        mergeMessages(existing, [message]),
      );
      void queryClient.invalidateQueries({ queryKey: inboxQueryKey });
    },
  });
}

/** POST …/read: everything the other player wrote counts as read; the counters follow. */
export function useMarkRead(playerId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api.POST('/api/messages/with/{playerId}/read', { params: { path: { playerId } } });
    },
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: unreadQueryKey }),
        queryClient.invalidateQueries({ queryKey: inboxQueryKey }),
      ]),
  });
}

/** GET /api/blocks: the players the player has blocked. */
export function useBlocks() {
  return useQuery({
    queryKey: blocksQueryKey,
    queryFn: () => expectData(api.GET('/api/blocks')),
  });
}

/** A block hides a conversation (and its unread messages), an unblock shows it again. */
function refreshAfterBlockChange(queryClient: QueryClient): Promise<unknown> {
  // loaded conversations may hold messages that are hidden now (or were): load them afresh
  queryClient.removeQueries({ queryKey: [...messagesQueryKey, 'with'], type: 'inactive' });
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: blocksQueryKey }),
    queryClient.invalidateQueries({ queryKey: messagesQueryKey }),
  ]);
}

/** PUT /api/blocks/{playerId}. */
export function useBlockPlayer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (playerId: number) => {
      await api.PUT('/api/blocks/{playerId}', { params: { path: { playerId } } });
    },
    // the server answers [] for a blocked player, but a conversation that is open keeps what it
    // loaded (see useConversation): empty it, or the blocked messages stay on screen
    onSuccess: (_, playerId) => queryClient.setQueryData<PrivateMessage[]>(conversationQueryKey(playerId), []),
    onSettled: () => refreshAfterBlockChange(queryClient),
  });
}

/** DELETE /api/blocks/{playerId}. */
export function useUnblockPlayer() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (playerId: number) => {
      await api.DELETE('/api/blocks/{playerId}', { params: { path: { playerId } } });
    },
    onSettled: () => refreshAfterBlockChange(queryClient),
  });
}

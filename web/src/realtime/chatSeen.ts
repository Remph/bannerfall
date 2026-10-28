import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useSyncExternalStore } from 'react';
import { useCurrentAccount } from '../api/queries/auth';
import {
  chatSeenQueryKey,
  fetchChatSeen,
  putChatSeen,
  useChat,
  useGuildChat,
  useGuildChatId,
  type ChatChannel,
  type ChatSeen,
} from '../api/queries/chat';
import { newestId, serverSeenId, unreadCount, withSeenMarker } from '../game/chat';

/**
 * The newest chat message the player has seen, stored per device, player and channel
 * (`bannerfall.chatSeen.<playerId>`, `bannerfall.guildChatSeen.<playerId>.<guildId>`), so messages
 * written while he was away count as unread after a reload too. A tiny store with listeners: every
 * badge re-renders when the chat is read. The guild's key holds the guild id: message ids are global,
 * so the seen id of an old guild would make the history of the next one look unread.
 *
 * Since 1.7 the server keeps the truth (`/api/chat/seen`), so a chat read on one device counts as read
 * on the others: what is stored here is the fast copy (no flicker on a reload), moved forward by what
 * the player reads here (and sent to the server) and by what the server tells (not sent back).
 */
const listeners = new Set<() => void>();

const STORAGE_PREFIX = 'bannerfall.chatSeen.';
const GUILD_STORAGE_PREFIX = 'bannerfall.guildChatSeen.';

/** The key of the world chat, or of the chat of guild `guildId`. */
export function storageKey(playerId: number, guildId?: number): string {
  return guildId === undefined ? `${STORAGE_PREFIX}${playerId}` : `${GUILD_STORAGE_PREFIX}${playerId}.${guildId}`;
}

/** The stored id; null when nothing is stored (first visit on this device). */
export function parseSeenId(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }
  return Number(value);
}

/** Only in memory when the storage is blocked (private mode): then it holds until a reload. */
const fallback = new Map<string, number>();

function loadSeenId(playerId: number, guildId?: number): number | null {
  try {
    return parseSeenId(localStorage.getItem(storageKey(playerId, guildId)));
  } catch {
    return fallback.get(storageKey(playerId, guildId)) ?? null;
  }
}

/** Stores `id` unless the device has a newer one; true when it moved forward. */
function storeSeen(playerId: number, id: number, guildId?: number): boolean {
  const seen = loadSeenId(playerId, guildId);
  if (seen !== null && seen >= id) {
    return false;
  }
  fallback.set(storageKey(playerId, guildId), id);
  try {
    localStorage.setItem(storageKey(playerId, guildId), String(id));
  } catch {
    // blocked: the in-memory value above is used
  }
  listeners.forEach((listener) => listener());
  return true;
}

/** How long a read marker waits before it goes to the server: reading a busy chat sends one PUT per second at most. */
export const SEEN_UPLOAD_DELAY_MS = 1000;

type SendSeen = (playerId: number, guildId: number | undefined, id: number) => Promise<unknown>;

/**
 * Sends the read markers to the server, at most one request per channel and `delayMs`: the newest id
 * queued meanwhile goes. A failed request is dropped; the next read (or the next load, which uploads
 * a device that is ahead) sends it again.
 */
export function createSeenUploader(send: SendSeen, delayMs: number) {
  const waiting = new Map<string, number>();
  return {
    queue(playerId: number, guildId: number | undefined, id: number): void {
      const key = storageKey(playerId, guildId);
      const queued = waiting.get(key);
      waiting.set(key, Math.max(queued ?? 0, id));
      if (queued !== undefined) {
        return; // the timer is running; it sends the newest
      }
      setTimeout(() => {
        const newest = waiting.get(key);
        waiting.delete(key);
        if (newest !== undefined) {
          send(playerId, guildId, newest).catch(() => undefined);
        }
      }, delayMs);
    },
  };
}

/** Remembers `id` as seen in the world chat, or in the chat of guild `guildId` (never goes back to an older one), and tells the server. */
export function markChatSeen(playerId: number, id: number, guildId?: number): void {
  // id 0 is an empty chat (first visit): nothing to tell, and the server refuses a marker below 1
  if (storeSeen(playerId, id, guildId) && id >= 1) {
    uploader.queue(playerId, guildId, id);
  }
}

/** The server's marker (from a load, a push or the answer to a PUT): moves the device forward, never sends it back. */
export function applyServerSeen(playerId: number, id: number, guildId?: number): void {
  storeSeen(playerId, id, guildId);
}

const uploader = createSeenUploader(async (playerId, guildId, id) => {
  const marker = await putChatSeen(guildId, id);
  // a device that was behind: the answer is where the marker really is
  applyServerSeen(playerId, marker.id, marker.guildId ?? undefined);
}, SEEN_UPLOAD_DELAY_MS);

/** Whether a `storage` event (another tab wrote) is about the seen chat message. */
export function isChatSeenKey(key: string | null): boolean {
  // null: the other tab cleared the whole storage
  return key === null || key.startsWith(STORAGE_PREFIX) || key.startsWith(GUILD_STORAGE_PREFIX);
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  // the chat read in another tab of the same browser clears the badges here too
  const onStorage = (event: StorageEvent) => {
    if (isChatSeenKey(event.key)) {
      listener();
    }
  };
  window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('storage', onStorage);
  };
}

/**
 * The server's read markers (1.7). Answered markers are put on the device before the query counts as
 * loaded, so the badges never see "loaded, but the device doesn't know yet".
 */
export function useChatSeenMarkers() {
  const me = useCurrentAccount();
  return useQuery({
    queryKey: chatSeenQueryKey,
    queryFn: async () => {
      const markers = await fetchChatSeen();
      for (const marker of markers) {
        applyServerSeen(me.playerId, marker.id, marker.guildId ?? undefined);
      }
      return markers;
    },
    staleTime: Infinity,
  });
}

/**
 * Once for the game screens (the realtime layer): a chat marker pushed from another device, and the
 * upload of this device's marker when it is ahead of the server (read here before 1.7, or while the
 * server couldn't be reached).
 */
export function useChatSeenSync(): (marker: ChatSeen) => void {
  const me = useCurrentAccount();
  const queryClient = useQueryClient();
  const guildId = useGuildChatId();
  const markers = useChatSeenMarkers().data;
  useEffect(() => {
    if (markers === undefined) {
      return;
    }
    for (const channel of guildId === undefined ? [undefined] : [undefined, guildId]) {
      const local = loadSeenId(me.playerId, channel);
      if (local !== null && local > (serverSeenId(markers, channel) ?? 0)) {
        uploader.queue(me.playerId, channel, local);
      }
    }
  }, [markers, guildId, me.playerId]);
  return (marker) => {
    applyServerSeen(me.playerId, marker.id, marker.guildId ?? undefined);
    queryClient.setQueryData<ChatSeen[]>(chatSeenQueryKey, (old) =>
      old === undefined ? old : withSeenMarker(old, marker),
    );
  };
}

/**
 * The number of messages of other players the player hasn't seen yet in one channel (0 for a guild
 * channel he can't use). In a channel nobody has read yet (neither this device nor the server knows
 * a marker) the whole history counts as seen, so nobody starts with "99+".
 */
export function useChannelUnread(channel: ChatChannel): number {
  const me = useCurrentAccount();
  const world = useChat();
  const guild = useGuildChat();
  const guildId = useGuildChatId();
  // until the server has answered (or failed) its marker may still turn up: no first visit yet
  const serverAnswered = useChatSeenMarkers().status !== 'pending';
  const seenIn = channel === 'guild' ? guildId : undefined;
  const usable = channel === 'world' || guildId !== undefined;
  const seen = useSyncExternalStore(subscribe, () => loadSeenId(me.playerId, seenIn));
  const messages = usable ? (channel === 'guild' ? guild : world).data?.messages : undefined;
  const firstVisit = seen === null && messages !== undefined && serverAnswered;
  useEffect(() => {
    if (firstVisit && messages !== undefined) {
      markChatSeen(me.playerId, newestId(messages), seenIn);
    }
  }, [firstVisit, me.playerId, messages, seenIn]);
  if (messages === undefined || seen === null) {
    return 0;
  }
  return unreadCount(messages, seen, me.playerId);
}

/** The unread messages in all the channels: the chat's badges. */
export function useChatUnread(): number {
  return useChannelUnread('world') + useChannelUnread('guild');
}

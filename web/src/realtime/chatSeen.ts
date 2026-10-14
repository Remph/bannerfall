import { useEffect, useSyncExternalStore } from 'react';
import { useCurrentAccount } from '../api/queries/auth';
import { useChat, useGuildChat, useGuildChatId, type ChatChannel } from '../api/queries/chat';
import { newestId, unreadCount } from '../game/chat';

/**
 * The newest chat message the player has seen, stored per device, player and channel
 * (`bannerfall.chatSeen.<playerId>`, `bannerfall.guildChatSeen.<playerId>.<guildId>`), so messages
 * written while he was away count as unread after a reload too. A tiny store with listeners: every
 * badge re-renders when the chat is read. The guild's key holds the guild id: message ids are global,
 * so the seen id of an old guild would make the history of the next one look unread.
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

/** Remembers `id` as seen in the world chat, or in the chat of guild `guildId` (never goes back to an older one). */
export function markChatSeen(playerId: number, id: number, guildId?: number): void {
  const seen = loadSeenId(playerId, guildId);
  if (seen !== null && seen >= id) {
    return;
  }
  fallback.set(storageKey(playerId, guildId), id);
  try {
    localStorage.setItem(storageKey(playerId, guildId), String(id));
  } catch {
    // blocked: the in-memory value above is used
  }
  listeners.forEach((listener) => listener());
}

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
 * The number of messages of other players the player hasn't seen yet in one channel (0 for a guild
 * channel he can't use). On the first visit on a device the whole history counts as seen, so nobody
 * starts with "99+".
 */
export function useChannelUnread(channel: ChatChannel): number {
  const me = useCurrentAccount();
  const world = useChat();
  const guild = useGuildChat();
  const guildId = useGuildChatId();
  const seenIn = channel === 'guild' ? guildId : undefined;
  const usable = channel === 'world' || guildId !== undefined;
  const seen = useSyncExternalStore(subscribe, () => loadSeenId(me.playerId, seenIn));
  const messages = usable ? (channel === 'guild' ? guild : world).data?.messages : undefined;
  const firstVisit = seen === null && messages !== undefined;
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

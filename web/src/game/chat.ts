/**
 * The world chat's history in the client (M9b) – pure, so it can be tested without a server.
 * `GET /api/chat/messages` answers the last 100 messages oldest first, `before` pages further back;
 * new messages come one by one over STOMP (`/topic/chat`).
 */

/** The fields the helpers need (the generated `ChatMessageDto` has them). */
export interface ChatLine {
  id: number;
  /** null: a broadcast by the host (M14d). */
  playerId: number | null;
}

/** What the `['chat']` query holds: the messages shown, oldest first, and whether older ones exist. */
export interface ChatLog<M extends ChatLine> {
  messages: M[];
  /** The first message of the chat is among `messages` (nothing to load further up). */
  complete: boolean;
}

/** The server's page size: fewer messages in an answer means the start of the chat was reached. */
export const CHAT_PAGE_SIZE = 100;

/** The server's limit for one message (after trimming, 04-api *Chat*). */
export const CHAT_MAX_LENGTH = 500;

/** From 450 characters on, the composer shows how many are left. */
export const CHAT_LENGTH_HINT_FROM = 450;

/**
 * The length as the server counts it: in code points, so an emoji is one character (JavaScript's
 * `length` counts UTF-16 units, two for most emoji).
 */
export function chatLength(text: string): number {
  return Array.from(text).length;
}

/** The text cut to `CHAT_MAX_LENGTH` code points (the field's limit; a pair is never split). */
export function clampChatText(text: string): string {
  return chatLength(text) <= CHAT_MAX_LENGTH ? text : Array.from(text).slice(0, CHAT_MAX_LENGTH).join('');
}

/** Messages sorted by id (= the order the server saved them), each id once. */
function byIdUnique<M extends ChatLine>(messages: readonly M[]): M[] {
  const seen = new Map<number, M>();
  for (const message of messages) {
    seen.set(message.id, message);
  }
  return [...seen.values()].sort((a, b) => a.id - b.id);
}

/** The first answer of the history (the newest page). */
export function chatLogFrom<M extends ChatLine>(latest: readonly M[]): ChatLog<M> {
  return { messages: byIdUnique(latest), complete: latest.length < CHAT_PAGE_SIZE };
}

/**
 * A fresh newest page (after a reconnect: messages sent meanwhile were never pushed) joined with
 * what is shown. If the two overlap, the older messages stay (scrolled-up history is not thrown
 * away), but within the fresh page's range only its messages count (hidden ones disappear); if the
 * fresh page starts after what is shown, only the fresh page is kept, since there may be a gap. Live messages newer than the fresh page (they came while it was loading) always stay.
 */
export function mergeLatest<M extends ChatLine>(existing: ChatLog<M> | undefined, latest: readonly M[]): ChatLog<M> {
  const fresh = chatLogFrom(latest);
  if (existing === undefined || existing.messages.length === 0) {
    return fresh;
  }
  const freshNewest = newestId(fresh.messages);
  const freshOldest = fresh.messages[0]?.id ?? 0;
  const overlaps = existing.messages.some((message) => message.id >= freshOldest && message.id <= freshNewest);
  if (fresh.complete || !overlaps) {
    // ids may skip (deleted messages), so "touching" can't be told from the ids: a gap is possible
    const liveAfter = existing.messages.filter((message) => message.id > freshNewest);
    return { messages: byIdUnique([...fresh.messages, ...liveAfter]), complete: fresh.complete };
  }
  // within the fresh page's range the fresh page is the truth: a shown message missing from it was
  // hidden (admin, guild leader) while the connection was down, and the hide was never pushed
  const outside = existing.messages.filter((message) => message.id < freshOldest || message.id > freshNewest);
  return { messages: byIdUnique([...outside, ...fresh.messages]), complete: existing.complete };
}

/** One page further back (`before` = the oldest id shown) put in front. */
export function prependOlder<M extends ChatLine>(existing: ChatLog<M>, older: readonly M[]): ChatLog<M> {
  return { messages: byIdUnique([...older, ...existing.messages]), complete: older.length < CHAT_PAGE_SIZE };
}

/** A live message from `/topic/chat` added at the end (a second delivery of the same id is ignored). */
export function appendLive<M extends ChatLine>(existing: ChatLog<M>, message: M): ChatLog<M> {
  if (existing.messages.some((shown) => shown.id === message.id)) {
    return existing;
  }
  return { ...existing, messages: byIdUnique([...existing.messages, message]) };
}

/** The id `before` for loading older messages, undefined when there are none. */
export function olderPageBefore(log: ChatLog<ChatLine>): number | undefined {
  return log.complete || log.messages.length === 0 ? undefined : log.messages[0]!.id;
}

/** The newest message's id (what the player has seen once the chat is open), 0 for an empty chat. */
export function newestId(messages: readonly ChatLine[]): number {
  return messages.length === 0 ? 0 : messages[messages.length - 1]!.id;
}

/** Messages of other players newer than the last one the player has seen: the chat's badge. */
export function unreadCount(messages: readonly ChatLine[], seenId: number, myPlayerId: number): number {
  return messages.filter((message) => message.id > seenId && message.playerId !== myPlayerId).length;
}

/** A read marker as the server keeps it (`ChatSeenDto`): null `guildId` = the world chat. */
export interface SeenMarker {
  guildId: number | null;
  id: number;
}

/** The id the server has for one channel (the world's with `guildId` undefined), null when it has none. */
export function serverSeenId(markers: readonly SeenMarker[], guildId?: number): number | null {
  return markers.find((marker) => marker.guildId === (guildId ?? null))?.id ?? null;
}

/** The markers with `marker` in them; a channel's marker never goes back to an older id. */
export function withSeenMarker(markers: readonly SeenMarker[], marker: SeenMarker): SeenMarker[] {
  const others = markers.filter((other) => other.guildId !== marker.guildId);
  const old = markers.find((other) => other.guildId === marker.guildId);
  return [...others, old !== undefined && old.id >= marker.id ? old : marker];
}

/** Whether a scrolled list is (almost) at its end: new messages scroll along only then. */
export function isNearBottom(scrollTop: number, clientHeight: number, scrollHeight: number): boolean {
  return scrollHeight - scrollTop - clientHeight < 48;
}

/** Messages of one player within this time form one block (name and time only above the first). */
export const CHAT_GROUP_MS = 5 * 60_000;

/**
 * Whether a message starts a new block: another player wrote the one before, or it was more than
 * five minutes earlier.
 */
export function startsGroup(
  previous: { playerId: number | null; createdAt: string } | undefined,
  message: { playerId: number | null; createdAt: string },
): boolean {
  return (
    previous === undefined ||
    previous.playerId !== message.playerId ||
    Date.parse(message.createdAt) - Date.parse(previous.createdAt) > CHAT_GROUP_MS
  );
}

/** The log without one message (the admin hid it). */
export function withoutMessage<M extends ChatLine>(log: ChatLog<M>, messageId: number): ChatLog<M> {
  return { ...log, messages: log.messages.filter((message) => message.id !== messageId) };
}

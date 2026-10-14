/** Private messages (02-game-rules §17): the pure parts. */

/** The server sends the newest 100 messages of a conversation per request. */
export const MESSAGES_PAGE_SIZE = 100;

export interface MessageLine {
  id: number;
}

/**
 * The messages of a conversation after loading the newest page again: what was loaded before
 * (older pages scrolled to, a message just sent) stays, the fresh page wins for the same ids.
 * Oldest first, no duplicates.
 */
export function mergeMessages<M extends MessageLine>(existing: readonly M[] | undefined, latest: readonly M[]): M[] {
  const byId = new Map<number, M>();
  for (const message of existing ?? []) {
    byId.set(message.id, message);
  }
  for (const message of latest) {
    byId.set(message.id, message);
  }
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

/** An older page was loaded: it goes in front, anything already there is kept as it is. */
export function prependOlderMessages<M extends MessageLine>(existing: readonly M[], older: readonly M[]): M[] {
  return mergeMessages(older, existing);
}

/**
 * The id to ask for when scrolling back, or undefined if there is nothing older: a full first page
 * means there may be more.
 */
export function olderMessagesBefore(
  messages: readonly MessageLine[],
  pageSize = MESSAGES_PAGE_SIZE,
): number | undefined {
  return messages.length >= pageSize ? messages[0]?.id : undefined;
}

/**
 * The id of the newest message the other player wrote, 0 if there is none: when it changes, there
 * is something new to mark as read.
 */
export function newestIncomingId(messages: readonly (MessageLine & { mine: boolean })[]): number {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message !== undefined && !message.mine) {
      return message.id;
    }
  }
  return 0;
}

/** Whether the path is the open conversation with that player: a toast would only repeat what is on screen. */
export function isConversationPath(pathname: string, playerId: number): boolean {
  return pathname === `/messages/${playerId}`;
}

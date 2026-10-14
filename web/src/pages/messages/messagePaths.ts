/** `/messages`: the inbox. */
export const MESSAGES_PATH = '/messages';

/** `/messages/12`: the conversation with a player. */
export function conversationPath(playerId: number): string {
  return `/messages/${playerId}`;
}

/** `/messages/12` → 12; anything else → undefined. */
export function parseConversationPlayerId(param: string | undefined): number | undefined {
  return param !== undefined && /^\d{1,15}$/.test(param) && Number(param) > 0 ? Number(param) : undefined;
}

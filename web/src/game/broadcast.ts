/**
 * The host's popup messages in the client (1.7) – pure, so it can be tested without a server.
 * `GET /api/broadcasts` answers the last ten minutes oldest first, new ones come over STOMP
 * (`/topic/broadcast`). The ids are send times in milliseconds and only grow, so the newest id
 * the player dismissed says which ones he has seen.
 */

/** The fields the helpers need (the generated `BroadcastDto` has them). */
export interface BroadcastLine {
  id: number;
}

/** The server keeps 20; the client never needs more. */
export const BROADCASTS_KEPT = 20;

/** `list` plus `incoming`, oldest first and without doubles (the same one comes by REST and STOMP). */
export function withBroadcast<B extends BroadcastLine>(list: readonly B[], incoming: B): B[] {
  if (list.some((broadcast) => broadcast.id === incoming.id)) {
    return [...list];
  }
  return [...list, incoming].sort((a, b) => a.id - b.id).slice(-BROADCASTS_KEPT);
}

/** The broadcasts the player has not dismissed yet; `seenId` null = none dismissed on this device. */
export function unseenBroadcasts<B extends BroadcastLine>(list: readonly B[], seenId: number | null): B[] {
  return seenId === null ? [...list] : list.filter((broadcast) => broadcast.id > seenId);
}

/** The newest id of the list, null when it is empty. */
export function newestBroadcastId(list: readonly BroadcastLine[]): number | null {
  return list.reduce<number | null>((newest, { id }) => (newest === null || id > newest ? id : newest), null);
}

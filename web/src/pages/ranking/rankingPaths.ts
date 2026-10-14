/** `/ranking`, `/ranking?page=2`: pages are 1-based in the URL, 0-based for the server. */
export function rankingPath(page: number): string {
  return page <= 0 ? '/ranking' : `/ranking?page=${page + 1}`;
}

/** `/players/12`: a player's profile. */
export function playerPath(playerId: number): string {
  return `/players/${playerId}`;
}

/** `/players/12` → 12; anything else → undefined. */
export function parsePlayerId(param: string | undefined): number | undefined {
  return param !== undefined && /^\d{1,15}$/.test(param) && Number(param) > 0 ? Number(param) : undefined;
}

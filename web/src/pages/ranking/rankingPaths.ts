import type { RankingKind } from '../../api/queries/ranking';

/** The ranking tabs (1.5) in their order, and their name in the URL. */
export const RANKING_KINDS: readonly RankingKind[] = ['POINTS', 'ATTACKER', 'DEFENDER', 'PLUNDERER'];

/**
 * `/ranking`, `/ranking?page=2`, `/ranking?by=attacker&page=2`: pages are 1-based in the URL,
 * 0-based for the server; the points tab has no `by`.
 */
export function rankingPath(page: number, by: RankingKind = 'POINTS'): string {
  const params = new URLSearchParams();
  if (by !== 'POINTS') {
    params.set('by', by.toLowerCase());
  }
  if (page > 0) {
    params.set('page', String(page + 1));
  }
  const query = params.toString();
  return query === '' ? '/ranking' : `/ranking?${query}`;
}

/** The tab of `?by=` (the points for anything unknown). */
export function rankingKindFromSearch(params: URLSearchParams): RankingKind {
  const by = params.get('by')?.toUpperCase();
  return RANKING_KINDS.find((kind) => kind === by) ?? 'POINTS';
}

/** `/players/12`: a player's profile. */
export function playerPath(playerId: number): string {
  return `/players/${playerId}`;
}

/** `/statistics`: the world statistics (1.5). */
export const STATISTICS_PATH = '/statistics';

/** `/players/12` → 12; anything else → undefined. */
export function parsePlayerId(param: string | undefined): number | undefined {
  return param !== undefined && /^\d{1,15}$/.test(param) && Number(param) > 0 ? Number(param) : undefined;
}

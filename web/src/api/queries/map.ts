import { useQueries, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { useEffect } from 'react';
import { refetchDelayMs } from '../../game/buildQueue';
import { nextMovementArrivalMs } from '../../game/mapMovements';
import { chunkArea, type Chunk } from '../../game/mapView';
import { serverNow } from '../../game/serverTime';
import { api, expectData } from '../client';
import type { components } from '../schema';

/** One village of GET /api/map. Barbarian villages have no owner and usually no name (those fields are null). */
export type MapVillage = components['schemas']['MapVillageDto'];

/**
 * One entry of GET /api/map/movements (M26): a village the player's own movements head to – his
 * attacks and supports, and what other players send to his villages – with the counts per kind.
 */
export type MapMovement = components['schemas']['MapMovementDto'];

/** Prefix of all map queries, e.g. to reload the map after a village was renamed. */
export const mapQueryKey = ['mapChunk'] as const;

export function mapChunkQueryKey(chunk: Chunk) {
  return [...mapQueryKey, chunk.cx, chunk.cy] as const;
}

export interface MapVillages {
  villages: MapVillage[];
  /** At least one chunk is still loading for the first time. */
  isPending: boolean;
  /** The error of a chunk that couldn't be loaded, else null. */
  error: Error | null;
  /** Loads the failed chunks again. */
  retry: () => void;
}

/**
 * The villages of the given chunks, one query per 20 × 20 chunk (05-frontend §4, §6): panning
 * only loads the chunks that come into view, the others come from the cache.
 */
export function useMapVillages(chunks: readonly Chunk[]): MapVillages {
  return useQueries({
    queries: chunks.map((chunk) => ({
      queryKey: mapChunkQueryKey(chunk),
      queryFn: (): Promise<MapVillage[]> => expectData(api.GET('/api/map', { params: { query: chunkArea(chunk) } })),
      // The map changes slowly (new villages, names, points); a minute old is fine.
      staleTime: 60_000,
    })),
    combine: combineChunks,
  });
}

// Outside the component, so its identity never changes: TanStack Query then only combines again when a result changed.
function combineChunks(results: UseQueryResult<MapVillage[]>[]): MapVillages {
  const failed = results.filter((result) => result.isError);
  return {
    villages: results.flatMap((result) => result.data ?? []),
    isPending: results.some((result) => result.isPending),
    error: failed[0]?.error ?? null,
    retry: () => failed.forEach((result) => void result.refetch()),
  };
}

/**
 * Sending, cancelling or an arrival changes the movement markers: wherever troops lists are
 * reloaded, this is reloaded too (troops.ts, farm.ts, the notifications).
 */
export const mapMovementsQueryKey = ['mapMovements'] as const;

/**
 * GET /api/map/movements: the markers of the map, while they are switched on. The server pushes
 * attacks on the player's villages (INCOMING_ATTACK) and changes to them. A support another player
 * sends him has no push, so the open map asks again every minute; arrivals reload it by themselves
 * (below).
 */
export function useMapMovements(enabled: boolean) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: mapMovementsQueryKey,
    queryFn: (): Promise<MapMovement[]> => expectData(api.GET('/api/map/movements')),
    enabled,
    staleTime: 60_000,
    refetchInterval: 60_000,
  });
  const nextArrival = query.data === undefined ? undefined : nextMovementArrivalMs(query.data);
  const { dataUpdatedAt, errorUpdatedAt } = query;

  // One timer for the next arrival, like the rally point's (troops.ts): the marker goes when the
  // worker has handled it. Restarted by every answer, so a late worker means asking again.
  useEffect(() => {
    if (!enabled || nextArrival === undefined) {
      return;
    }
    const timer = setTimeout(
      () => void queryClient.invalidateQueries({ queryKey: mapMovementsQueryKey }),
      refetchDelayMs(nextArrival, serverNow()),
    );
    return () => clearTimeout(timer);
  }, [queryClient, enabled, nextArrival, dataUpdatedAt, errorUpdatedAt]);

  return enabled ? (query.data ?? NO_MOVEMENTS) : NO_MOVEMENTS;
}

// One empty list for every render, so the map's drawing effect doesn't run again for a new [].
const NO_MOVEMENTS: MapMovement[] = [];

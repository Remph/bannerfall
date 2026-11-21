import { useQuery } from '@tanstack/react-query';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type Chronicle = components['schemas']['ChronicleDto'];
export type ChronicleEntry = components['schemas']['ChronicleEntry'];
export type ChronicleFacts = components['schemas']['ChronicleFacts'];
export type ChronicleSide = components['schemas']['ChronicleSide'];
export type ChroniclePlayer = components['schemas']['ChroniclePlayer'];
export type ChronicleMap = components['schemas']['ChronicleMap'];
export type ChronicleMapSide = components['schemas']['ChronicleMapSide'];

export const chronicleQueryKey = ['chronicle'] as const;

/**
 * GET /api/chronicle (2.1 I1, 02-game-rules §32): the story of the world once it has ended. Nothing
 * in it changes any more, so it is read once.
 */
export function useChronicle(enabled = true) {
  return useQuery({
    queryKey: chronicleQueryKey,
    queryFn: () => expectData(api.GET('/api/chronicle')),
    enabled,
    staleTime: Infinity,
  });
}

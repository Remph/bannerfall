import { useMutation } from '@tanstack/react-query';
import type { SimulatorBody } from '../../game/simulator';
import { api, expectData } from '../client';
import type { components } from '../schema';

export type Simulation = components['schemas']['SimulationDto'];
export type SimulatedBattle = Simulation['scenarios'][number];

/**
 * POST /api/simulator: the same battle with the worst luck, none and the best luck. Nothing is
 * saved on the server, so it is a mutation without any cache to refresh.
 */
export function useSimulate() {
  return useMutation({
    mutationFn: (body: SimulatorBody) => expectData(api.POST('/api/simulator', { body })),
  });
}

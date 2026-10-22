import type { IncomingMovement } from '../../api/queries/troops';
import { isOnMap, parseCoords, type Coords } from '../../game/coords';

/** `/v/1/rally?x=48&y=52`: the rally point, with a target to fill in (05-frontend §3). */
export function rallyPath(villageId: number, target?: Coords): string {
  return target === undefined ? `/v/${villageId}/rally` : `/v/${villageId}/rally?x=${target.x}&y=${target.y}`;
}

/**
 * `/v/1/map?x=48&y=52&focus=1`: the map zoomed in on a field with that field outlined (the village
 * links of the rally point, the farm list, profiles; `focus` since 1.6.2). The map drops `focus` from
 * the address once it has opened, so a reload keeps the player's own view.
 */
export function mapPath(villageId: number, field: Coords): string {
  return `/v/${villageId}/map?x=${field.x}&y=${field.y}&focus=1`;
}

/** The field a village link ({@link mapPath}) points at: `?x=&y=&focus=1` and a field of the map; else nothing. */
export function mapFocusFromSearch(params: URLSearchParams, mapSize: number): Coords | undefined {
  if (params.get('focus') !== '1') {
    return undefined;
  }
  const field = parseCoords(`${params.get('x') ?? ''}|${params.get('y') ?? ''}`);
  return field !== undefined && isOnMap(field, mapSize) ? field : undefined;
}

/** The target field's first text from `?x=&y=`: "48|52", or '' if they are missing or no coordinates. */
export function targetFromSearch(params: URLSearchParams): string {
  const field = parseCoords(`${params.get('x') ?? ''}|${params.get('y') ?? ''}`);
  return field === undefined ? '' : `${field.x}|${field.y}`;
}

/**
 * `/v/1/rally/simulator`, `/v/1/rally/simulator?report=12`: the attack simulator (M23), optionally
 * prefilled from a battle report.
 */
export function simulatorPath(villageId: number, reportId?: number): string {
  return reportId === undefined
    ? `/v/${villageId}/rally/simulator`
    : `/v/${villageId}/rally/simulator?report=${reportId}`;
}

/** The rally point's parts behind the switch on phones and tablets (1.3). */
export type RallyPart = 'send' | 'moves' | 'troops';

/** The addresses that open the movements: `#incoming` (the attack notice), `#outgoing` (the map's line panel). */
export function opensMoves(hash: string): boolean {
  return hash === '#incoming' || hash === '#outgoing';
}

/**
 * Which part the rally point opens on: the form when the address names a target (map, farm list),
 * the movements while a hostile attack is coming in or for `#incoming` and `#outgoing`, else the form.
 */
export function initialRallyPart(
  incoming: readonly Pick<IncomingMovement, 'kind' | 'units'>[],
  params: URLSearchParams,
  hash: string,
): RallyPart {
  if (targetFromSearch(params) !== '') {
    return 'send';
  }
  // own later waves on a village conquered meanwhile show their units: not a threat
  const attacked = incoming.some((movement) => movement.kind === 'ATTACK' && movement.units === null);
  return attacked || opensMoves(hash) ? 'moves' : 'send';
}

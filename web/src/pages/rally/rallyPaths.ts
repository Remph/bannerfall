import { parseCoords, type Coords } from '../../game/coords';

/** `/v/1/rally?x=48&y=52`: the rally point, with a target to fill in (05-frontend §3). */
export function rallyPath(villageId: number, target?: Coords): string {
  return target === undefined ? `/v/${villageId}/rally` : `/v/${villageId}/rally?x=${target.x}&y=${target.y}`;
}

/** `/v/1/map?x=48&y=52`: the map centred on a field (the coordinates in the rally point's lists). */
export function mapPath(villageId: number, field: Coords): string {
  return `/v/${villageId}/map?x=${field.x}&y=${field.y}`;
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

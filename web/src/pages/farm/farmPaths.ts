import type { Coords } from '../../game/coords';
import { FARM_TEMPLATE_MAX, farmUnitTypes } from '../../game/farm';
import type { UnitMap } from '../../game/troops';

/** `/v/1/farm?x=48&y=52`: the farm list of a village, with a target to add filled in (05-frontend §3). */
export function farmPath(villageId: number, target?: Coords): string {
  return target === undefined ? `/v/${villageId}/farm` : `/v/${villageId}/farm?x=${target.x}&y=${target.y}`;
}

/**
 * The link state that fills in the add form's units too (M38: an attack report offers the units it
 * sent). Router state, not the URL: a template is not worth a shareable address.
 */
export function farmUnitsState(units: UnitMap): { farmUnits: UnitMap } {
  return { farmUnits: units };
}

/**
 * The units from `farmUnitsState`, or {} for any other state. Only template types and counts the
 * server accepts: the state survives reloads in the browser's history, so it is checked like input.
 */
export function unitsFromState(state: unknown): UnitMap {
  if (typeof state !== 'object' || state === null || !('farmUnits' in state)) {
    return {};
  }
  const units = state.farmUnits;
  if (typeof units !== 'object' || units === null) {
    return {};
  }
  const result: UnitMap = {};
  for (const type of farmUnitTypes) {
    const count: unknown = (units as Record<string, unknown>)[type];
    if (typeof count === 'number' && Number.isInteger(count) && count > 0) {
      result[type] = Math.min(count, FARM_TEMPLATE_MAX);
    }
  }
  return result;
}

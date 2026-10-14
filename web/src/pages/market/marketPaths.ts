import type { Coords } from '../../game/coords';

/** `/v/1/market?x=48&y=52`: the market, with a target to send resources to (the map's button). */
export function marketPath(villageId: number, target?: Coords): string {
  return target === undefined ? `/v/${villageId}/market` : `/v/${villageId}/market?x=${target.x}&y=${target.y}`;
}

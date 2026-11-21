import type { Coords } from '../../game/coords';
import { resourceTypes, type ResourceAmounts } from '../../game/resources';

/** `/v/1/market?x=48&y=52`: the market, with a target to send resources to (the map's button). */
export function marketPath(villageId: number, target?: Coords): string {
  return target === undefined ? `/v/${villageId}/market` : `/v/${villageId}/market?x=${target.x}&y=${target.y}`;
}

/**
 * `/v/1/market?x=48&y=52&wood=400&iron=200`: the market with the target and the amounts filled in
 * (2.1 S4: "Send resources" on an ally's help request).
 */
export function marketSendPath(villageId: number, target: Coords, amounts: ResourceAmounts): string {
  const list = resourceTypes
    .filter((type) => amounts[type] > 0)
    .map((type) => `&${type}=${amounts[type]}`)
    .join('');
  return `${marketPath(villageId, target)}${list}`;
}

/** The amounts of `?wood=&clay=&iron=` ({@link marketSendPath}): whole numbers above 0, else 0. */
export function amountsFromSearch(params: URLSearchParams): ResourceAmounts {
  const read = (type: keyof ResourceAmounts) => {
    const value = Number(params.get(type) ?? '');
    return Number.isInteger(value) && value > 0 ? value : 0;
  };
  return { wood: read('wood'), clay: read('clay'), iron: read('iron') };
}

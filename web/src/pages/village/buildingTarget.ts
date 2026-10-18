import { recruitingBuildings, type BuildingType } from '../../game/gameData';
import { bannersPath } from '../banners/bannerPaths';
import { marketPath } from '../market/marketPaths';
import { rallyPath } from '../rally/rallyPaths';
import { recruitPath } from '../recruit/recruitPaths';
import { smithyPath } from '../smithy/smithyPaths';
import { warehousePath } from '../warehouse/warehousePaths';

/**
 * Where a building's row on the village page leads: a built building with its own
 * screen opens it (barracks, stable, workshop and academy → recruiting, rally point, market, warehouse, smithy); the Town Hall
 * opens the Town Hall; the warehouse opens its page (M22: stock and when each resource is full); everything else, and anything not built yet, shows its place in the Town Hall,
 * where it can be built.
 */
export function buildingTarget(
  villageId: number,
  type: BuildingType,
  level: number,
  bannersOn = true,
): { pathname: string; hash?: string } {
  if (type === 'HEADQUARTERS') {
    return { pathname: `/v/${villageId}/hq` };
  }
  if (level > 0) {
    if (type === 'MARKET') {
      return { pathname: marketPath(villageId) };
    }
    if (type === 'RALLY_POINT') {
      return { pathname: rallyPath(villageId) };
    }
    if (type === 'WAREHOUSE') {
      return { pathname: warehousePath(villageId) };
    }
    if (type === 'SMITHY') {
      // M30: what every unit can do and, with unit levels, the research
      return { pathname: smithyPath(villageId) };
    }
    if (type === 'BANNER_HALL' && bannersOn) {
      // M32a: the stash and the slot of this village; without banners it only trains the paladin
      // (1.4), so it opens its recruiting tab below
      return { pathname: bannersPath(villageId) };
    }
    const recruiting = recruitingBuildings.find((building) => building === type);
    if (recruiting !== undefined) {
      return { pathname: recruitPath(villageId, recruiting) };
    }
  }
  return { pathname: `/v/${villageId}/hq`, hash: type };
}

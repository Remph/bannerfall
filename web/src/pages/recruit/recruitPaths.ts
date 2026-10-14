import { recruitingBuildings, type RecruitingBuilding } from '../../game/gameData';

/** `/v/1/recruit/barracks`: the building is part of the URL in lower case (05-frontend §3). */
export function recruitPath(villageId: number, building: RecruitingBuilding): string {
  return `/v/${villageId}/recruit/${building.toLowerCase()}`;
}

/** 'stable' → 'STABLE'; undefined for anything that isn't a recruiting building. */
export function parseRecruitBuilding(slug: string | undefined): RecruitingBuilding | undefined {
  return recruitingBuildings.find((building) => building.toLowerCase() === slug);
}

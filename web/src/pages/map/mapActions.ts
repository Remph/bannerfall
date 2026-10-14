import type { Coords } from '../../game/coords';
import type { VillageKind } from '../../game/mapView';
import { farmPath } from '../farm/farmPaths';
import { marketPath } from '../market/marketPaths';
import { playerPath } from '../ranking/rankingPaths';
import { rallyPath } from '../rally/rallyPaths';

export type MapActionKey = 'open' | 'attack' | 'farm' | 'support' | 'sendResources' | 'profile';

export interface MapAction {
  key: MapActionKey;
  /** Where the button leads. Missing = that screen doesn't exist yet, the button shows "coming soon". */
  to?: string;
}

/**
 * The buttons of the village panel (05-frontend §6), depending on whose village it is. Each
 * milestone that builds a target screen fills in its link here: the rally point since M7b (attack
 * and support open it from the current village with the target filled in), the market since M13b
 * ("Send resources" opens the market with the target filled in), the owner's profile since M14a.
 */
export function mapActions(
  village: { id: number; playerId: number | null } & Coords,
  kind: VillageKind,
  currentVillageId: number,
): MapAction[] {
  const rally = rallyPath(currentVillageId, village);
  const market = marketPath(currentVillageId, village);
  const farm = farmPath(currentVillageId, village);
  const profile = village.playerId === null ? undefined : playerPath(village.playerId);
  switch (kind) {
    case 'own':
      // nobody supports or trades with the village they are sending from
      return village.id === currentVillageId
        ? [{ key: 'open', to: `/v/${village.id}` }]
        : [
            { key: 'open', to: `/v/${village.id}` },
            { key: 'support', to: rally },
            { key: 'sendResources', to: market },
          ];
    case 'player':
    case 'npc':
      return [
        { key: 'attack', to: rally },
        { key: 'farm', to: farm },
        { key: 'support', to: rally },
        { key: 'sendResources', to: market },
        { key: 'profile', to: profile },
      ];
    case 'barbarian':
      return [
        { key: 'attack', to: rally },
        { key: 'farm', to: farm },
      ];
  }
}

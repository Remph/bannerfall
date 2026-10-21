import type { components } from '../api/schema';
import { formatCoords, formatNumber } from '../game/format';
import type { Dictionary } from '../i18n/en';
import type { Language } from '../i18n/language';

type Schemas = components['schemas'];

/**
 * The words for ATTACKS_RETURNED (1.6): one attack back or lost, a train or a farm list run; the
 * toast and the push say the same. `village` is the home village as the caller names it.
 */
export function attacksReturnedText(
  event: Schemas['AttacksReturned'],
  village: string,
  t: Dictionary,
  language: Language,
): string {
  const total = event.loot.wood + event.loot.clay + event.loot.iron;
  const loot = total > 0 ? formatNumber(total, language) : null;
  const from =
    event.target === null
      ? null
      : `${event.target.name ?? t.map.barbarianVillage} ${formatCoords(event.target.x, event.target.y)}`;
  if (event.attacks === 1 && from !== null) {
    return event.lost === 1 ? t.realtime.nobodyBack(from) : t.realtime.troopsBack(village, from, loot);
  }
  return t.realtime.attacksBack(event.attacks, village, event.farmRun ? null : from, loot, event.lost);
}

const resourceKeys = { WOOD: 'wood', CLAY: 'clay', IRON: 'iron' } as const;

/** The words for STORAGE_FULL (1.6): "Warehouse full in Hilltop: wood, clay." */
export function storageFullText(event: Schemas['StorageFull'], village: string, t: Dictionary): string {
  return t.realtime.storageFull(village, event.full.map((resource) => t.resources[resourceKeys[resource]]).join(', '));
}

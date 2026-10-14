import type { VillageRef } from '../../api/queries/troops';
import { formatCoords } from '../../game/format';
import type { Dictionary } from '../../i18n/en';

/** "Hilltop (48|52)" as plain text (messages, screen-reader names). Barbarian villages have no name. */
export function villageLabel(t: Dictionary, village: VillageRef): string {
  return `${village.name ?? t.map.barbarianVillage} ${formatCoords(village.x, village.y)}`;
}

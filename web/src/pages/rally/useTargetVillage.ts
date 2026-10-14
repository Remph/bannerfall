import { useMapVillages, type MapVillage } from '../../api/queries/map';
import type { Village } from '../../api/queries/villages';
import { distance, type Coords } from '../../game/coords';
import { formatDecimal } from '../../game/format';
import { CHUNK_SIZE } from '../../game/mapView';
import { useI18n } from '../../i18n';

/** What is known about the village on the target field; `text` is shown under the field. */
export type TargetVillage =
  | { kind: 'unknown' } // no valid field typed, or the map couldn't be loaded
  | { kind: 'loading' }
  | { kind: 'none' }
  | { kind: 'found'; text: string; village: MapVillage; distance: number };

/**
 * The village on the target field, from the map's cached 20 × 20 chunk (GET /api/map): shown under
 * the field while typing, before anything is sent – "Hilltop · ben · 12.3 fields" (also used by
 * the market's send form).
 */
export function useTargetVillage(field: Coords | undefined, from: Village): TargetVillage {
  const { t, language } = useI18n();
  const chunks =
    field === undefined ? [] : [{ cx: Math.floor(field.x / CHUNK_SIZE), cy: Math.floor(field.y / CHUNK_SIZE) }];
  const map = useMapVillages(chunks);
  if (field === undefined || map.error !== null) {
    return { kind: 'unknown' };
  }
  if (map.isPending) {
    return { kind: 'loading' };
  }
  const found = map.villages.find((entry) => entry.x === field.x && entry.y === field.y);
  if (found === undefined) {
    return { kind: 'none' };
  }
  if (found.id === from.id) {
    return { kind: 'found', text: t.rally.thisVillage, village: found, distance: 0 };
  }
  const fields = distance(from, found);
  const parts = [
    found.name ?? t.map.barbarianVillage,
    found.playerName, // null for barbarians
    t.map.fields(formatDecimal(fields, language)),
  ];
  return { kind: 'found', text: parts.filter((part) => part !== null).join(' · '), village: found, distance: fields };
}

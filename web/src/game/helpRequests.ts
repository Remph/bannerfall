import type { HelpRequest } from '../api/queries/diplomacy';
import type { Dictionary } from '../i18n/en';
import type { Language } from '../i18n/language';
import { formatCoords, formatNumber } from './format';

/** "Wulfsburg (48|52)" */
export function helpVillageLabel(village: HelpRequest['village']): string {
  return `${village.name ?? ''} ${formatCoords(village.x, village.y)}`.trim();
}

/** What an ally asks for (2.1 S4), for the strip and the sheet: "300 defenders at Wulfsburg (48|52)". */
export function helpNeed(t: Pick<Dictionary, 'diplomacy'>, request: HelpRequest, language: Language): string {
  const village = helpVillageLabel(request.village);
  return request.kind === 'SUPPORT'
    ? t.diplomacy.help.needSupport(formatNumber(request.defenders, language), village)
    : t.diplomacy.help.needResources(request.goal ?? '', village);
}

/** The need as the sheet's own line: with a capital ("Resources for the wall at …"; UI review 2.1). */
export function helpNeedLine(t: Pick<Dictionary, 'diplomacy'>, request: HelpRequest, language: Language): string {
  const need = helpNeed(t, request, language);
  return need.charAt(0).toLocaleUpperCase(language) + need.slice(1);
}

/**
 * The requests still open at `now` (game time), the nearest deadline first. One whose deadline has
 * passed is gone at once, although the server only ends it in the lord's next round.
 */
export function byDeadline(requests: readonly HelpRequest[], now: number): HelpRequest[] {
  return requests
    .filter((request) => Date.parse(request.deadline) > now)
    .sort((a, b) => Date.parse(a.deadline) - Date.parse(b.deadline) || a.id - b.id);
}

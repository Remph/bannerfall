import { formatShare } from './format';
import type { Language } from '../i18n/language';

/** The lowest morale a host can set and the simulator takes, in percent (1.6, 02-game-rules §8.2). */
export const MIN_MORALE = 30;

/** 0.48 → "48%" (en) / "48 %" (de): a morale factor as the whole percent the server rounds it to. */
export function formatMorale(morale: number, language: Language): string {
  return formatShare(Math.round(morale * 100), language);
}

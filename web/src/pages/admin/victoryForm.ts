import type { AdminWorldSettings, WorldSettingsUpdate } from '../../api/queries/admin';
import type { VictoryMode } from '../../api/queries/world';
import { worldFromForm, worldToForm } from './worldForm';

/** The victory settings (v1.3, 02-game-rules §22) as the card holds them: numbers as the text in the field. */
export interface VictoryForm {
  victoryMode: VictoryMode;
  dominationPercent: string;
  victoryHoldHours: string;
  keepCount: string;
  keepPercent: string;
}

export const VICTORY_MODES: readonly VictoryMode[] = ['DOMINATION', 'KEEPS', 'NONE'];

export function victoryToForm(settings: AdminWorldSettings): VictoryForm {
  return {
    victoryMode: settings.victoryMode,
    dominationPercent: String(settings.dominationPercent),
    victoryHoldHours: String(settings.victoryHoldHours),
    keepCount: String(settings.keepCount),
    keepPercent: String(settings.keepPercent),
  };
}

/** A whole number in [min, max], or null (the server's @Min/@Max of `WorldSettingsUpdateRequest`). */
function whole(text: string, min: number, max: number): number | null {
  const value = Number(text);
  if (text.trim() === '' || !Number.isInteger(value) || value < min || value > max) {
    return null;
  }
  return value;
}

/**
 * The request that saves the card: the stored settings with only the victory settings replaced, so
 * the card never sends what someone has half typed in the world settings card. Null while a field is
 * not valid (share 10–100 %, hold 1–720 hours, 1–12 keeps, keep share 1–100 %).
 */
export function victoryRequest(settings: AdminWorldSettings, form: VictoryForm): WorldSettingsUpdate | null {
  const dominationPercent = whole(form.dominationPercent, 10, 100);
  const victoryHoldHours = whole(form.victoryHoldHours, 1, 720);
  const keepCount = whole(form.keepCount, 1, 12);
  const keepPercent = whole(form.keepPercent, 1, 100);
  // the stored settings were valid when they were saved
  const stored = worldFromForm(worldToForm(settings), settings.phase === 'PREGAME');
  if (
    dominationPercent === null ||
    victoryHoldHours === null ||
    keepCount === null ||
    keepPercent === null ||
    stored === null
  ) {
    return null;
  }
  return {
    ...stored,
    victoryMode: form.victoryMode,
    dominationPercent,
    victoryHoldHours,
    keepCount,
    keepPercent,
  };
}

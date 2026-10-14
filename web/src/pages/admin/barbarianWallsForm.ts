import type { AdminWorldSettings, WorldSettingsUpdate } from '../../api/queries/admin';
import { worldFromForm, worldToForm } from './worldForm';

/** The barbarian wall settings (M29) as the card holds them: numbers as the text in the field. */
export interface BarbarianWallsForm {
  barbarianWallsEnabled: boolean;
  /** Real days after the world opened before the walls start to grow. */
  barbarianWallStartDays: string;
  barbarianWallMaxLevel: string;
  barbarianWallsRebuild: boolean;
}

export function barbarianWallsToForm(settings: AdminWorldSettings): BarbarianWallsForm {
  return {
    barbarianWallsEnabled: settings.barbarianWallsEnabled,
    barbarianWallStartDays: String(settings.barbarianWallStartDays),
    barbarianWallMaxLevel: String(settings.barbarianWallMaxLevel),
    barbarianWallsRebuild: settings.barbarianWallsRebuild,
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
 * The request that saves the card: the stored settings with only the wall settings replaced, so the
 * card never sends what someone has half typed in the world settings card. Null while a field is
 * not valid (0–365 days, wall level 0–20).
 */
export function barbarianWallsRequest(
  settings: AdminWorldSettings,
  form: BarbarianWallsForm,
): WorldSettingsUpdate | null {
  const days = whole(form.barbarianWallStartDays, 0, 365);
  const maxLevel = whole(form.barbarianWallMaxLevel, 0, 20);
  // the stored settings were valid when they were saved
  const stored = worldFromForm(worldToForm(settings), settings.phase === 'PREGAME');
  if (days === null || maxLevel === null || stored === null) {
    return null;
  }
  return {
    ...stored,
    barbarianWallsEnabled: form.barbarianWallsEnabled,
    barbarianWallStartDays: days,
    barbarianWallMaxLevel: maxLevel,
    barbarianWallsRebuild: form.barbarianWallsRebuild,
  };
}

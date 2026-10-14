import { MAX_LEVEL } from '../../game/achievements';
import type { Dictionary } from '../../i18n/en';

/** The name of an achievement; an unknown key (a newer server) shows as the key itself. */
export function achievementName(t: Dictionary, key: string): string {
  const item = (t.achievements.items as Record<string, { name: string } | undefined>)[key];
  return item?.name ?? key;
}

/** What the achievement asks for; empty for an unknown key. */
export function achievementDescription(t: Dictionary, key: string): string {
  const item = (t.achievements.items as Record<string, { description: string } | undefined>)[key];
  return item?.description ?? '';
}

/** "Bronze" … "Gold" for level 1 … 3; undefined levels fall back to the number. */
export function levelName(t: Dictionary, level: number): string {
  return level >= 1 && level <= MAX_LEVEL ? (t.achievements.levelNames[level - 1] ?? String(level)) : String(level);
}

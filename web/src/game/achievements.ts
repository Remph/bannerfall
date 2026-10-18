/**
 * Achievements (M16, 02-game-rules §19): the keys in the server's order, and the little arithmetic
 * of levels and progress. The texts are in the dictionaries; a test checks that every key has them.
 */

export const achievementKeys = [
  'PIONEER',
  'FOREMAN',
  'SCHOLAR',
  'SIEGECRAFT',
  'NOBLE_BLOOD',
  'FIRST_BLOOD',
  'BREACH',
  'PYRRHIC_VICTORY',
  'AVENGER',
  'DEMOLISHER',
  'LUCKY_STRIKE',
  'SHIELD',
  'SPY_CATCHER',
  'LAST_STAND',
  'BROTHERS_IN_ARMS',
  'CONQUEROR',
  'TOPPLER',
  'LORD_BREAKER',
  'FALLEN',
  'PHOENIX',
  'FIRST_DEAL',
  'FIRST_WORDS',
  'GUILD_FOUNDER',
  'GUILD_MEMBER',
  'STANDARD_BEARER',
  'COLOURS_RAISED',
  'COLOURS_LOST',
  'FULL_SET',
  'GROWTH',
  'LANDLORD',
  'STANDING_ARMY',
  'VICTOR',
  'PLUNDERER',
  'SLAYER',
  'BULWARK',
  'WARDEN',
  'MERCHANT_PRINCE',
  'VETERAN',
  'BANNER_COLLECTOR',
  'BANNER_THIEF',
] as const;

export type AchievementKey = (typeof achievementKeys)[number];

export const achievementCategories = [
  'BUILDING',
  'ARMY',
  'COMBAT',
  'DEFENCE',
  'CONQUEST',
  'BANNER',
  'TRADE',
  'SOCIAL',
  'TIME',
] as const;

export type AchievementCategory = (typeof achievementCategories)[number];

/** Bronze, silver, gold: the three levels of a levelled achievement. */
export const MAX_LEVEL = 3;

/** What the badge needs to know about an achievement. */
export interface Badge {
  key: string;
  category: string;
  /** 0 = not earned. */
  level: number;
  /** Whether it has levels (else it is earned or not). */
  leveled: boolean;
}

export function isAchievementKey(key: string): key is AchievementKey {
  return (achievementKeys as readonly string[]).includes(key);
}

export function isAchievementCategory(category: string): category is AchievementCategory {
  return (achievementCategories as readonly string[]).includes(category);
}

/**
 * The value of the next level, or undefined at the highest one (or for a one-time achievement,
 * which has no thresholds).
 */
export function nextThreshold(thresholds: readonly number[], level: number): number | undefined {
  return thresholds[level];
}

/**
 * How far along the way to the next level, 0 … 100: from the last threshold (or 0) to the next. A
 * finished achievement is 100.
 */
export function progressPercent(thresholds: readonly number[], level: number, progress: number): number {
  const next = nextThreshold(thresholds, level);
  if (next === undefined) {
    return 100;
  }
  const from = level === 0 ? 0 : (thresholds[level - 1] ?? 0);
  const span = next - from;
  if (span <= 0) {
    return 100;
  }
  return Math.max(0, Math.min(100, Math.floor(((progress - from) / span) * 100)));
}

/** The earned ones first (highest level first), then by the server's order. */
export function sortEarned<T extends { key: string; level: number }>(items: readonly T[]): T[] {
  const order = (key: string) => {
    const index = (achievementKeys as readonly string[]).indexOf(key);
    return index === -1 ? achievementKeys.length : index;
  };
  return [...items].sort((a, b) => b.level - a.level || order(a.key) - order(b.key));
}

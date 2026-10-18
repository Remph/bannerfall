import type { World } from '../api/queries/world';

/** The night bonus of a world that has it on (1.4, 02-game-rules §8.1). */
export type NightBonus = NonNullable<World['nightBonus']>;

/** The hour (0–23) of `at` (ms) in an IANA time zone. */
export function hourIn(at: number, zone: string): number {
  const hour = new Intl.DateTimeFormat('en-GB', { hour: 'numeric', hourCycle: 'h23', timeZone: zone }).format(at);
  return Number(hour);
}

/** Whether `at` (ms) lies in the night window: the start hour counts, the end hour doesn't; it may run across midnight. */
export function isNight(night: NightBonus, at: number): boolean {
  const hour = hourIn(at, night.zone);
  return night.startHour < night.endHour
    ? hour >= night.startHour && hour < night.endHour
    : hour >= night.startHour || hour < night.endHour;
}

/**
 * The factor the defence gets against an attack arriving at `at` (ms), or null when there is none:
 * the world has no night bonus, it is day then, or the village is a barbarian one (`owned` false).
 * Shown before the battle; the battle itself uses the settings at its arrival.
 */
export function nightBonusAt(night: NightBonus | null | undefined, at: number, owned: boolean): number | null {
  if (night === null || night === undefined || !owned) {
    return null;
  }
  return isNight(night, at) ? night.factor : null;
}

/** The window as "23:00–07:00" (in the night bonus' own time zone). */
export function formatNightHours(night: Pick<NightBonus, 'startHour' | 'endHour'>): string {
  const hour = (value: number) => `${String(value).padStart(2, '0')}:00`;
  return `${hour(night.startHour)}–${hour(night.endHour)}`;
}

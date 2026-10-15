import type { components } from '../api/schema';
import { unitTypes, type UnitType } from './gameData';
import type { UnitMap } from './troops';

/** Most units of one type in a template: the same limit as one recruit order (server FarmService). */
export const FARM_TEMPLATE_MAX = 30_000;

/** The server's limit of targets per village's list (02-game-rules §18). */
export const FARM_LIST_MAX = 50;

/**
 * The types a template can hold: everything but nobles, the paladin and banner thieves, which never
 * belong to "the usual troops" (the server refuses thieves in a template, M32b).
 */
export const farmUnitTypes: readonly UnitType[] = unitTypes.filter(
  (type) => type !== 'NOBLE' && type !== 'PALADIN' && type !== 'BANNER_THIEF',
);

/** The template as the server wants it: only the types with units. */
export function templateOf(counts: UnitMap): UnitMap {
  const template: UnitMap = {};
  for (const type of farmUnitTypes) {
    const count = counts[type] ?? 0;
    if (count > 0) {
      template[type] = count;
    }
  }
  return template;
}

/** Whether the template has at least one unit (the server refuses an empty one). */
export function hasUnits(counts: UnitMap): boolean {
  return Object.keys(templateOf(counts)).length > 0;
}

/** Whether the units at home cover the template right now (a hint: the server decides). */
export function fitsHome(template: UnitMap, home: UnitMap): boolean {
  return Object.entries(template).every(([type, count]) => (home[type as UnitType] ?? 0) >= (count ?? 0));
}

/**
 * Whether sending a farm target's template ends the sender's own beginner protection (02-game-rules
 * §6): only an attack on a player's village does. A barbarian village (no player) never does unless
 * nobles go along, and a template never holds nobles (`farmUnitTypes`), so those stay one tap.
 */
export function farmSendEndsProtection(
  protectedUntilMs: number,
  targetPlayerId: number | null,
  nowMs: number,
): boolean {
  return protectedUntilMs > nowMs && targetPlayerId !== null;
}

/**
 * When the player's banner roll against this target comes back (02-game-rules §21.7, 1.2.2): the
 * server's `nextBannerRollAt` while it is still ahead, undefined when a won battle would roll now
 * (or banners are off: the server sends null then).
 */
export function bannerRollBackAt(nextBannerRollAt: string | null, nowMs: number): number | undefined {
  if (nextBannerRollAt === null) {
    return undefined;
  }
  const at = Date.parse(nextBannerRollAt);
  return at > nowMs ? at : undefined;
}

/**
 * The next moment a pill runs out, as a delay from now in ms (the list is fetched again then, so the
 * pill goes on time); undefined when no row has one.
 */
export function nextBannerRollBackIn(nextBannerRollAts: (string | null)[], nowMs: number): number | undefined {
  let next: number | undefined;
  for (const at of nextBannerRollAts) {
    const back = bannerRollBackAt(at, nowMs);
    if (back !== undefined && (next === undefined || back < next)) {
      next = back;
    }
  }
  return next === undefined ? undefined : next - nowMs;
}

/** What "send all" (M39) answers: what went out, what was skipped and why, where it stopped. */
export type SendAllResult = components['schemas']['FarmSendAllDto'];

export type SendAllSkipReason = SendAllResult['skipped'][number]['reason'];

/** What a row shows after a "send all": sent, skipped (with the reason) or the entry it stopped at. */
export type SendAllNote = { kind: 'sent' } | { kind: 'skipped'; reason: SendAllSkipReason } | { kind: 'stopped' };

/** The note of each entry the run touched, by entry id; entries after the stop have none. */
export function sendAllNotes(result: SendAllResult): Map<number, SendAllNote> {
  const notes = new Map<number, SendAllNote>();
  for (const id of result.sent) {
    notes.set(id, { kind: 'sent' });
  }
  for (const { targetId, reason } of result.skipped) {
    notes.set(targetId, { kind: 'skipped', reason });
  }
  if (result.stoppedAt !== null) {
    notes.set(result.stoppedAt, { kind: 'stopped' });
  }
  return notes;
}

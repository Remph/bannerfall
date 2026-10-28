import { buildingTypes, unitTypes, type BuildingType, type UnitType } from './gameData';
import { calendarDay } from './format';
import { presentUnits, withoutZeros, type UnitMap } from './troops';

/**
 * Helpers for reports (02-game-rules §14) – pure. The kinds and outcomes are the server enums
 * ReportKind and BattleOutcome.
 */
export type ReportKind =
  | 'ATTACK'
  | 'DEFENSE'
  | 'SCOUT'
  | 'SUPPORT_ARRIVED'
  | 'SUPPORT_RETURNED'
  | 'CONQUEST'
  | 'VILLAGE_LOST'
  | 'TRADE_ARRIVED';
export type BattleOutcome = 'ATTACKER_WON' | 'DEFENDER_WON' | 'SCOUTS_ONLY';
/** What an attack's banner thieves did (server enum TheftResult, M32b, 02-game-rules §21.6.2). */
export type TheftResult = 'STOLEN' | 'FAILED' | 'NO_BANNER' | 'CONQUERED' | 'NO_SURVIVORS' | 'BATTLE_LOST';

/** How a report ended for its reader: green, red, or neither (supports, scouts in the list). */
export type ReportResult = 'won' | 'lost' | 'none';

/** Reports per page of the list (the server's default). */
export const REPORTS_PAGE_SIZE = 25;

/** The filter chips of the list (server enum ReportFilter, 1.3); every kind is in exactly one. */
export const reportFilters = ['ATTACKS', 'DEFENSE', 'SCOUTING', 'SUPPORT', 'TRADE'] as const;
export type ReportFilter = (typeof reportFilters)[number];

/** One day of the list: its reports in list order, under one heading. */
export interface ReportDay<T> {
  /** "2026-10-16", the day in the device's (or the given) time zone. */
  day: string;
  items: T[];
}

/**
 * The list's reports by day (1.3): each report goes by `listedAt`, the moment the list sorts it by
 * (a copy someone sent: when it arrived), so the days follow the list's order.
 */
export function groupByDay<T extends { listedAt: string }>(items: readonly T[], timeZone?: string): ReportDay<T>[] {
  const days: ReportDay<T>[] = [];
  for (const item of items) {
    const day = calendarDay(Date.parse(item.listedAt), timeZone);
    const last = days.at(-1);
    if (last !== undefined && last.day === day) {
      last.items.push(item);
    } else {
      days.push({ day, items: [item] });
    }
  }
  return days;
}

/**
 * The result from the reader's side: the attacker wins with ATTACKER_WON, the defenders (owner and
 * supporters, kind DEFENSE) with DEFENDER_WON. A conquest is won, a lost village lost. Scouts only
 * and supports have none.
 */
export function resultFor(kind: ReportKind, outcome: BattleOutcome | null): ReportResult {
  if (kind === 'CONQUEST') {
    return 'won';
  }
  if (kind === 'VILLAGE_LOST') {
    return 'lost';
  }
  if (outcome === null || outcome === 'SCOUTS_ONLY') {
    return 'none';
  }
  if (kind === 'ATTACK') {
    return outcome === 'ATTACKER_WON' ? 'won' : 'lost';
  }
  if (kind === 'DEFENSE') {
    return outcome === 'DEFENDER_WON' ? 'won' : 'lost';
  }
  return 'none';
}

/** A battle with only scouts on the attacking side: the attacker's SCOUT, the owner's DEFENSE. */
export function isScouting(kind: ReportKind, outcome: BattleOutcome | null): boolean {
  return kind === 'SCOUT' || outcome === 'SCOUTS_ONLY';
}

/** What the detail page says first about a battle (a text key) and its colour. */
export type Verdict =
  | 'attackWon'
  | 'attackLost'
  | 'defenseWon'
  | 'defenseLost'
  | 'scoutsThrough'
  | 'scoutsCaught'
  | 'enemyScoutsThrough'
  | 'enemyScoutsCaught'
  | 'thievesAlone'
  | 'enemyThievesAlone';

/**
 * The verdict of a battle for the reader. Scouts only: for the attacker it depends on whether his
 * scouts got through (`scouting` is null in his copy when none came back), for the defender it is
 * bad news when they did.
 */
export function battleVerdict(
  kind: ReportKind,
  outcome: BattleOutcome,
  scoutingSucceeded: boolean | undefined,
  scoutsSent = true,
): { verdict: Verdict; result: ReportResult } {
  const defender = kind === 'DEFENSE';
  if (outcome === 'SCOUTS_ONLY' && !scoutsSent) {
    // M32b: banner thieves alone have no attack, so there was no battle (02-game-rules §21.5)
    return { verdict: defender ? 'enemyThievesAlone' : 'thievesAlone', result: 'none' };
  }
  if (outcome === 'SCOUTS_ONLY') {
    if (defender) {
      return scoutingSucceeded === true
        ? { verdict: 'enemyScoutsThrough', result: 'lost' }
        : { verdict: 'enemyScoutsCaught', result: 'won' };
    }
    return scoutingSucceeded === true
      ? { verdict: 'scoutsThrough', result: 'won' }
      : { verdict: 'scoutsCaught', result: 'lost' };
  }
  if (defender) {
    return outcome === 'DEFENDER_WON'
      ? { verdict: 'defenseWon', result: 'won' }
      : { verdict: 'defenseLost', result: 'lost' };
  }
  return outcome === 'ATTACKER_WON'
    ? { verdict: 'attackWon', result: 'won' }
    : { verdict: 'attackLost', result: 'lost' };
}

/** One line of a unit table: how many there were, how many died, how many are left. */
export interface UnitRow {
  type: UnitType;
  count: number;
  lost: number;
  left: number;
}

/** The rows of a unit table in unit order: every type that was there (or, oddly, only died). */
export function unitRows(units: UnitMap, losses: UnitMap): UnitRow[] {
  return unitTypes.flatMap((type): UnitRow[] => {
    const count = units[type] ?? 0;
    const lost = losses[type] ?? 0;
    return count > 0 || lost > 0 ? [{ type, count, lost, left: Math.max(0, count - lost) }] : [];
  });
}

/** The scouted buildings that exist (level above 0), in building order. */
export function builtBuildings(levels: Partial<Record<BuildingType, number>>): [BuildingType, number][] {
  return buildingTypes.flatMap((type): [BuildingType, number][] => {
    const level = levels[type] ?? 0;
    return level > 0 ? [[type, level]] : [];
  });
}

/** The number on the navigation badge: "7", "99+"; undefined (no badge) for 0. */
export function badgeText(count: number): string | undefined {
  if (count <= 0) {
    return undefined;
  }
  return count > 99 ? '99+' : String(count);
}

/** How many pages the list has (at least 1, so an empty list is "page 1 of 1"). */
export function pageCount(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / size));
}

/** Village refs of a report: who they belong to (null for barbarians). */
interface OwnedRef {
  playerId: number | null;
}

/**
 * A SUPPORT_RETURNED report goes to the one who didn't do it (02-game-rules §14): if the units are
 * the reader's own, the supported village's owner sent them back; otherwise their owner withdrew
 * them from the reader's village.
 */
export function wasSentBack(from: OwnedRef, playerId: number): boolean {
  return from.playerId === playerId;
}

/**
 * The defenders' copy lists the units per village; it is worth showing only when other villages'
 * troops stood there too (with only the village's own units it repeats the totals).
 */
export function hasSupporters(stacks: readonly { home: { id: number } }[], defenderVillageId: number): boolean {
  return stacks.some((stack) => stack.home.id !== defenderVillageId);
}

/**
 * The village a battle report offers for the farm list (M38): the other side's. In the attacker's
 * reports (ATTACK, SCOUT) the attacked village, in the defenders' (DEFENSE, also a supporter's) the
 * attacker's village. None for the reader's own villages, which a farm list can't hold, and none for
 * other kinds of report.
 */
export function farmCandidate<V extends { playerId: number | null }>(
  kind: ReportKind,
  attacker: V,
  defender: V,
  playerId: number,
): V | null {
  const other = kind === 'ATTACK' || kind === 'SCOUT' ? defender : kind === 'DEFENSE' ? attacker : null;
  return other === null || other.playerId === playerId ? null : other;
}

/**
 * Whose farm list a report's "Add to farm list" fills (M38): in the reader's own attack and scout
 * reports the village that sent them, while it is still the reader's (the units it sent become the
 * template, so they belong on its list); otherwise the current village, like the map's button.
 */
export function farmListVillageId(
  kind: ReportKind,
  attackerVillageId: number,
  ownVillageIds: readonly number[],
  currentVillageId: number | undefined,
): number | undefined {
  return (kind === 'ATTACK' || kind === 'SCOUT') && ownVillageIds.includes(attackerVillageId)
    ? attackerVillageId
    : currentVillageId;
}

/** Players one report can be sent to at once (the server's limit, M40). */
export const MAX_SHARE_RECIPIENTS = 10;

/**
 * The names typed into "Send report" (M40): separated by commas, semicolons or spaces (names have
 * none of them), each once ignoring case, in the order typed.
 */
export function shareNames(input: string): string[] {
  const seen = new Set<string>();
  return input
    .split(/[\s,;]+/)
    .filter((name) => name !== '')
    .filter((name) => {
      const key = name.toLowerCase();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
}

/**
 * Adds a name to the typed list (a quick pick, M40) unless it is in it already; keeps what was
 * typed and ends with ", " so the next name can follow.
 */
export function addShareName(input: string, name: string): string {
  if (shareNames(input).some((typed) => typed.toLowerCase() === name.toLowerCase())) {
    return input;
  }
  const kept = input.replace(/[\s,;]+$/, '');
  return `${kept === '' ? '' : `${kept}, `}${name}, `;
}

/**
 * The quick picks of "Send report" (1.3.1): the players last sent a report to first, then those of
 * the inbox, each once ignoring case, at most `max`.
 */
export function sharePicks(recentRecipients: string[], inbox: string[], max: number): string[] {
  const seen = new Set<string>();
  return [...recentRecipients, ...inbox]
    .filter((name) => {
      const key = name.toLowerCase();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    })
    .slice(0, max);
}

/**
 * The player a report's "you" means (M40): for a copy someone sent, the player whose report it was
 * first; else the reader. Texts that depend on whose units they are (a support sent back) use it.
 */
export function viewerOf(sharedBy: { viewOfPlayerId: number } | null, readerId: number): number {
  return sharedBy === null ? readerId : sharedBy.viewOfPlayerId;
}

/** Units a battle cost one of the reader's villages. */
export interface VillageLosses<V> {
  readonly village: V;
  readonly losses: UnitMap;
}

/**
 * The reader's own villages that lost units in a battle (1.6.3), for "Re-recruit losses": the
 * attacker's sending village in the reader's own attack, the reader's stacks (the defended village
 * and the villages his supports came from) in a defence. A village counts once the player owns it
 * (`ownVillageIds`), so a conquered one is left out.
 */
export function ownLosses<V extends { id: number; playerId: number | null }>(
  kind: ReportKind,
  battle: {
    attacker: V;
    attackerLosses: UnitMap;
    defender: V;
    defenderSide: { losses: UnitMap; stacks: readonly { home: V; losses: UnitMap }[] } | null;
  },
  ownVillageIds: readonly number[],
): VillageLosses<V>[] {
  const candidates: VillageLosses<V>[] =
    kind === 'ATTACK' || kind === 'SCOUT'
      ? [{ village: battle.attacker, losses: battle.attackerLosses }]
      : kind === 'DEFENSE' && battle.defenderSide !== null
        ? battle.defenderSide.stacks.length > 0
          ? battle.defenderSide.stacks.map((stack) => ({ village: stack.home, losses: stack.losses }))
          : [{ village: battle.defender, losses: battle.defenderSide.losses }]
        : [];
  return candidates
    .map(({ village, losses }) => ({ village, losses: withoutZeros(losses) }))
    .filter(({ village, losses }) => ownVillageIds.includes(village.id) && presentUnits(losses).length > 0);
}

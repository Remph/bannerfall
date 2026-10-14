import { ApiError, errorMessage } from '../../api/errors';
import type { ReportSummary } from '../../api/queries/reports';
import type { VillageRef } from '../../api/queries/troops';
import { isScouting, wasSentBack, type ReportKind } from '../../game/reports';
import type { Dictionary } from '../../i18n/en';

/** A village's name in a report; barbarian villages have none. */
export function villageName(t: Dictionary, village: VillageRef): string {
  return village.name ?? t.map.barbarianVillage;
}

/**
 * The title of a report, the same in the list and on the detail page: "Benhausen attacks
 * Tomburg", "Support from Benhausen arrived in Tomburg", "Benhausen conquered Tomburg" … A support sent home reads differently
 * for the one whose units they are and for the one they left (02-game-rules §14).
 */
export function reportTitle(t: Dictionary, kind: ReportKind, summary: ReportSummary | null, playerId: number): string {
  if (summary === null) {
    return t.reports.titles.unreadable;
  }
  const from = villageName(t, summary.from);
  const to = villageName(t, summary.to);
  switch (kind) {
    case 'ATTACK':
    case 'DEFENSE':
    case 'SCOUT':
      // the owner's DEFENSE of a scouts-only attack reads like the attacker's SCOUT
      return isScouting(kind, summary.outcome) ? t.reports.titles.scout(from, to) : t.reports.titles.battle(from, to);
    case 'SUPPORT_ARRIVED':
      return t.reports.titles.supportArrived(from, to);
    case 'SUPPORT_RETURNED':
      return wasSentBack(summary.from, playerId)
        ? t.reports.titles.supportSentBack(to)
        : t.reports.titles.supportWithdrawn(from, to);
    case 'TRADE_ARRIVED':
      return t.reports.titles.tradeArrived(from, to);
    case 'CONQUEST':
    case 'VILLAGE_LOST':
      // `to` is the village as it was before: a barbarian village is "Barbarian village"
      return t.reports.titles.conquest(from, to);
  }
}

/** The error of sending a report (M40): with the player the send stopped at when the server names one. */
export function sendError(t: Dictionary, error: Error): string {
  if (error instanceof ApiError && error.playerName !== null) {
    switch (error.code) {
      case 'PLAYER_NOT_FOUND':
      case 'OWN_PLAYER':
      case 'PLAYER_BLOCKED':
      case 'INBOX_FULL':
        return t.reports.sendErrors[error.code](error.playerName);
    }
  }
  return errorMessage(t, error);
}

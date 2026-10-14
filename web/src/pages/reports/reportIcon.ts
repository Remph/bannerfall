import type { IconName } from '../../components/Icon';
import { isScouting, type BattleOutcome, type ReportKind } from '../../game/reports';

/**
 * The icon of a report: swords for an attack, the wall for a defence, a scout, a shield for supports,
 * a banner for a village that changed owner.
 */
export function reportIcon(kind: ReportKind, outcome: BattleOutcome | null): IconName {
  if (isScouting(kind, outcome)) {
    return 'SCOUT';
  }
  switch (kind) {
    case 'ATTACK':
    case 'SCOUT':
      return 'troops';
    case 'DEFENSE':
      return 'WALL';
    case 'SUPPORT_ARRIVED':
      return 'shield';
    case 'SUPPORT_RETURNED':
      return 'returning';
    case 'CONQUEST':
    case 'VILLAGE_LOST':
      return 'banner';
    case 'TRADE_ARRIVED':
      return 'MARKET';
  }
}

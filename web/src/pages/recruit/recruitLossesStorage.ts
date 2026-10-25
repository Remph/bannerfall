import type { UnitMap } from '../../game/troops';

/**
 * The goals of "Re-recruit losses" (1.6.3) that were recruited, per report and village, stored per
 * device. The goal is what the village owns plus the loss, so a plan opened again after recruiting
 * would count the queued units as owned and ask for the loss a second time; the stored goal makes
 * the second opening show what is still missing. Only the newest few are kept.
 */
const STORAGE_KEY = 'bannerfall.recruitLossesGoals';
const KEPT = 40;

type Stored = Record<string, UnitMap>;

function keyOf(reportId: number, villageId: number): string {
  return `${reportId}:${villageId}`;
}

function read(): Stored {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? (parsed as Stored) : {};
  } catch {
    return {}; // storage blocked (private mode) or damaged
  }
}

/** The goal recruited from this report for this village, or undefined. */
export function loadLossesGoal(reportId: number, villageId: number): UnitMap | undefined {
  return read()[keyOf(reportId, villageId)];
}

export function saveLossesGoal(reportId: number, villageId: number, goal: UnitMap): void {
  try {
    const stored = read();
    const key = keyOf(reportId, villageId);
    delete stored[key]; // a replaced entry moves to the newest place
    const entries = Object.entries({ ...stored, [key]: goal });
    localStorage.setItem(STORAGE_KEY, JSON.stringify(Object.fromEntries(entries.slice(-KEPT))));
  } catch {
    // ignore: without it a second opening asks for the loss again, which the goal field can fix
  }
}

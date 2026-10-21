import type { VillageAutomation } from '../../api/queries/automation';
import { formatNumber, formatUpcoming } from '../../game/format';
import { unitTypes } from '../../game/gameData';
import type { Dictionary } from '../../i18n/en';
import type { Language } from '../../i18n/language';

/** What the automation queues next, e.g. "Barracks Lv 4"; empty without a building. */
function nextText(t: Dictionary, status: VillageAutomation): string {
  return status.building === null ? '' : `${t.buildings[status.building]} ${t.village.level(status.level)}`;
}

type State = VillageAutomation['state'];

interface TextInput {
  readonly t: Dictionary;
  readonly language: Language;
  readonly nowMs: number;
  readonly status: VillageAutomation;
  /** What it queues next, e.g. "Barracks Lv 4". */
  readonly next: string;
}

const stateTexts: Record<State, (input: TextInput) => string | undefined> = {
  LOCKED: () => undefined,
  OFF: ({ t, status }) => (status.plan === null ? t.automation.noPlanYet : t.automation.state.OFF),
  PAUSED: ({ t }) => t.automation.state.PAUSED,
  NO_PLAN: ({ t }) => t.automation.state.NO_PLAN,
  READY: ({ t, next }) => t.automation.state.READY(next),
  WAITING_RESOURCES: ({ t, language, nowMs, status, next }) =>
    t.automation.state.WAITING_RESOURCES(
      next,
      status.readyAt === null ? '' : formatUpcoming(Date.parse(status.readyAt), nowMs, language),
    ),
  WAITING_QUEUE: ({ t, next }) => t.automation.state.WAITING_QUEUE(next),
  WAITING_UPGRADE: ({ t }) => t.automation.state.WAITING_UPGRADE,
  BLOCKED: ({ t, next }) => t.automation.state.BLOCKED(next),
  DONE: ({ t }) => t.automation.state.DONE,
};

/**
 * The sentence for what the village's automation does now (02-game-rules §25). Undefined while it
 * is locked: the page shows the lock instead.
 */
export function statusText(
  t: Dictionary,
  language: Language,
  nowMs: number,
  status: VillageAutomation,
): string | undefined {
  return stateTexts[status.state]({ t, language, nowMs, status, next: nextText(t, status) });
}

/**
 * "Spearman 340, Swordsman 120 (needs Smithy 1)": what the recruit template still lacks, in unit
 * order; a unit the village can't recruit yet says what it needs.
 */
export function missingText(
  t: Dictionary,
  language: Language,
  missing: VillageAutomation['missing'],
  blocked: VillageAutomation['blocked'] = {},
): string {
  return unitTypes
    .filter((type) => (missing[type] ?? 0) > 0)
    .map((type) => {
      const text = `${t.units[type]} ${formatNumber(missing[type] ?? 0, language)}`;
      const requirements = blocked[type] ?? [];
      if (requirements.length === 0) {
        return text;
      }
      const needs = requirements.map((r) => t.townHall.requirement(t.buildings[r.building], r.level)).join(', ');
      return `${text} (${t.automation.needsShort(needs)})`;
    })
    .join(', ');
}

/** "Noble, Banner thief": the template's units the player recruits by hand. */
export function byHandText(t: Dictionary, byHand: VillageAutomation['byHand']): string {
  return byHand.map((type) => t.units[type]).join(', ');
}

import type { TutorialStepKey, TutorialStepLine } from '../api/queries/tutorial';
import type { Dictionary } from '../i18n/en';
import type { Language } from '../i18n/language';
import { bannersPath } from '../pages/banners/bannerPaths';
import { recruitPath } from '../pages/recruit/recruitPaths';
import { formatNumber } from './format';

/** Where a step's "Show me" leads (the page the step is about). */
export function tutorialPath(key: TutorialStepKey, villageId: number): string {
  switch (key) {
    case 'WOODCUTTER':
    case 'HEADQUARTERS':
    case 'BARRACKS':
    case 'MARKET':
      return `/v/${villageId}/hq`;
    case 'SPEARMEN':
      return recruitPath(villageId, 'BARRACKS');
    case 'MAP_BARBARIAN':
      return `/v/${villageId}/map`;
    case 'ATTACK':
      return `/v/${villageId}/rally`;
    case 'REPORT':
      return '/reports';
    case 'SIMULATOR':
      return `/v/${villageId}/rally/simulator`;
    case 'FARM_LIST':
      return `/v/${villageId}/farm`;
    case 'GUILD':
      return '/guild';
    case 'LESSON_UNIT_LEVELS':
      return `/v/${villageId}/smithy`;
    case 'LESSON_CONQUEST':
      return recruitPath(villageId, 'ACADEMY');
    case 'LESSON_AUTOMATION':
      return `/v/${villageId}/automation`;
    case 'LESSON_BANNER_HALL':
      return bannersPath(villageId);
  }
}

/** "63 Wood, 75 Clay, 50 Iron": the amounts above 0, in the language's resource names. */
export function resourceText(
  amount: { wood: number; clay: number; iron: number },
  t: Dictionary,
  language: Language,
): string {
  const parts: string[] = [];
  for (const [value, name] of [
    [amount.wood, t.resources.wood],
    [amount.clay, t.resources.clay],
    [amount.iron, t.resources.iron],
  ] as const) {
    if (value > 0) {
      parts.push(`${formatNumber(value, language)} ${name}`);
    }
  }
  return parts.join(', ');
}

/**
 * The refunds of one answer as one toast's content (design review 1.7): the amounts added up and
 * the first village that got something. A refund into a full warehouse arrives as 0 and adds
 * nothing; `villageId` stays undefined while nothing arrived at all.
 */
export function sumRefunds(
  refunds: readonly { villageId: number; amount: { wood: number; clay: number; iron: number } }[],
): {
  amount: { wood: number; clay: number; iron: number };
  villageId: number | undefined;
} {
  const amount = { wood: 0, clay: 0, iron: 0 };
  let villageId: number | undefined;
  for (const refund of refunds) {
    amount.wood += refund.amount.wood;
    amount.clay += refund.amount.clay;
    amount.iron += refund.amount.iron;
    if (villageId === undefined && refund.amount.wood + refund.amount.clay + refund.amount.iron > 0) {
      villageId = refund.villageId;
    }
  }
  return { amount, villageId };
}

/** The lines of the "All steps" sheet: first the steps, then the lessons. */
export function splitLines(steps: TutorialStepLine[]): { steps: TutorialStepLine[]; lessons: TutorialStepLine[] } {
  return {
    steps: steps.filter((line) => line.kind === 'STEP'),
    lessons: steps.filter((line) => line.kind === 'LESSON'),
  };
}

import type { components } from '../api/schema';
import type { Dictionary } from '../i18n/en';
import type { Language } from '../i18n/language';
import { formatCoords, formatDateTime, formatNumber } from './format';

export type LordLetter = components['schemas']['LordLetter'];

type LetterTexts = Pick<Dictionary, 'messages' | 'units' | 'resources' | 'realtime'>;

/** The parts of an order letter (2.1 S3), already written in the reader's language. */
export interface LetterParts {
  /** The village of the order, "Wulframsfeste (61|52)". */
  target: string;
  /** The units it sends, "Axeman 180, Ram 10". */
  troops: string;
  /** The resources it sends, "Wood 1,200, Clay 800". */
  goods: string;
  /** When its troops or merchants arrive; empty if none. */
  arrive: string;
  /** When a "land together" march leaves; empty if none. */
  depart: string;
  /** The guild the letter names, or "your guild". */
  guild: string;
  /** The defenders a help request asks for. */
  count: string;
  /** Who attacks the village of a help request. */
  attacker: string;
  /** What a request for resources is for: WALL or NOBLE. */
  goal: string;
  /** The court that taunts, "Court of the Raven". */
  court: string;
}

/**
 * A lord's letter in the reader's language (2.1 S2b, S3 and S4, 12-solo-and-allies §5, §8, §9): the
 * server keeps only its kind, the lord's voice and the facts it names (the guild, an order's village,
 * troops, resources and times), so a German player reads it in German, an English one in English.
 * A letter without a voice (written before S4) reads in the steady one. `times: false` leaves the
 * times out (the help sheet, beside the request's live time).
 */
export function letterText(
  t: LetterTexts,
  letter: LordLetter,
  language: Language,
  options: { times?: boolean } = {},
): string {
  const parts = letterParts(t, letter, language);
  // beside a live request the letter's times would be stale after the world slept: the sheet's own
  // time speaks, and the letter leaves its time out (UI review 2.1, finding 06)
  const shown = options.times === false ? { ...parts, arrive: '', depart: '' } : parts;
  return tidyLetter(t.messages.letters[letter.voice ?? 'STEADY'][letter.kind](shown), language);
}

/**
 * A letter whose fact was missing reads as a sentence all the same (UI review 2.1, finding 13): one
 * space between words, none before a stop or a comma, and a capital at the start of every sentence
 * ("the village is …" when the village has no name).
 */
export function tidyLetter(text: string, language: Language): string {
  return text
    .replace(/\s+/g, ' ')
    .replace(/ ([.,:;!?])/g, '$1')
    .trim()
    .replace(
      /(^|[.!?] )(\p{Ll})/gu,
      (_, before: string, letter: string) => before + letter.toLocaleUpperCase(language),
    );
}

/** The village, troops, resources and times an order letter names, in the reader's language. */
export function letterParts(t: LetterTexts, letter: LordLetter, language: Language): LetterParts {
  const target =
    letter.target == null
      ? t.messages.letterVillage
      : `${letter.target.name ?? t.messages.letterVillage} ${formatCoords(letter.target.x, letter.target.y)}`;
  const units = letter.units ?? {};
  const troops = (Object.keys(t.units) as (keyof LetterTexts['units'])[])
    .filter((unit) => (units[unit] ?? 0) > 0)
    .map((unit) => `${t.units[unit]} ${formatNumber(units[unit] ?? 0, language)}`)
    .join(', ');
  const resources = letter.resources;
  const goods =
    resources == null
      ? ''
      : (['wood', 'clay', 'iron'] as const)
          .filter((resource) => resources[resource] > 0)
          .map((resource) => `${t.resources[resource]} ${formatNumber(resources[resource], language)}`)
          .join(', ');
  const time = (iso: string | null | undefined) => (iso == null ? '' : formatDateTime(Date.parse(iso), language));
  const guild = letter.guild ?? t.messages.yourGuild;
  return {
    target,
    troops,
    goods,
    arrive: time(letter.arriveAt),
    depart: time(letter.departAt),
    guild,
    count: letter.count == null ? '' : formatNumber(letter.count, language),
    attacker: letter.attacker ?? '',
    goal: letter.goal ?? '',
    court: letter.court == null ? guild : t.realtime.courtName(letter.court.key, letter.court.numeral, guild),
  };
}

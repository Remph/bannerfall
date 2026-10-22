import { templateLetters } from './farmTemplates';
import { MAX_PLAN_LINES, MAX_PLAN_NAME, minesTopLevel, type PlanBuilding, type PlanLine } from './buildPlans';
import { buildingTypes } from './gameData';

/**
 * A build plan as text (1.6.1): the player sends it in a message and another player (or the same
 * one on another world) pastes it into the plan editor. `BFP1|Name|HEADQUARTERS:3,MINES:5,RECRUIT:B`:
 * a version tag, the name URL-encoded (so no `|`, `,` or `:` in it), then the lines. Pasted text
 * comes from other players, so {@link decodePlan} checks everything a typed plan is checked for
 * (length, the building names the game has, levels up to the building's top, one recruit line, the
 * recruit letters A to E) and never trusts a key it did not look up in a fixed list. What it lets
 * through is an ordinary editor state: the requirements are shown and the server checks the plan
 * again when it is saved.
 */
export const PLAN_CODE_TAG = 'BFP1';

/** The longest code looked at; 80 lines of the longest building name and a level are far below. */
export const MAX_PLAN_CODE_LENGTH = 4000;

const RECRUIT_KEY = 'RECRUIT';
/** The mines line (1.6.2): all three mines to one level. */
const MINES_KEY = 'MINES';
const LEVEL = /^[0-9]{1,3}$/;
const CONTROL_CHARS = /[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u;

export type PlanCodeError =
  /** Nothing there. */
  | 'empty'
  /** More than {@link MAX_PLAN_CODE_LENGTH} characters. */
  | 'tooLong'
  /** Not a plan code: the tag, the three parts or a line's `KEY:VALUE` is missing. */
  | 'format'
  | 'badName'
  | 'tooManyLines'
  | 'unknownBuilding'
  | 'badLevel'
  | 'badTemplate'
  | 'twoRecruitLines';

export type DecodedPlan =
  | { readonly ok: true; readonly name: string; readonly lines: readonly PlanLine[] }
  | { readonly ok: false; readonly error: PlanCodeError };

/** The plan as a code, e.g. `BFP1|Economy|HEADQUARTERS:3,RECRUIT:B,CLAY_PIT:10`. */
export function encodePlan(name: string, lines: readonly PlanLine[]): string {
  const body = lines.map((line) => {
    switch (line.kind) {
      case 'recruit':
        return `${RECRUIT_KEY}:${line.template}`;
      case 'mines':
        return `${MINES_KEY}:${line.level}`;
      case 'build':
        return `${line.building}:${line.level}`;
    }
  });
  return [PLAN_CODE_TAG, encodeURIComponent(name.trim()), body.join(',')].join('|');
}

function fail(error: PlanCodeError): DecodedPlan {
  return { ok: false, error };
}

function decodeName(encoded: string): string | undefined {
  let name: string;
  try {
    name = decodeURIComponent(encoded).trim();
  } catch {
    return undefined; // a stray % or broken UTF-8
  }
  return name.length > 0 && name.length <= MAX_PLAN_NAME && !CONTROL_CHARS.test(name) ? name : undefined;
}

/** One `KEY:VALUE` line, or the error it earns. */
function decodeLine(part: string, buildings: readonly PlanBuilding[]): PlanLine | PlanCodeError {
  const pair = part.split(':');
  const [key, value] = pair;
  if (pair.length !== 2 || key === undefined || value === undefined) {
    return 'format';
  }
  if (key === RECRUIT_KEY) {
    const template = templateLetters.find((letter) => letter === value);
    return template === undefined ? 'badTemplate' : { kind: 'recruit', template };
  }
  if (key === MINES_KEY) {
    if (!LEVEL.test(value)) {
      return 'badLevel';
    }
    const level = Number(value);
    return level >= 1 && level <= minesTopLevel(buildings) ? { kind: 'mines', level } : 'badLevel';
  }
  const building = buildingTypes.find((type) => type === key);
  const info = buildings.find((candidate) => candidate.building === building);
  if (building === undefined || info === undefined) {
    return 'unknownBuilding';
  }
  if (!LEVEL.test(value)) {
    return 'badLevel';
  }
  const level = Number(value);
  return level >= 1 && level <= info.maxLevel ? { kind: 'build', building, level } : 'badLevel';
}

/**
 * The plan in pasted text. Spaces and line breaks around and inside it are ignored (a message app
 * may wrap it). `buildings` are the editor's buildings with their top levels.
 */
export function decodePlan(text: string, buildings: readonly PlanBuilding[]): DecodedPlan {
  if (text.length > MAX_PLAN_CODE_LENGTH) {
    return fail('tooLong');
  }
  const code = text.replace(/\s+/gu, '');
  if (code === '') {
    return fail('empty');
  }
  const parts = code.split('|');
  const [tag, encodedName, body] = parts;
  if (parts.length !== 3 || tag !== PLAN_CODE_TAG || encodedName === undefined || body === undefined || body === '') {
    return fail('format');
  }
  const name = decodeName(encodedName);
  if (name === undefined) {
    return fail('badName');
  }
  const pieces = body.split(',');
  if (pieces.length > MAX_PLAN_LINES) {
    return fail('tooManyLines');
  }
  const lines: PlanLine[] = [];
  for (const piece of pieces) {
    const line = decodeLine(piece, buildings);
    if (typeof line === 'string') {
      return fail(line);
    }
    if (line.kind === 'recruit' && lines.some((earlier) => earlier.kind === 'recruit')) {
      return fail('twoRecruitLines');
    }
    lines.push(line);
  }
  return { ok: true, name, lines };
}

import { describe, expect, it } from 'vitest';
import {
  appendNextLevel,
  changeLevel,
  fromApiLines,
  isSavable,
  lineState,
  linesFromLevels,
  missingPerLine,
  moveLine,
  removeLine,
  setRecruitLine,
  toApiLines,
  type Levels,
  type PlanBuilding,
  type PlanLine,
} from './buildPlans';
import fixture from './buildPlans.fixture.json';

// requirements as in the game data: the Stable needs Town Hall 10, Barracks 5 and Smithy 5, the
// Smithy Town Hall 5 and Barracks 1, the Barracks Town Hall 3
const buildings = fixture.buildings as PlanBuilding[];
const village = fixture.village as Levels;
const TOP = 30;
const HQ_FOR_BARRACKS = 3;
const HQ_FOR_STABLE = 10;
const FOR_STABLE = 5;
const SOME = 4;
const STABLE_LEVEL = 2;
const WOOD_LEVEL = 7;

const build = (
  building: 'WOODCUTTER' | 'HEADQUARTERS' | 'BARRACKS' | 'SMITHY' | 'STABLE',
  level: number,
): PlanLine => ({
  kind: 'build',
  building,
  level,
});
const wood = (level: number): PlanLine => build('WOODCUTTER', level);
const hq = (level: number): PlanLine => build('HEADQUARTERS', level);

describe('API lines', () => {
  it('turn into plan lines and back, the recruit line with level 0', () => {
    const api = [
      { building: 'WOODCUTTER' as const, level: SOME, recruit: null },
      { building: null, level: 0, recruit: 'B' as const },
    ];
    const lines = fromApiLines(api);
    expect(lines).toEqual([wood(SOME), { kind: 'recruit', template: 'B' }]);
    expect(toApiLines(lines)).toEqual(api);
  });
});

describe('appendNextLevel', () => {
  it('appends the next level after what the lines reach', () => {
    expect(appendNextLevel([], 'WOODCUTTER', buildings)).toEqual([wood(1)]);
    expect(appendNextLevel([wood(SOME)], 'WOODCUTTER', buildings)).toEqual([wood(SOME), wood(SOME + 1)]);
    // the Town Hall starts at 1
    expect(appendNextLevel([], 'HEADQUARTERS', buildings)).toEqual([hq(1 + 1)]);
  });

  it('puts missing requirements in front, theirs first', () => {
    const lines = appendNextLevel([hq(HQ_FOR_BARRACKS)], 'STABLE', buildings);
    expect(lines).toEqual([
      hq(HQ_FOR_BARRACKS),
      hq(HQ_FOR_STABLE),
      build('BARRACKS', FOR_STABLE),
      build('SMITHY', FOR_STABLE),
      build('STABLE', 1),
    ]);
    expect(missingPerLine(lines, buildings).flat()).toEqual([]);
  });

  it('adds nothing at the top level', () => {
    expect(appendNextLevel([wood(TOP)], 'WOODCUTTER', buildings)).toEqual([wood(TOP)]);
  });
});

describe('missingPerLine and isSavable', () => {
  it('names what the lines above don’t reach', () => {
    const lines: PlanLine[] = [build('BARRACKS', 1), hq(HQ_FOR_BARRACKS)];
    expect(missingPerLine(lines, buildings)).toEqual([[{ building: 'HEADQUARTERS', level: HQ_FOR_BARRACKS }], []]);
    expect(isSavable('A', lines, buildings)).toBe(false);
    expect(isSavable('A', moveLine(lines, 1, -1), buildings)).toBe(true);
  });

  it('wants a name, a line and one recruit line at most', () => {
    expect(isSavable(' ', [wood(1)], buildings)).toBe(false);
    expect(isSavable('A', [], buildings)).toBe(false);
    const two: PlanLine[] = [wood(1), { kind: 'recruit', template: 'A' }, { kind: 'recruit', template: 'B' }];
    expect(isSavable('A', two, buildings)).toBe(false);
  });
});

describe('editing lines', () => {
  it('keeps one recruit line and points it at the new template', () => {
    const once = setRecruitLine([wood(1)], 'A');
    expect(once).toEqual([wood(1), { kind: 'recruit', template: 'A' }]);
    expect(setRecruitLine(once, 'C')).toEqual([wood(1), { kind: 'recruit', template: 'C' }]);
  });

  it('keeps levels within 1 … the top level', () => {
    expect(changeLevel([wood(1)], 0, -1, buildings)).toEqual([wood(1)]);
    expect(changeLevel([wood(TOP - 1)], 0, 1, buildings)).toEqual([wood(TOP)]);
    expect(changeLevel([wood(TOP)], 0, 1, buildings)).toEqual([wood(TOP)]);
  });

  it('moves and removes lines, nothing past the ends', () => {
    expect(moveLine([wood(1), hq(SOME)], 0, 1)).toEqual([hq(SOME), wood(1)]);
    expect(moveLine([wood(1), hq(SOME)], 0, -1)).toEqual([wood(1), hq(SOME)]);
    expect(removeLine([wood(1), hq(SOME)], 0)).toEqual([hq(SOME)]);
  });
});

describe('linesFromLevels', () => {
  it('builds the village’s levels with requirements in front, leaving start levels and missing halls out', () => {
    const lines = linesFromLevels(village, buildings);
    expect(lines).toEqual([
      hq(HQ_FOR_STABLE),
      build('BARRACKS', FOR_STABLE),
      build('SMITHY', FOR_STABLE),
      build('STABLE', STABLE_LEVEL),
      wood(WOOD_LEVEL),
    ]);
    expect(missingPerLine(lines, buildings).flat()).toEqual([]);
  });
});

describe('lineState', () => {
  it('marks lines above the current one as done', () => {
    expect([0, 1, 1 + 1].map((i) => lineState(i, 1))).toEqual(['done', 'current', 'next']);
  });
});

import { describe, expect, it } from 'vitest';
import { MAX_PLAN_LINES, type PlanBuilding, type PlanLine } from './buildPlans';
import fixture from './buildPlans.fixture.json';
import { buildingTypes } from './gameData';
import { MAX_PLAN_CODE_LENGTH, decodePlan, encodePlan } from './planCode';

const TOP = 30;
const buildings = [
  ...(fixture.buildings as PlanBuilding[]),
  { building: 'CLAY_PIT', startLevel: 0, maxLevel: TOP, requires: [], available: true },
  { building: 'IRON_MINE', startLevel: 0, maxLevel: TOP, requires: [], available: true },
] satisfies PlanBuilding[];

const lines: PlanLine[] = [
  { kind: 'build', building: 'HEADQUARTERS', level: 3 },
  { kind: 'build', building: 'BARRACKS', level: 1 },
  { kind: 'recruit', template: 'B' },
  { kind: 'build', building: 'CLAY_PIT', level: 10 },
  { kind: 'mines', level: 12 },
];

describe('encodePlan and decodePlan', () => {
  it('write a plan as one line of text and read it back', () => {
    const code = encodePlan('Wirtschaft', lines);

    expect(code).toBe('BFP1|Wirtschaft|HEADQUARTERS:3,BARRACKS:1,RECRUIT:B,CLAY_PIT:10,MINES:12');
    expect(decodePlan(code, buildings)).toEqual({ ok: true, name: 'Wirtschaft', lines });
  });

  it('keep any character of the name, since the name travels URL-encoded', () => {
    const code = encodePlan('A|B, C: ä€', lines);

    expect(code).not.toContain(' ');
    expect(decodePlan(code, buildings)).toMatchObject({ ok: true, name: 'A|B, C: ä€' });
  });

  it('ignore spaces and line breaks a message app adds', () => {
    const code = encodePlan('Eco', lines);
    const wrapped = ` \n${code.slice(0, 10)}\n  ${code.slice(10)}\n`;

    expect(decodePlan(wrapped, buildings)).toEqual({ ok: true, name: 'Eco', lines });
  });
});

describe('decodePlan with text from other players', () => {
  const error = (text: string) => {
    const result = decodePlan(text, buildings);
    return result.ok ? 'ok' : result.error;
  };

  it('refuses what is no plan code', () => {
    expect(error('')).toBe('empty');
    expect(error('   \n ')).toBe('empty');
    expect(error('hello')).toBe('format');
    expect(error('BFP2|Eco|HEADQUARTERS:3')).toBe('format');
    expect(error('BFP1|Eco')).toBe('format');
    expect(error('BFP1|Eco|')).toBe('format');
    expect(error('BFP1|Eco|HEADQUARTERS:3|more')).toBe('format');
    expect(error('BFP1|Eco|HEADQUARTERS')).toBe('format');
    expect(error('BFP1|Eco|HEADQUARTERS:3:4')).toBe('format');
    expect(error('BFP1|Eco|,,')).toBe('format');
  });

  it('refuses a name that is empty, too long, broken or holds control characters', () => {
    expect(error('BFP1||HEADQUARTERS:3')).toBe('badName');
    expect(error('BFP1|%20%20|HEADQUARTERS:3')).toBe('badName');
    expect(error(`BFP1|${'x'.repeat(17)}|HEADQUARTERS:3`)).toBe('badName');
    expect(error('BFP1|%E0%A4%A|HEADQUARTERS:3')).toBe('badName');
    expect(error('BFP1|%|HEADQUARTERS:3')).toBe('badName');
    expect(error('BFP1|a%00b|HEADQUARTERS:3')).toBe('badName');
    expect(error('BFP1|a%0Ab|HEADQUARTERS:3')).toBe('badName');
    expect(error('BFP1|a%E2%80%AEb|HEADQUARTERS:3')).toBe('badName');
  });

  it('reads the mines line with a level up to the lowest top level of the three mines', () => {
    expect(error('BFP1|Eco|MINES:1')).toBe('ok');
    expect(error(`BFP1|Eco|MINES:${TOP}`)).toBe('ok');
    expect(error('BFP1|Eco|MINES:0')).toBe('badLevel');
    expect(error(`BFP1|Eco|MINES:${TOP + 1}`)).toBe('badLevel');
    expect(error('BFP1|Eco|MINES:x')).toBe('badLevel');
    expect(error('BFP1|Eco|MINES')).toBe('format');
    expect(error('BFP1|Eco|mines:3')).toBe('unknownBuilding');
  });

  it('knows only the game’s buildings, by exact name', () => {
    expect(error('BFP1|Eco|TOWER:1')).toBe('unknownBuilding');
    expect(error('BFP1|Eco|headquarters:1')).toBe('unknownBuilding');
    expect(error('BFP1|Eco|__proto__:1')).toBe('unknownBuilding');
    expect(error('BFP1|Eco|constructor:1')).toBe('unknownBuilding');
    expect(error('BFP1|Eco|toString:1')).toBe('unknownBuilding');
    expect(error('BFP1|Eco|<script>:1')).toBe('unknownBuilding');
    // a real building the editor doesn't offer
    expect(buildingTypes).toContain('WALL');
    expect(error('BFP1|Eco|WALL:1')).toBe('unknownBuilding');
  });

  it('keeps every level within 1 and the building’s top, in plain digits', () => {
    expect(error(`BFP1|Eco|CLAY_PIT:${TOP}`)).toBe('ok');
    for (const level of [
      '0',
      `${TOP + 1}`,
      '-1',
      '1.5',
      '1e1',
      '0x10',
      '',
      ' ',
      '١٠',
      '٣',
      '999999999999',
      '9007199254740993',
      'NaN',
    ]) {
      expect(error(`BFP1|Eco|CLAY_PIT:${level}`)).toBe('badLevel');
    }
  });

  it('takes recruit letters A to E and one recruit line', () => {
    expect(error('BFP1|Eco|RECRUIT:E')).toBe('ok');
    expect(error('BFP1|Eco|RECRUIT:F')).toBe('badTemplate');
    expect(error('BFP1|Eco|RECRUIT:a')).toBe('badTemplate');
    expect(error('BFP1|Eco|RECRUIT:')).toBe('badTemplate');
    expect(error('BFP1|Eco|RECRUIT:A,CLAY_PIT:3,RECRUIT:B')).toBe('twoRecruitLines');
  });

  it('stops at the most lines a plan has and at an enormous text', () => {
    const many = (count: number) => `BFP1|Eco|${Array.from({ length: count }, () => 'CLAY_PIT:1').join(',')}`;

    expect(error(many(MAX_PLAN_LINES))).toBe('ok');
    expect(error(many(MAX_PLAN_LINES + 1))).toBe('tooManyLines');
    expect(error('x'.repeat(MAX_PLAN_CODE_LENGTH + 1))).toBe('tooLong');
    expect(error(many(MAX_PLAN_CODE_LENGTH))).toBe('tooLong');
  });

  it('never hands on anything but plain lines', () => {
    const result = decodePlan('BFP1|Eco|CLAY_PIT:2,RECRUIT:C', buildings);

    expect(result).toEqual({
      ok: true,
      name: 'Eco',
      lines: [
        { kind: 'build', building: 'CLAY_PIT', level: 2 },
        { kind: 'recruit', template: 'C' },
      ],
    });
  });
});

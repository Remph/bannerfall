import { describe, expect, it } from 'vitest';
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import { letterText, type LordLetter } from './letters';

const letter = (over: Partial<LordLetter> & Pick<LordLetter, 'kind'>): LordLetter => ({
  guild: null,
  target: null,
  units: null,
  resources: null,
  arriveAt: null,
  departAt: null,
  voice: null,
  request: null,
  count: null,
  attacker: null,
  goal: null,
  court: null,
  ...over,
});

const target = { id: 7, name: 'Wulframsfeste', x: 61, y: 52, playerId: 3, playerName: 'Baron Wulfram' };

const ALL_KINDS = [
  'JOINED',
  'REFUSED_TRUST',
  'REFUSED_FULL',
  'LEFT_TRUST',
  'LEFT_ATTACKED',
  'ORDER_ATTACK',
  'ORDER_ATTACK_PLANNED',
  'ORDER_SUPPORT',
  'ORDER_SEND',
  'ORDER_HOLD',
  'ORDER_REFUSED_TRUST',
  'ORDER_REFUSED_BOUND',
  'ORDER_TOO_STRONG',
  'ORDER_NO_TROOPS',
  'ORDER_NO_SURPLUS',
  'ORDER_NO_ROOM',
  'ORDER_NOT_IN_TIME',
  'ORDER_UNSEEN',
  'ORDER_CAPS',
  'ORDER_PROTECTED',
  'HELP_SUPPORT',
  'HELP_RESOURCES',
  'THANKS',
  'WARNING',
  'COURT_TAUNT',
] as const satisfies readonly LordLetter['kind'][];

const VOICES = ['STEADY', 'CAREFUL', 'BOLD'] as const;

describe('letterText (2.1 S2b)', () => {
  it('writes a lord’s letter in the reader’s language', () => {
    const joined = letter({ kind: 'JOINED', guild: '[KN] Knights' });
    expect(letterText(en, joined, 'en')).toContain('[KN] Knights');
    expect(letterText(de, joined, 'de')).toContain('[KN] Knights');
    expect(letterText(en, joined, 'en')).not.toBe(letterText(de, joined, 'de'));
  });

  it('names "your guild" when the guild is gone', () => {
    expect(letterText(en, letter({ kind: 'LEFT_TRUST' }), 'en')).toContain(en.messages.yourGuild);
  });

  it('reads a letter written before S3, with only its kind and guild', () => {
    const old = { kind: 'JOINED', guild: 'G' } as LordLetter;
    expect(letterText(en, old, 'en')).toContain('G');
  });

  it('has a text for every kind in every voice in both languages', () => {
    for (const kind of ALL_KINDS) {
      for (const voice of VOICES) {
        for (const [dictionary, language] of [
          [en, 'en'],
          [de, 'de'],
        ] as const) {
          const text = letterText(
            dictionary,
            letter({ kind, voice, guild: 'G', target, count: 300, attacker: 'Baron', goal: 'WALL' }),
            language,
          );
          expect(text.length).toBeGreaterThan(10);
          expect(text).not.toContain('undefined');
          expect(text).not.toContain('null');
        }
      }
    }
  });
});

describe('order letters (2.1 S3)', () => {
  it('names the village, the troops and when they land', () => {
    const text = letterText(
      en,
      letter({
        kind: 'ORDER_ATTACK',
        target,
        units: { AXEMAN: 1240, RAM: 10, SPEARMAN: 0 },
        arriveAt: '2026-11-19T23:12:00Z',
      }),
      'en',
    );
    expect(text).toContain('Wulframsfeste (61\u2060|\u206052)');
    expect(text).toContain('Axeman 1,240, Ram 10');
    expect(text).not.toContain('Spearman');
    expect(text).toMatch(/\d\d:\d\d/);
  });

  it('writes the numbers the German way', () => {
    const text = letterText(
      de,
      letter({
        kind: 'ORDER_SEND',
        target,
        resources: { wood: 1200, clay: 0, iron: 800 },
        arriveAt: '2026-11-19T23:12:00Z',
      }),
      'de',
    );
    expect(text).toContain('Holz 1.200, Eisen 800');
    expect(text).not.toContain('Lehm');
  });

  it('gives a planned march its departure and its landing', () => {
    const text = letterText(
      en,
      letter({
        kind: 'ORDER_ATTACK_PLANNED',
        target,
        units: { AXEMAN: 340 },
        departAt: '2026-11-19T22:00:00Z',
        arriveAt: '2026-11-19T23:12:30Z',
      }),
      'en',
    );
    expect(text).toContain('Axeman 340');
    expect(text).toMatch(/march at .+ land at /);
  });

  it('says "the village" when the village is gone', () => {
    expect(letterText(en, letter({ kind: 'ORDER_TOO_STRONG' }), 'en')).toContain(en.messages.letterVillage);
  });
});

describe('voices and help letters (2.1 S4)', () => {
  it('writes each voice differently', () => {
    const texts = VOICES.map((voice) => letterText(en, letter({ kind: 'JOINED', voice, guild: 'G' }), 'en'));
    expect(new Set(texts).size).toBe(3);
  });

  it('reads a letter without a voice in the steady one', () => {
    const plain = letter({ kind: 'JOINED', guild: 'G' });
    expect(letterText(en, plain, 'en')).toBe(letterText(en, { ...plain, voice: 'STEADY' }, 'en'));
  });

  it('asks for defenders: the attacker, the village, how many and by when', () => {
    const text = letterText(
      en,
      letter({ kind: 'HELP_SUPPORT', target, count: 1500, attacker: 'Baron Grim', arriveAt: '2026-11-19T23:12:00Z' }),
      'en',
    );
    expect(text).toContain('Baron Grim');
    expect(text).toContain('Wulframsfeste (61\u2060|\u206052)');
    expect(text).toContain('1,500 defenders');
    expect(text).toMatch(/\d\d:\d\d/);
  });

  it('asks for resources for the wall or the next noble', () => {
    const ask = (goal: string) =>
      letterText(
        de,
        letter({ kind: 'HELP_RESOURCES', target, goal, resources: { wood: 400, clay: 0, iron: 200 } }),
        'de',
      );
    expect(ask('WALL')).toContain('Wall');
    expect(ask('NOBLE')).toContain('Adliger');
    expect(ask('WALL')).toContain('Holz 400, Eisen 200');
  });

  it('thanks with or without a shipment', () => {
    const bare = letterText(en, letter({ kind: 'THANKS' }), 'en');
    const shipped = letterText(
      en,
      letter({
        kind: 'THANKS',
        target,
        resources: { wood: 500, clay: 500, iron: 0 },
        arriveAt: '2026-11-19T23:12:00Z',
      }),
      'en',
    );
    expect(bare).not.toContain('Wood');
    expect(shipped).toContain('Wood 500, Clay 500');
  });

  it('names the court in a taunt', () => {
    const text = letterText(
      en,
      letter({ kind: 'COURT_TAUNT', guild: 'Raven Court', court: { key: null, numeral: null } }),
      'en',
    );
    expect(text).toContain(en.realtime.courtName(null, null, 'Raven Court'));
  });
});

describe('letters without their facts (UI review 2.1, finding 13)', () => {
  for (const [name, dictionary] of [
    ['en', en],
    ['de', de],
  ] as const) {
    it(`start with a capital and leave no holes in ${name}`, () => {
      for (const voice of VOICES) {
        for (const kind of ALL_KINDS) {
          const text = letterText(dictionary, letter({ kind, voice }), name);
          expect(text, `${voice} ${kind}`).toMatch(/^\p{Lu}/u);
          expect(text, `${voice} ${kind}`).not.toMatch(/ {2}| [.,:;!?]|\b(at|um|bis|by|before|with|mit)[.,]/u);
          expect(text, `${voice} ${kind}`).toMatch(/[.!?]$/u);
          // every sentence starts with a capital
          expect(text, `${voice} ${kind}`).not.toMatch(/[.!?] \p{Ll}/u);
        }
      }
    });
  }

  it('leave the time out beside a live request (finding 06)', () => {
    const asked = letter({
      kind: 'HELP_SUPPORT',
      voice: 'CAREFUL',
      target,
      count: 650,
      attacker: 'Gräfin Irmgard',
      arriveAt: '2026-11-20T22:55:00Z',
    });
    expect(letterText(en, asked, 'en')).toContain('lands at');
    const live = letterText(en, asked, 'en', { times: false });
    expect(live).toBe(
      'Please, I need you. Gräfin Irmgard’s army marches on Wulframsfeste (61\u2060|\u206052). I cannot hold alone: send 650 defenders, I beg you.',
    );
    expect(letterText(de, asked, 'de', { times: false })).not.toMatch(/\d{2}:\d{2}/u);
  });
});

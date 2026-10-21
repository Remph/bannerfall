// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { BattleReport, DefenderStack } from '../../api/queries/reports';
import { renderPage } from '../../testing/renderPage';
import { BattleDetails } from './BattleDetails';

const attacker = { id: 7, name: 'Red keep', x: 10, y: 10, playerId: 3, playerName: 'Red' };
const defender = { id: 59, name: 'Blue hill', x: 12, y: 11, playerId: 4, playerName: 'Blue' };

function battle(overrides: Partial<BattleReport>): BattleReport {
  return {
    type: 'BATTLE',
    attacker,
    defender,
    outcome: 'DEFENDER_WON',
    luck: 0,
    attackerUnits: { AXEMAN: 10, PALADIN: 1 },
    attackerLosses: { AXEMAN: 10, PALADIN: 1 },
    attackerLevels: {},
    catapultTarget: null,
    defenderSide: null,
    scouting: null,
    loot: { wood: 0, clay: 0, iron: 0 },
    loyalty: null,
    banners: null,
    haulCapacity: null,
    nightBonus: null,
    morale: null,
    ...overrides,
  };
}

const noBanners = {
  attackBonus: null,
  defenceBonus: null,
  found: null,
  theft: null,
  stolen: null,
  attackStandard: null,
  foundStandard: null,
  standardTheft: null,
};

function open(kind: 'ATTACK' | 'DEFENSE', report: BattleReport) {
  return renderPage({
    path: '/v/:villageId/reports',
    url: '/v/59/reports',
    element: <BattleDetails kind={kind} battle={report} villageId={59} />,
    data: [],
  });
}

/** Paladin standards in reports (1.4, 02-game-rules §21.18). */
describe('BattleDetails with paladin standards', () => {
  afterEach(cleanup);

  it('shows the defenders their stack’s standard and the standard their thieves took', () => {
    const home: DefenderStack = {
      home: defender,
      units: { SPEARMAN: 100, BANNER_THIEF: 10 },
      losses: { SPEARMAN: 17, BANNER_THIEF: 2 },
      levels: {},
      standard: { type: 'SPEAR_STANDARD', tier: 2, effectiveTier: 1 },
    };
    open(
      'DEFENSE',
      battle({
        defenderSide: {
          units: home.units,
          losses: home.losses,
          stacks: [home],
          wallBefore: 0,
          wallAfter: 0,
          catapultLevelBefore: null,
          catapultLevelAfter: null,
          levels: {},
        },
        banners: {
          ...noBanners,
          attackStandard: { type: 'AXE_STANDARD', tier: 3, effectiveTier: 3 },
          standardTheft: {
            thieves: 8,
            chance: 0.2163,
            stolen: true,
            standard: { type: 'AXE_STANDARD', tier: 3 },
          },
        },
      }),
    );

    expect(screen.getByText('Standard of the attack')).toBeTruthy();
    expect(screen.getByText('Axe standard, tier III')).toBeTruthy();
    expect(screen.getByText('Spear standard, tier II: works as tier I')).toBeTruthy();
    expect(screen.getByText('8 survived')).toBeTruthy();
    expect(screen.getByText('21.6%')).toBeTruthy();
    expect(
      screen.getByText('Your banner thieves took the attacker’s Axe standard, tier III! It is in your stash now.'),
    ).toBeTruthy();
  });

  it('tells the attacker only that his standard was taken', () => {
    open(
      'ATTACK',
      battle({
        banners: {
          ...noBanners,
          attackStandard: { type: 'AXE_STANDARD', tier: 1, effectiveTier: 1 },
          standardTheft: { thieves: null, chance: null, stolen: true, standard: { type: 'AXE_STANDARD', tier: 1 } },
        },
      }),
    );

    expect(screen.getByText('The defenders’ banner thieves took your Axe standard, tier I.')).toBeTruthy();
    expect(screen.queryByText('Chance to take the standard')).toBeNull();
  });

  it('names a standard the winner found', () => {
    open(
      'ATTACK',
      battle({
        outcome: 'ATTACKER_WON',
        banners: { ...noBanners, foundStandard: { type: 'RAM_STANDARD', tier: 1 } },
      }),
    );

    expect(screen.getByText('You found a paladin standard')).toBeTruthy();
    expect(screen.getByText('Ram standard, tier I')).toBeTruthy();
  });
});

/** The night bonus (1.4, 02-game-rules §8.1). */
describe('BattleDetails with the night bonus', () => {
  afterEach(cleanup);

  it('tells both sides when the defence had the night bonus', () => {
    open('ATTACK', battle({ nightBonus: 2 }));
    expect(screen.getByText('Night bonus: the attack arrived at night, the village defended ×2.')).toBeTruthy();
    cleanup();

    open('DEFENSE', battle({ nightBonus: 1.5 }));
    expect(screen.getByText('Night bonus: the attack arrived at night, the village defended ×1.5.')).toBeTruthy();
    cleanup();

    open('ATTACK', battle({}));
    expect(screen.queryByText(/Night bonus/)).toBeNull();
  });
});

/** Morale (1.6, 02-game-rules §8.2). */
describe('BattleDetails with morale', () => {
  afterEach(cleanup);

  it('explains the attacker’s morale in both copies, only below 100 %', () => {
    open('ATTACK', battle({ morale: 0.48 }));
    expect(
      screen.getByText(
        'Morale 48%: Blue has far fewer points than you, so your attack hit with only 48% of its strength.',
      ),
    ).toBeTruthy();
    cleanup();

    open('DEFENSE', battle({ morale: 0.3 }));
    expect(
      screen.getByText(
        'Morale 30%: Blue has far fewer points than Red, so the attack hit with only 30% of its strength.',
      ),
    ).toBeTruthy();
    cleanup();

    open('ATTACK', battle({}));
    expect(screen.queryByText(/Morale/)).toBeNull();
  });
});

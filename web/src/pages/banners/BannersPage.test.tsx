// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bannersQueryKey } from '../../api/queries/banners';
import { villageQueryKey } from '../../api/queries/villages';
import { banners, FIXTURE_NOW, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { BannersPage } from './BannersPage';

function open(data = banners, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId/banners',
    url: `/v/${village.id}/banners`,
    element: <BannersPage />,
    data: [
      [villageQueryKey(village.id), village],
      [bannersQueryKey, data],
    ],
    language,
  });
}

/** The slot card ("Banner in this village"), found by its heading. */
function slotCard() {
  return screen.getByRole('heading', { name: 'Banner in this village' }).closest('section') ?? document.body;
}

describe('BannersPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the banner that flies here with what it does at the hall level', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Banner Hall' })).toBeTruthy();
    const slot = within(slotCard());
    expect(slot.getByText('Harvest banner')).toBeTruthy();
    expect(slot.getByText('Tier II')).toBeTruthy();
    expect(slot.getByText(/Wood, clay and iron production \+10\s?%/)).toBeTruthy();
    expect(slot.getByText('Works at full strength.')).toBeTruthy();
    expect(slot.getByRole('button', { name: 'Take down' })).toHaveProperty('disabled', false);
    // the picture is decorative next to the written name, so screen readers skip it
    expect(slot.queryByRole('img')).toBeNull();
  });

  it('lists the stash with one swap button per type and tier, identical banners counted', () => {
    open();

    expect(screen.getByRole('heading', { name: 'Stash (4)' })).toBeTruthy();
    // the War banner III in a level 2 hall would work as tier II
    expect(screen.getByText('The hall is too small for this tier: the banner works as tier II.')).toBeTruthy();
    // the two Bulwark banners I share a row
    expect(screen.getAllByRole('button', { name: /^Swap in / })).toHaveLength(3);
    expect(screen.getByText('× 2')).toBeTruthy();
  });

  it('locks the slot and hides the stash buttons while an attack is on its way', () => {
    open({ ...banners, villages: banners.villages.map((entry) => ({ ...entry, locked: true })) });

    expect(
      screen.getByText('An attack is on its way: banners cannot be changed here until it has arrived.'),
    ).toBeTruthy();
    expect(within(slotCard()).getByRole('button', { name: 'Take down' })).toHaveProperty('disabled', true);
    expect(screen.queryAllByRole('button', { name: /^Swap in / })).toHaveLength(0);
  });

  it('offers the three tier I banners for fusing, but not the single tier III one', () => {
    open();

    expect(screen.getByText('Tier I (3)')).toBeTruthy();
    expect(screen.queryByText('Tier III (1)')).toBeNull();
    expect(screen.queryByText('You need three banners of the same tier (I or II) in your stash.')).toBeNull();
  });

  it('says what fusing needs when no tier has three banners', () => {
    open({ ...banners, banners: banners.banners.filter((banner) => banner.type !== 'HASTE') });

    expect(screen.getByText('You need three banners of the same tier (I or II) in your stash.')).toBeTruthy();
  });

  // ---- paladin standards (1.4, 02-game-rules §21.18) ----

  /** The fixture with an Axe standard III in the village and three Spear standards I in the stash. */
  function withStandards(changes: Partial<(typeof banners)['villages'][number]> = {}) {
    return {
      ...banners,
      banners: [
        ...banners.banners,
        { id: 6, type: 'AXE_STANDARD' as const, tier: 3, villageId: village.id, equippedAt: FIXTURE_NOW },
        { id: 7, type: 'SPEAR_STANDARD' as const, tier: 1, villageId: null, equippedAt: null },
        { id: 8, type: 'SPEAR_STANDARD' as const, tier: 1, villageId: null, equippedAt: null },
        { id: 9, type: 'SWORD_STANDARD' as const, tier: 1, villageId: null, equippedAt: null },
      ],
      villages: banners.villages.map((entry) => ({
        ...entry,
        standardId: 6,
        standardEffectiveTier: 1,
        ...changes,
      })),
    };
  }

  function standardCard() {
    return screen.getByRole('heading', { name: 'Paladin standard' }).closest('section') ?? document.body;
  }

  it('shows the standard of this village at the level of the paladin', () => {
    open(withStandards());

    const card = within(standardCard());
    expect(card.getByText('Axe standard')).toBeTruthy();
    expect(card.getByText('Tier III')).toBeTruthy();
    expect(card.getByText(/Attack strength of the axemen it goes with \+5\s?%/)).toBeTruthy();
    expect(card.getByText('Your paladin’s level caps it: the standard works as tier I.')).toBeTruthy();
    expect(card.getByText(/Your paladin is at level 1/)).toBeTruthy();
    expect(card.getByText(/banner thieves may take it/)).toBeTruthy();
    expect(card.getByRole('button', { name: 'Take down' })).toHaveProperty('disabled', false);
    // the village list names it too
    expect(screen.getByText(/Harvest banner.*Axe standard/)).toBeTruthy();
  });

  it('keeps the standards apart in the stash and when fusing', () => {
    open(withStandards());

    expect(screen.getByRole('heading', { name: 'Village banners' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Paladin standards' })).toBeTruthy();
    expect(screen.getByText('Standards, tier I (3)')).toBeTruthy();
    // the banners' tier I group still counts only the banners
    expect(screen.getByText('Tier I (3)')).toBeTruthy();
  });

  it('locks only the standard while an attack with a paladin from here is under way', () => {
    open(withStandards({ standardLocked: true }));

    expect(within(standardCard()).getByText(/the standard cannot be changed until it is over/)).toBeTruthy();
    expect(within(standardCard()).getByRole('button', { name: 'Take down' })).toHaveProperty('disabled', true);
    expect(within(slotCard()).getByRole('button', { name: 'Take down' })).toHaveProperty('disabled', false);
    // the banners can still be swapped, the standards not
    expect(screen.getAllByRole('button', { name: /^Swap in / })).toHaveLength(3);
  });

  it('has no standard card in a world without paladins and without standards', () => {
    open({ ...banners, paladinsEnabled: false });

    expect(screen.queryByRole('heading', { name: 'Paladin standard' })).toBeNull();
  });

  it('speaks German', () => {
    open(banners, 'de');

    expect(screen.getByRole('heading', { level: 1, name: 'Bannerhalle' })).toBeTruthy();
  });
});

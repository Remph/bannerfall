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

  it('speaks German', () => {
    open(banners, 'de');

    expect(screen.getByRole('heading', { level: 1, name: 'Bannerhalle' })).toBeTruthy();
  });
});

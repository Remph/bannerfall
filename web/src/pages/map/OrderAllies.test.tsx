// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { meQueryKey } from '../../api/queries/auth';
import { diplomacyQueryKey, type Diplomacy } from '../../api/queries/diplomacy';
import { farmQueryKey } from '../../api/queries/farm';
import { farmTemplatesQueryKey } from '../../api/queries/farmTemplates';
import type { MapVillage } from '../../api/queries/map';
import { worldQueryKey } from '../../api/queries/world';
import { village as home, me, world } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { VillagePanel } from './VillagePanel';

const target: MapVillage = {
  id: 90,
  x: 63,
  y: 57,
  name: 'Wulframsfeste',
  playerId: 30,
  playerName: 'Baron Wulfram',
  npc: true,
  points: 900,
  loyalty: null,
  loyaltySeenAt: null,
  guildId: null,
  guildTag: null,
  nextBannerRollAt: null,
  keep: false,
  note: null,
};

const diplomacy = (allies: number): Diplomacy => ({
  soloMode: true,
  leader: true,
  guildFull: false,
  court: null,
  mine: null,
  allVillages: 100,
  allies: Array.from({ length: allies }, (_, index) => ({
    playerId: 40 + index,
    name: `Lord ${index}`,
    guild: null,
    ally: true,
    trust: 60,
    bar: null,
    changes: [],
    villages: 2,
    points: 800,
    home: null,
    invitationId: null,
    inviteAfter: null,
    variant: null,
    order: null,
  })),
  lords: [],
});

function panel(soloMode: boolean, allies: number) {
  renderPage({
    path: '/',
    url: '/',
    element: <VillagePanel village={target} kind="npc" currentVillage={home} onClose={vi.fn()} onCover={vi.fn()} />,
    data: [
      [meQueryKey, me],
      [worldQueryKey, { ...world, soloMode }],
      [diplomacyQueryKey, diplomacy(allies)],
      [farmQueryKey(home.id), []],
      [farmTemplatesQueryKey, []],
    ],
  });
}

/** "Order allies" in the map panel (2.1 S3, 12-solo-and-allies §8). */
describe('VillagePanel: Order allies', () => {
  beforeAll(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
  });

  afterEach(cleanup);

  it('is offered in a solo world once the player has an ally', () => {
    panel(true, 1);
    expect(screen.getByRole('button', { name: 'Order allies' })).toBeTruthy();
  });

  it('is not offered without an ally or outside a solo world', () => {
    panel(true, 0);
    expect(screen.queryByRole('button', { name: 'Order allies' })).toBeNull();
    cleanup();
    panel(false, 1);
    expect(screen.queryByRole('button', { name: 'Order allies' })).toBeNull();
  });
});

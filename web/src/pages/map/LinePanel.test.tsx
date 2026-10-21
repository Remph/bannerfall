// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MapMovementLine } from '../../api/queries/map';
import { troopsQueryKey, type Troops, type VillageRef } from '../../api/queries/troops';
import { FIXTURE_NOW } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { LinePanel } from './LinePanel';

const HOME_ID = 59;
const HOME_X = 61;
const HOME_Y = 40;
const HILLTOP_ID = 901;
const HILLTOP_X = 52;
const HILLTOP_Y = 60;
const HILLTOP_OWNER = 7;
const BARBARIANS_ID = 900;
const BARBARIANS_X = 50;
const BARBARIANS_Y = 61;
const OWN_ATTACK_ID = 5;
const ATTACK_ON_ME_ID = 2;
const SPEARMEN = 10;
/** The "hidden" of the way and of the troops. */
const HIDDEN_FACTS = 2;
/** The cancel button reads "Cancel" and the time left, e.g. "Cancel 2:52". */
const CANCEL = /^Cancel /;

const HOME = { id: HOME_ID, name: 'Home', x: HOME_X, y: HOME_Y, playerId: 1, playerName: 'Aldric' };
const HILLTOP = {
  id: HILLTOP_ID,
  name: 'Hilltop',
  x: HILLTOP_X,
  y: HILLTOP_Y,
  playerId: HILLTOP_OWNER,
  playerName: 'Jonas',
};
const BARBARIANS = {
  id: BARBARIANS_ID,
  name: null,
  x: BARBARIANS_X,
  y: BARBARIANS_Y,
  playerId: null,
  playerName: null,
};
const DEPART = '2026-10-15T20:20:00Z';
const ARRIVE = '2026-10-15T20:40:00Z';

const ownAttack: MapMovementLine = {
  id: OWN_ATTACK_ID,
  kind: 'ATTACK',
  own: true,
  nobles: false,
  from: HOME,
  to: BARBARIANS,
  departAt: DEPART,
  arriveAt: ARRIVE,
};

const attackOnMe: MapMovementLine = {
  id: ATTACK_ON_ME_ID,
  kind: 'ATTACK',
  own: false,
  nobles: false,
  from: HILLTOP,
  to: HOME,
  departAt: null,
  arriveAt: '2026-10-15T20:50:00Z',
};

/** The rally point's lists of the home village: the own attack can still be cancelled (until 20:30). */
const troops: Troops = {
  home: {},
  supportsHere: [],
  ownUnitsElsewhere: [],
  outgoing: [
    {
      id: OWN_ATTACK_ID,
      kind: 'ATTACK',
      sourceVillage: HOME,
      targetVillage: BARBARIANS,
      homeVillage: HOME,
      units: { SPEARMAN: SPEARMEN },
      catapultTarget: null,
      loot: { wood: 0, clay: 0, iron: 0 },
      departAt: DEPART,
      arriveAt: ARRIVE,
      cancellableUntil: '2026-10-15T20:30:00Z',
      notifyReturn: false,
    },
  ],
  incoming: [
    {
      id: ATTACK_ON_ME_ID,
      kind: 'ATTACK',
      sourceVillage: HILLTOP,
      sourcePlayerName: 'Jonas',
      departAt: null,
      arriveAt: '2026-10-15T20:50:00Z',
      units: null,
    },
  ],
};

function open(line: MapMovementLine) {
  const onShow = vi.fn<(village: VillageRef) => void>();
  renderPage({
    path: '/v/:villageId/map',
    url: `/v/${HOME.id}/map`,
    element: <LinePanel line={line} onClose={() => {}} onCover={() => {}} onShow={onShow} docked />,
    data: [[troopsQueryKey(HOME.id), troops]],
  });
  return onShow;
}

describe('LinePanel', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows an own attack with its troops and the way to the outgoing list of its rally point', () => {
    const onShow = open(ownAttack);

    expect(screen.getByRole('heading', { name: 'Attack on Barbarian village (50|61)' })).toBeTruthy();
    expect(screen.getByTitle('Spearman')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Rally point' }).getAttribute('href')).toBe(`/v/${HOME.id}/rally#outgoing`);
    fireEvent.click(screen.getByRole('button', { name: 'Show target' }));
    expect(onShow).toHaveBeenCalledWith(BARBARIANS);
  });

  it('asks before cancelling, as the rally point does', () => {
    open(ownAttack);

    fireEvent.click(screen.getByRole('button', { name: CANCEL }));

    expect(screen.getByRole('dialog', { name: 'Cancel this command?' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cancel command' })).toBeTruthy();
  });

  it("keeps the troops and the way of another player's attack hidden and leads to its attacker", () => {
    const onShow = open(attackOnMe);

    expect(screen.getByRole('heading', { name: 'Attack from Hilltop (52|60)' })).toBeTruthy();
    expect(screen.getAllByText('hidden')).toHaveLength(HIDDEN_FACTS);
    expect(screen.queryByRole('button', { name: CANCEL })).toBeNull();
    expect(screen.getByRole('link', { name: 'Rally point' }).getAttribute('href')).toBe(`/v/${HOME.id}/rally#incoming`);
    fireEvent.click(screen.getByRole('button', { name: 'Show attacker' }));
    expect(onShow).toHaveBeenCalledWith(HILLTOP);
  });
});

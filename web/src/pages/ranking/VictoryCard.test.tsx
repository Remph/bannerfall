// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { myGuildQueryKey } from '../../api/queries/guilds';
import { victoryQueryKey, worldQueryKey, type Victory, type VictorySide, type World } from '../../api/queries/world';
import { FIXTURE_NOW } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { VictoryCard } from './VictoryCard';

const me: Me = {
  accountId: 1,
  username: 'ada',
  role: 'PLAYER',
  playerId: 7,
  points: 1200,
  protectedUntil: '2026-10-12T00:00:00Z',
  needsRespawn: false,
  villages: [],
};

const world: World = {
  name: 'Bannerfall 1',
  speed: 6,
  unitSpeed: 1,
  mapSize: 100,
  buildQueueSize: 5,
  buildQueueMax: 10,
  recruitQueueSize: 5,
  tradeMaxRatio: 3,
  startedAt: '2026-10-12T18:00:00Z',
  startResources: 500,
  luckRange: 0.25,
  beginnerProtectionHours: 72,
  nobleMaxDistance: 70,
  loyaltyRegenPerHour: 1,
  cancelWindowSeconds: 600,
  barbarianGrowthEnabled: true,
  npcLordCount: 4,
  npcDifficulty: 'NORMAL',
  guildsEnabled: true,
  guildMaxMembers: 10,
  barbarianWallsEnabled: false,
  barbarianWallStartDays: 3,
  barbarianWallMaxLevel: 5,
  barbarianWallsRebuild: false,
  unitLevelsEnabled: true,
  paladinVillagesPerSlot: 4,
  paladinMaxSlots: 5,
  bannersEnabled: true,
  bannerDropFactor: 1,
  marketExchangeRatio: 1.5,
  victoryMode: 'DOMINATION',
  dominationPercent: 60,
  victoryHoldHours: 48,
  keepCount: 5,
  keepPercent: 51,
  endsAt: null,
  endedAt: null,
  phase: 'OPEN',
  opensAt: null,
  registeredPlayers: 6,
  version: '1.3.0',
};

const wolves: VictorySide = {
  kind: 'GUILD',
  id: 3,
  name: 'Iron Wolves',
  tag: 'IW',
  npc: false,
  villages: 75,
  points: 41_000,
  keeps: 3,
};
const ada: VictorySide = {
  kind: 'PLAYER',
  id: 7,
  name: 'ada',
  tag: null,
  npc: false,
  villages: 20,
  points: 9_000,
  keeps: 1,
};

const running: Victory = {
  mode: 'DOMINATION',
  dominationPercent: 60,
  holdHours: 48,
  keepCount: 0,
  keepPercent: 51,
  keepsNeeded: 3,
  endsAt: '2026-11-01T18:00:00Z',
  phase: 'OPEN',
  allVillages: 120,
  sides: [wolves, ada],
  leader: wolves,
  leaderSince: '2026-10-15T08:00:00Z',
  winsAt: '2026-10-17T08:00:00Z',
  result: null,
};

/** The player's guild: none (ada plays alone) or the Iron Wolves. */
function myGuild(member: boolean) {
  const guild = { id: 3, name: 'Iron Wolves', tag: 'IW', description: '', members: [], memberCount: 1, points: 0 };
  return { guild: member ? guild : null, role: member ? 'MEMBER' : null, invitations: [] };
}

function open(
  victory: Victory,
  { language = 'en', member = false }: { language?: 'en' | 'de'; member?: boolean } = {},
) {
  return renderPage({
    path: '/ranking',
    url: '/ranking',
    element: <VictoryCard />,
    data: [
      [meQueryKey, me],
      [worldQueryKey, world],
      [victoryQueryKey, victory],
      [myGuildQueryKey, myGuild(member)],
    ],
    language,
  });
}

/** A desktop window (≥ 1024 px): the whole card. Without it jsdom is a phone. */
function onDesktop() {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: true,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }));
}

describe('VictoryCard', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('leads with who holds what and the time left, the rule in one line and the whole of it behind "Rules"', () => {
    onDesktop();
    open(running);

    expect(screen.getByRole('heading', { name: 'Victory condition' })).toBeTruthy();
    expect(screen.getByText(/^\[IW\] Iron Wolves holds 62\.5\s?% of the villages$/)).toBeTruthy();
    expect(screen.getByText('Wins in')).toBeTruthy();
    expect(screen.getByText('Whoever holds 60 % of all villages for 48 hours in a row wins.')).toBeTruthy();
    expect(screen.getByText('Rules')).toBeTruthy();
    expect(screen.getByText(/A side wins once it holds 60% of all villages on the map/)).toBeTruthy();
    expect(screen.getByText(/A guild counts as one side with all its members/)).toBeTruthy();
    expect(screen.getByText(/The world ends .* at the latest/)).toBeTruthy();
    // 75 of 120 villages, and the player himself with his tag
    expect(screen.getByText(/^62\.5\s?% · 75 villages$/)).toBeTruthy();
    expect(screen.getByText('leads')).toBeTruthy();
    expect(screen.getByText('you')).toBeTruthy();
    expect(screen.getByText(/, unless someone stops them$/)).toBeTruthy();
  });

  it('calls the player\'s own guild "your guild"', () => {
    onDesktop();
    open(running, { member: true });

    expect(screen.getByText(/^Your guild holds 62\.5\s?% of the villages$/)).toBeTruthy();
    expect(screen.getByText('your guild')).toBeTruthy();
    expect(screen.getByText(/, if you hold on$/)).toBeTruthy();
    expect(screen.queryByText(/unless someone stops them/)).toBeNull();
  });

  it('shows a running world as one line on a phone, the card inside it', () => {
    const { container } = open(running);

    const strip = container.querySelector('details');
    expect(strip).not.toBeNull();
    expect(strip?.open).toBe(false);
    // the state line is the strip's summary (and again in the card inside it)
    expect(strip?.querySelector('summary')?.textContent).toMatch(/\[IW\] Iron Wolves holds 62\.5\s?% of the villages/);
    expect(screen.queryByRole('heading', { name: 'Victory condition' })).toBeNull();
  });

  it('counts keeps as pips in a keep world and names the closest side while nobody leads', () => {
    onDesktop();
    open({
      ...running,
      mode: 'KEEPS',
      sides: [wolves, { ...ada, keeps: 0 }],
      keepCount: 5,
      leader: null,
      leaderSince: null,
      winsAt: null,
      endsAt: null,
    });

    expect(screen.getByText('Nobody leads yet')).toBeTruthy();
    expect(screen.getByRole('img', { name: '3 of 5 keeps, 3 needed' })).toBeTruthy();
    expect(screen.getByText('Closest: [IW] Iron Wolves with 3 of the 3 keeps needed.')).toBeTruthy();
    expect(screen.getByText(/5 banner keeps stand around the centre of the map/)).toBeTruthy();
    expect(screen.getByText('3 of 5 keeps')).toBeTruthy();
    // ada holds no keep in this world: not listed
    expect(screen.queryByText('you')).toBeNull();
    expect(screen.queryByText(/at the latest/)).toBeNull();
  });

  it('names no side as the closest while several are level at the top', () => {
    onDesktop();
    open({
      ...running,
      sides: [
        { ...wolves, villages: 30 },
        { ...ada, villages: 30 },
      ],
      leader: null,
      leaderSince: null,
      winsAt: null,
    });

    expect(screen.getByText(/^Level at the top: 2 sides with 25\s?% of 60 % each\.$/)).toBeTruthy();
    expect(screen.queryByText(/Closest/)).toBeNull();
  });

  it('shows the result once the world has ended: who won and how, the own place, the final standings', () => {
    const lord: VictorySide = { ...ada, id: 9, name: 'Graf Ottmar', npc: true, points: 100 };
    const ended: Victory = {
      ...running,
      phase: 'ENDED',
      sides: [],
      leader: null,
      winsAt: null,
      result: {
        reason: 'END_DATE',
        endedAt: '2026-11-01T18:00:00Z',
        winner: wolves,
        sides: [wolves, ada, { ...lord, id: 10 }, { ...lord, id: 11 }, lord],
      },
    };
    open(ended, { language: 'de' });

    expect(screen.getByRole('heading', { name: 'Ergebnis' })).toBeTruthy();
    expect(screen.getByText('[IW] Iron Wolves hat gewonnen.')).toBeTruthy();
    expect(screen.getByText('Gewonnen mit den meisten Punkten am Weltende.')).toBeTruthy();
    expect(screen.getByText('Du: Platz 2 von 5')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Endstand' })).toBeTruthy();
    expect(screen.getByText(/^41\.000 Punkte$/)).toBeTruthy();
    expect(screen.getByText('Sieger')).toBeTruthy();
    // one NPC lord in the top three, the others not
    expect(screen.getAllByText('Graf Ottmar')).toHaveLength(1);
  });

  it('cheers when the own guild won', () => {
    open(
      {
        ...running,
        phase: 'ENDED',
        sides: [],
        leader: null,
        winsAt: null,
        result: { reason: 'DOMINATION', endedAt: '2026-11-01T18:00:00Z', winner: wolves, sides: [wolves, ada] },
      },
      { member: true },
    );

    expect(screen.getByText('Your guild won!')).toBeTruthy();
    expect(screen.getByText('Won by holding the map.')).toBeTruthy();
    expect(screen.queryByText(/place/)).toBeNull();
  });
});

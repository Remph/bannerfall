// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { Link } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { pushQueryKey } from '../../api/queries/push';
import { troopsQueryKey, type Troops } from '../../api/queries/troops';
import { villageQueryKey } from '../../api/queries/villages';
import { FIXTURE_NOW, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { RallyPage } from './RallyPage';

const barbarians = { id: 900, name: null, x: 50, y: 61, playerId: null, playerName: null };

const troops: Troops = {
  home: { SPEARMAN: 100 },
  supportsHere: [],
  ownUnitsElsewhere: [],
  outgoing: [],
  incoming: [
    {
      id: 1,
      kind: 'RETURN',
      sourceVillage: barbarians,
      sourcePlayerName: null,
      departAt: '2026-10-15T20:20:00Z',
      arriveAt: '2026-10-15T20:40:00Z',
      units: { SPEARMAN: 50 },
    },
  ],
};

/** The rally point, with a link like the header's attack badge above it. */
function open(
  url = `/v/${village.id}/rally`,
  data: Troops = troops,
  world: object = { mapSize: 100 },
  pushDevices = 0,
) {
  return renderPage({
    path: '/v/:villageId/rally',
    url,
    element: (
      <>
        <Link to={{ hash: 'incoming' }}>badge</Link>
        <RallyPage />
      </>
    ),
    data: [
      [villageQueryKey(village.id), village],
      [troopsQueryKey(village.id), data],
      [['world'], world],
      [pushQueryKey, { available: true, publicKey: 'key', devices: pushDevices, kinds: [] }],
    ],
  });
}

function shownPart() {
  return document.querySelector('[data-part]')?.getAttribute('data-part');
}

describe('RallyPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('opens on the form without attacks coming in, the movement count on the switch', () => {
    open();

    expect(shownPart()).toBe('send');
    expect(screen.getByRole('button', { name: 'On the way 1' })).toBeTruthy();
  });

  it('shows the movements again when the attack badge is tapped while the page is open', () => {
    open(`/v/${village.id}/rally#incoming`);
    expect(shownPart()).toBe('moves');

    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    expect(shownPart()).toBe('send');

    // the same address again: a new visit all the same
    fireEvent.click(screen.getByRole('link', { name: 'badge' }));
    expect(shownPart()).toBe('moves');
  });

  it('gives the own troops coming home a bar, hidden from screen readers', () => {
    open();

    const bars = document.querySelectorAll('[aria-hidden="true"] > [style*="scaleX"]');
    expect(bars).toHaveLength(1);
    // 7 of 20 minutes on the way
    expect((bars[0] as HTMLElement).style.transform).toMatch(/^scaleX\(0\.35/);
  });

  it('gives an attack coming in no bar: when it set off stays hidden', () => {
    const attack = {
      id: 2,
      kind: 'ATTACK' as const,
      sourceVillage: { id: 901, name: 'Hilltop', x: 52, y: 60, playerId: 7, playerName: 'Jonas' },
      sourcePlayerName: 'Jonas',
      departAt: null,
      arriveAt: '2026-10-15T20:50:00Z',
      units: null,
    };
    open(undefined, { ...troops, incoming: [...troops.incoming, attack] });

    expect(shownPart()).toBe('moves');
    // only the own troops coming home have one
    expect(document.querySelectorAll('[aria-hidden="true"] > [style*="scaleX"]')).toHaveLength(1);
    expect(screen.getByText(/Jonas/)).toBeTruthy();
  });

  it('says once that attacks hide their troops, links each to the attacker and lists them first', () => {
    const hilltop = { id: 901, name: 'Hilltop', x: 52, y: 60, playerId: 7, playerName: 'Jonas' };
    const attack = {
      id: 2,
      kind: 'ATTACK' as const,
      sourceVillage: hilltop,
      sourcePlayerName: 'Jonas',
      departAt: null,
      arriveAt: '2026-10-15T20:50:00Z',
      units: null,
    };
    const home = { id: village.id, name: village.name, x: village.x, y: village.y, playerId: 1, playerName: 'Aldric' };
    const outgoing = {
      id: 5,
      kind: 'ATTACK' as const,
      sourceVillage: home,
      targetVillage: barbarians,
      homeVillage: home,
      units: { SPEARMAN: 10 },
      catapultTarget: null,
      loot: { wood: 0, clay: 0, iron: 0 },
      departAt: '2026-10-15T20:20:00Z',
      arriveAt: '2026-10-15T20:40:00Z',
      cancellableUntil: null,
      notifyReturn: false,
    };
    open(undefined, {
      ...troops,
      outgoing: [outgoing],
      incoming: [attack, { ...attack, id: 4, arriveAt: '2026-10-15T20:55:00Z' }],
    });

    expect(screen.getAllByText('Troops and start of other players’ attacks stay hidden.')).toHaveLength(1);
    const links = screen.getAllByRole('link', { name: 'On the map (Hilltop (52|60))' });
    expect(links).toHaveLength(2);
    expect(links[0]?.getAttribute('href')).toBe(`/v/${village.id}/map?x=52&y=60`);
    // what comes at the village before the own movements, on every screen width
    const titles = screen.getAllByRole('heading').map((heading) => heading.textContent);
    expect(titles.indexOf('Incoming')).toBeLessThan(titles.indexOf('Outgoing'));
  });

  it("gives another player's support coming in a bar, as on the map", () => {
    const support = {
      id: 3,
      kind: 'SUPPORT' as const,
      sourceVillage: { id: 901, name: 'Hilltop', x: 52, y: 60, playerId: 7, playerName: 'Jonas' },
      sourcePlayerName: 'Jonas',
      departAt: '2026-10-15T20:17:00Z',
      arriveAt: '2026-10-15T20:37:00Z',
      units: { SPEARMAN: 20 },
    };
    open(undefined, { ...troops, incoming: [support, ...troops.incoming] });

    const bars = document.querySelectorAll('[aria-hidden="true"] > [style*="scaleX"]');
    expect(bars).toHaveLength(2);
    // 10 of 20 minutes on the way
    expect((bars[0] as HTMLElement).style.transform).toMatch(/^scaleX\(0\.5/);
  });

  it('marks the attacks that land in the night bonus with the moon, not those on barbarians (1.4)', () => {
    const hilltop = { id: 901, name: 'Hilltop', x: 52, y: 60, playerId: 7, playerName: 'Jonas' };
    const home = { id: village.id, name: village.name, x: village.x, y: village.y, playerId: 1, playerName: 'Aldric' };
    // 20:50 UTC is 22:50 in Germany (summer time)
    const coming = {
      id: 2,
      kind: 'ATTACK' as const,
      sourceVillage: hilltop,
      sourcePlayerName: 'Jonas',
      departAt: null,
      arriveAt: '2026-10-15T20:50:00Z',
      units: null,
    };
    const going = {
      id: 5,
      kind: 'ATTACK' as const,
      sourceVillage: home,
      targetVillage: hilltop,
      homeVillage: home,
      units: { SPEARMAN: 10 },
      catapultTarget: null,
      loot: { wood: 0, clay: 0, iron: 0 },
      departAt: '2026-10-15T20:20:00Z',
      arriveAt: '2026-10-15T20:50:00Z',
      cancellableUntil: null,
      notifyReturn: false,
    };
    const data = {
      ...troops,
      outgoing: [going, { ...going, id: 6, targetVillage: barbarians }],
      incoming: [coming, { ...coming, id: 4, arriveAt: '2026-10-15T19:50:00Z' }],
    };
    const nightBonus = { startHour: 22, endHour: 7, factor: 2, zone: 'Europe/Berlin' };

    open(undefined, data, { mapSize: 100, nightBonus });
    // the one coming in at 22:50 and the own one on a player's village; not the barbarians, not 21:50
    expect(screen.getAllByText('Night bonus ×2')).toHaveLength(2);
    cleanup();

    open(undefined, data, { mapSize: 100, nightBonus: null });
    expect(screen.queryByText('Night bonus ×2')).toBeNull();
  });

  it('rings a bell on an outgoing attack for a player with push (1.6)', () => {
    const attack = {
      id: 5,
      kind: 'ATTACK' as const,
      sourceVillage: { ...barbarians, id: village.id, name: village.name, x: 50, y: 50 },
      targetVillage: barbarians,
      homeVillage: { ...barbarians, id: village.id, name: village.name, x: 50, y: 50 },
      units: { SPEARMAN: 10 },
      catapultTarget: null,
      loot: { wood: 0, clay: 0, iron: 0 },
      departAt: '2026-10-15T20:20:00Z',
      arriveAt: '2026-10-15T20:50:00Z',
      cancellableUntil: null,
      notifyReturn: true,
    };
    const data = { ...troops, outgoing: [attack, { ...attack, id: 6, notifyReturn: false }] };

    open(undefined, data, { mapSize: 100 }, 1);
    const bells = screen.getAllByRole('button', { name: /Notify me when they’re back/ });
    expect(bells.map((bell) => bell.getAttribute('aria-pressed'))).toEqual(['true', 'false']);
    cleanup();

    // no device: no bell
    open(undefined, data, { mapSize: 100 }, 0);
    expect(screen.queryByRole('button', { name: /Notify me when they’re back/ })).toBeNull();
  });
});

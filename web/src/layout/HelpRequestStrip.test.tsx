// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { useLocation } from 'react-router';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { helpRequestsQueryKey, type HelpRequest } from '../api/queries/diplomacy';
import { worldQueryKey } from '../api/queries/world';
import { renderPage } from '../testing/renderPage';
import { HelpRequestStrip } from './HelpRequestStrip';
import { WorldEndContext, type WorldEnd } from './worldEnd';

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock('../api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api/client')>();
  return { ...original, api: { ...original.api, POST: post } };
});

const inHours = (hours: number) => new Date(Date.now() + hours * 3_600_000).toISOString();

const wulfsburg = { id: 40, name: 'Wulfsburg', x: 48, y: 52, playerId: 11, playerName: 'Dame Hildegard' };
const hilltop = { id: 7, name: 'Hilltop', x: 45, y: 50, playerId: 3, playerName: 'Nora' };

const support: HelpRequest = {
  id: 12,
  kind: 'SUPPORT',
  lordId: 11,
  lordName: 'Dame Hildegard',
  village: wulfsburg,
  defenders: 300,
  goal: null,
  resources: { wood: 0, clay: 0, iron: 0 },
  createdAt: inHours(-1),
  deadline: inHours(2),
  letter: {
    kind: 'HELP_SUPPORT',
    guild: null,
    target: wulfsburg,
    units: null,
    resources: null,
    arriveAt: inHours(2),
    departAt: null,
    voice: 'CAREFUL',
    request: 12,
    count: 300,
    attacker: 'Baron Grim',
    goal: null,
    court: null,
  },
  from: hilltop,
  fill: { SPEARMAN: 180, SWORDSMAN: 120 },
  seconds: 1800,
  answered: 15,
  declined: -5,
  ignored: -10,
};

const resources: HelpRequest = {
  ...support,
  id: 13,
  kind: 'RESOURCES',
  lordId: 12,
  lordName: 'Lord Wilfried',
  defenders: 0,
  goal: 'WALL',
  resources: { wood: 400, clay: 0, iron: 200 },
  deadline: inHours(9),
  letter: null,
  fill: {},
  seconds: 600,
};

const soloWorld = { phase: 'OPEN', soloMode: true, mapSize: 100 };

function Where() {
  const location = useLocation();
  return <output data-testid="where">{location.pathname + location.search}</output>;
}

function open(
  requests: HelpRequest[],
  options: { url?: string; world?: object; language?: 'en' | 'de'; end?: WorldEnd } = {},
) {
  return renderPage({
    path: '*',
    url: options.url ?? '/v/7/hq',
    element: (
      <WorldEndContext value={options.end ?? { ended: false, frozen: false }}>
        <HelpRequestStrip />
        <Where />
      </WorldEndContext>
    ),
    data: [
      [worldQueryKey, options.world ?? soloWorld],
      [helpRequestsQueryKey, requests],
    ],
    language: options.language,
  });
}

/** The request strip and sheet (2.1 S4, 12-solo-and-allies §9). */
describe('HelpRequestStrip', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  afterEach(() => {
    cleanup();
    post.mockReset();
  });

  it('names the lord and the need, with the time left as the biggest thing', () => {
    open([support]);
    const strip = screen.getByRole('button', {
      name: /^Dame Hildegard asks for 300 defenders at Wulfsburg \(48\u2060\|\u206052\)/,
    });
    expect(within(strip).getByText('Dame Hildegard')).toBeTruthy();
    expect(within(strip).getByText(/^(1:5\d:\d\d|2:00:00)$/)).toBeTruthy();
    expect(within(strip).queryByText('+1')).toBeNull();
  });

  it('shows the nearer deadline first and "+1" for a second request', () => {
    open([resources, support]);
    const strip = screen.getByRole('button', { name: /and 1 more request$/ });
    expect(within(strip).getByText('Dame Hildegard')).toBeTruthy();
    expect(within(strip).getByText('+1')).toBeTruthy();
  });

  it('drops a request once its deadline has passed, before the server ends it', () => {
    open([{ ...support, deadline: inHours(-0.01) }, resources]);
    const strip = screen.getByRole('button', { name: /^Lord Wilfried asks/ });
    expect(within(strip).queryByText('+1')).toBeNull();
  });

  it('stays away outside a solo world and without requests', () => {
    open([support], { world: { ...soloWorld, soloMode: false } });
    expect(screen.queryByRole('button', { name: /asks for/ })).toBeNull();
    cleanup();
    open([]);
    expect(screen.queryByRole('button', { name: /asks for/ })).toBeNull();
  });

  it('opens the sheet: the deadline first, then the letter, the way and what it is worth', () => {
    open([support]);
    fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
    const sheet = screen.getByRole('dialog', { name: 'Dame Hildegard asks for help' });
    const texts = within(sheet)
      .getAllByText(/./)
      .map((node) => node.textContent);
    const deadline = texts.indexOf('The attack lands in');
    const letter = texts.findIndex((text) => text?.startsWith('Please, I need you.'));
    expect(deadline).toBeGreaterThanOrEqual(0);
    expect(letter).toBeGreaterThan(deadline);
    expect(within(sheet).getByText(/Baron Grim/)).toBeTruthy();
    expect(within(sheet).getByText('From Hilltop (45\u2060|\u206050) your support arrives in 0:30:00.')).toBeTruthy();
    expect(within(sheet).getByText('In time: +15 trust. Not this time: −5. No answer: −10.')).toBeTruthy();
    expect(within(sheet).getByRole('button', { name: 'Not this time' })).toBeTruthy();
  });

  it('"Send support" opens the rally point of the nearest village with the defenders filled in', async () => {
    open([support]);
    fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Send support' }));
    await waitFor(() =>
      expect(screen.getByTestId('where').textContent).toBe('/v/7/rally?x=48&y=52&units=SPEARMAN.180,SWORDSMAN.120'),
    );
  });

  it('"Send resources" opens the market with what is still needed', async () => {
    open([resources]);
    fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
    const sheet = screen.getByRole('dialog', { name: 'Lord Wilfried asks for help' });
    expect(within(sheet).getByText('Time left')).toBeTruthy();
    expect(within(sheet).getByText(/Still needed: Wood 400, Iron 200\./)).toBeTruthy();
    fireEvent.click(within(sheet).getByRole('button', { name: 'Send resources' }));
    await waitFor(() =>
      expect(screen.getByTestId('where').textContent).toBe('/v/7/market?x=48&y=52&wood=400&iron=200'),
    );
  });

  it('says when help would come too late', () => {
    open([{ ...support, seconds: 3 * 3600 }]);
    fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
    expect(screen.getByText('That is after the deadline: it would come too late.')).toBeTruthy();
  });

  it('says a no costs nothing when the help could not arrive in time', () => {
    open([{ ...support, declined: 0, ignored: 0, seconds: 99_999 }]);
    fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
    expect(screen.getByText(/saying no costs nothing/)).toBeTruthy();
  });

  it('"Not this time" declines the request', async () => {
    post.mockResolvedValue({ data: [], response: new Response(null, { status: 200 }) });
    open([support]);
    fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Not this time' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/diplomacy/requests/{requestId}/decline', {
        params: { path: { requestId: 12 } },
      }),
    );
  });

  it('opens the request a push names with ?help=', () => {
    open([support, resources], { url: '/diplomacy?help=13' });
    expect(screen.getByRole('dialog', { name: 'Lord Wilfried asks for help' })).toBeTruthy();
  });

  it('reads in German too', () => {
    open([support], { language: 'de' });
    fireEvent.click(screen.getByRole('button', { name: /bittet um 300 Verteidiger/ }));
    expect(screen.getByRole('dialog', { name: 'Dame Hildegard bittet um Hilfe' })).toBeTruthy();
    expect(screen.getByText('Der Angriff schlägt ein in')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Unterstützung schicken' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Diesmal nicht' })).toBeTruthy();
  });

  describe('UI review 2.1', () => {
    it('shows the letter without its time: the sheet’s own time speaks (06)', () => {
      open([support]);
      fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
      const letter = screen.getByText(/^Please, I need you\./);
      expect(letter.textContent).not.toMatch(/lands at|\d:\d\d/);
    });

    it('starts the sheet’s line with a capital (05)', () => {
      open([resources]);
      fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
      expect(screen.getByText(/^Resources for the wall at Wulfsburg/)).toBeTruthy();
    });

    it('says in one line that no village can send, without a send button (01)', () => {
      open([{ ...support, from: null, fill: {}, seconds: null }]);
      fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
      expect(screen.getByText('None of your villages has defenders at home.')).toBeTruthy();
      expect(screen.queryByRole('button', { name: 'Send support' })).toBeNull();
      cleanup();
      open([{ ...resources, from: null, seconds: null }]);
      fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
      expect(screen.getByText('None of your villages has a market with a free merchant.')).toBeTruthy();
    });

    it('turns both answers off while paused, with the reason for the host (04)', () => {
      open([support], { end: { ended: false, frozen: true, paused: true, host: true } });
      fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
      expect(screen.getByText('The world is paused: press play to go on')).toBeTruthy();
      expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Not this time' }).disabled).toBe(true);
      expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Send support' }).disabled).toBe(true);
    });

    it('keeps only the reason after the end, also where the page stays open (04)', () => {
      open([support], { end: { ended: true, frozen: false } });
      fireEvent.click(screen.getByRole('button', { name: /asks for/ }));
      expect(screen.getByText('The world has ended')).toBeTruthy();
      expect(screen.queryByRole('button', { name: 'Not this time' })).toBeNull();
      expect(screen.queryByRole('button', { name: 'Send support' })).toBeNull();
    });

    it('lives in an aside, so it makes room for the Now column (02)', () => {
      open([support]);
      expect(screen.getByRole('complementary', { name: 'Help request' })).toBeTruthy();
    });
  });
});

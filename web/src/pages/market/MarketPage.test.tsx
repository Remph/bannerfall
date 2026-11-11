// @vitest-environment jsdom
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey } from '../../api/queries/auth';
import { marketQueryKey, type Market, type Transport } from '../../api/queries/market';
import { villageQueryKey } from '../../api/queries/villages';
import { worldQueryKey } from '../../api/queries/world';
import { FIXTURE_NOW, me, village, world } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { MarketPage } from './MarketPage';

const market: Market = {
  merchants: { total: 5, free: 3 },
  outgoing: [],
  incoming: [],
  offers: [],
  exchange: { ratio: 1.5, dailyCap: 0, received: { wood: 0, clay: 0, iron: 0 }, resetsAt: '2026-10-16T00:00:00Z' },
};

const transport: Transport = {
  id: 1,
  sourceVillage: { id: 59, name: "Aldric's village", x: 61, y: 58, playerId: 5, playerName: 'aldric' },
  targetVillage: { id: 3, name: 'Tomburg', x: 70, y: 58, playerId: 7, playerName: 'Brita' },
  resources: { wood: 100, clay: 200, iron: 300 },
  merchants: 2,
  departAt: '2026-10-15T20:00:00Z',
  arriveAt: '2026-10-15T21:00:00Z',
  returnAt: '2026-10-15T22:00:00Z',
  arrived: false,
};

function open(shown: Market = market, language: 'en' | 'de' = 'en', url = `/v/${village.id}/market`) {
  return renderPage({
    path: '/v/:villageId/market',
    url,
    element: <MarketPage />,
    data: [
      [meQueryKey, me],
      [villageQueryKey(village.id), village],
      [marketQueryKey(village.id), shown],
      [worldQueryKey, world],
    ],
    language,
  });
}

/** The market (05-frontend §3): the merchants on top, five tabs, and what is left without a market. */
describe('MarketPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the village and its free merchants above the five tabs', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Market' })).toBeTruthy();
    expect(screen.getByText(/Merchants: 3 of 5/)).toBeTruthy();
    const tabs = screen.getByRole('group', { name: 'Market sections' });
    expect(
      within(tabs)
        .getAllByRole('button')
        .map((tab) => tab.textContent),
    ).toEqual(['Send', 'Offers', 'Own', 'Exchange', 'Merchants']);
    expect(within(tabs).getByRole('button', { name: 'Send' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('hides the exchange tab where the world turned it off', () => {
    open({ ...market, exchange: { ...market.exchange, ratio: 0 } });

    expect(screen.queryByRole('button', { name: 'Exchange' })).toBeNull();
  });

  it('lists the merchants on their way under the Merchants tab', () => {
    open({ ...market, outgoing: [transport] });

    fireEvent.click(screen.getByRole('button', { name: 'Merchants' }));

    expect(screen.getByRole('button', { name: 'Merchants' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('heading', { name: 'Merchants sent' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Merchants coming here' })).toBeTruthy();
    expect(screen.getByText(/Tomburg/)).toBeTruthy();
  });

  it('says a village without a market has no merchants, and still shows what is on the way', () => {
    open({
      ...market,
      merchants: { total: 0, free: 0 },
      incoming: [
        { ...transport, id: 2, sourceVillage: transport.targetVillage, targetVillage: transport.sourceVillage },
      ],
    });

    expect(screen.getByText('This village has no market yet, so it has no merchants.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Build one in the Town Hall' }).getAttribute('href')).toBe(
      `/v/${village.id}/hq#MARKET`,
    );
    expect(screen.queryByRole('group', { name: 'Market sections' })).toBeNull();
    expect(screen.getByText(/Tomburg/)).toBeTruthy();
  });

  it('is shown in German too', () => {
    open(market, 'de');

    expect(screen.getByRole('heading', { level: 1, name: 'Marktplatz' })).toBeTruthy();
  });

  it('sends a village id that is not a number home', () => {
    renderPage({ path: '/v/:villageId/market', url: '/v/x/market', element: <MarketPage />, data: [] });

    expect(screen.queryByRole('heading', { level: 1 })).toBeNull();
  });
});

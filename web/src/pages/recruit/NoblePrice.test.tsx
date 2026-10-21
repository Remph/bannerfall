// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { noble } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { NoblePriceBody } from './NoblePrice';

function open(data: typeof noble) {
  return renderPage({ path: '/', url: '/', element: <NoblePriceBody noble={data} />, data: [] });
}

describe('NoblePriceBody', () => {
  afterEach(cleanup);

  it('shows the next price, the rank behind it and the one after it', () => {
    open(noble);

    expect(screen.getByText('×1.25')).toBeTruthy();
    expect(screen.getByText('2 villages + 3 nobles, 4 free → 1 × 25% = ×1.25')).toBeTruthy();
    expect(screen.getByText('The one after it')).toBeTruthy();
    expect(screen.getByRole('list', { name: 'Your next noble' }).textContent).toContain('62,500');
    expect(screen.getByRole('list', { name: 'The one after it' }).textContent).toContain('75,000');
    expect(
      screen.getByText(/your first 4 nobles cost the base price.*25% of the base price dearer, up to 7 times/),
    ).toBeTruthy();
  });

  it('says when the price is at its highest', () => {
    open({ ...noble, nobles: 30, rank: 28, factor: 7, cost: { wood: 280_000, clay: 350_000, iron: 350_000 } });

    expect(screen.getByText('This is the highest price: it rises no further.')).toBeTruthy();
    expect(screen.getByText('2 villages + 30 nobles, 4 free → 28 × 25%, at most ×7')).toBeTruthy();
    expect(screen.queryByText('The one after it')).toBeNull();
  });

  it('says so in a world with the flat price', () => {
    open({ ...noble, step: 0, factor: 1, cost: noble.baseCost, costAfter: noble.baseCost });

    expect(screen.getByText('In this world every noble costs the same.')).toBeTruthy();
    expect(screen.queryByText(/free →/)).toBeNull();
  });
});

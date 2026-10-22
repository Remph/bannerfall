// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { troopsQueryKey, type Troops } from '../../api/queries/troops';
import type { Village } from '../../api/queries/villages';
import { village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { TroopsAtHome } from './VillagePage';

const stack = (stackId: number, units: Troops['supportsHere'][number]['units']): Troops['supportsHere'][number] => ({
  stackId,
  homeVillage: { id: 900 + stackId, name: `Dorf ${stackId}`, x: 50, y: 61, playerId: 5, playerName: 'Ben' },
  units,
});

function show(unitsHome: Village['unitsHome'], supportsHere: Troops['supportsHere']) {
  const troops: Troops = { home: unitsHome, supportsHere, ownUnitsElsewhere: [], outgoing: [], incoming: [] };
  renderPage({
    path: '/',
    url: '/',
    element: <TroopsAtHome village={{ ...village, unitsHome }} />,
    data: [[troopsQueryKey(village.id), troops]],
  });
}

describe('TroopsAtHome', () => {
  afterEach(cleanup);

  it('adds the support of other villages as one separate total under the own troops', () => {
    show({ SPEARMAN: 100 }, [stack(1, { SPEARMAN: 30, AXEMAN: 5 }), stack(2, { SPEARMAN: 12 })]);

    expect(screen.getByRole('heading', { name: 'Support from other villages' })).toBeTruthy();
    const rows = screen
      .getAllByRole('term')
      .map((term) => `${term.textContent}: ${term.nextElementSibling?.textContent}`);
    // the own spearmen first, then the support summed over both villages
    expect(rows).toEqual(['Spearman: 100', 'Spearman: 42', 'Axeman: 5']);
    expect(screen.queryByText('Dorf 1')).toBeNull();
  });

  it('shows no support part without support', () => {
    show({ SPEARMAN: 100 }, []);

    expect(screen.queryByRole('heading', { name: 'Support from other villages' })).toBeNull();
  });

  it('says there are no troops only when there is no support either', () => {
    show({}, []);
    expect(screen.getByText('No troops at home yet.')).toBeTruthy();
    cleanup();

    show({}, [stack(1, { SPEARMAN: 7 })]);
    expect(screen.queryByText('No troops at home yet.')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Support from other villages' })).toBeTruthy();
  });
});

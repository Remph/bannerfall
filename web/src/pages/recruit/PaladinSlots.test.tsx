// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { paladinQueryKey } from '../../api/queries/paladin';
import { paladin } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { PaladinSlots } from './PaladinSlots';

function open(data: typeof paladin) {
  return renderPage({ path: '/', url: '/', element: <PaladinSlots />, data: [[paladinQueryKey, data]] });
}

describe('PaladinSlots', () => {
  afterEach(cleanup);

  it('counts the slots in use and says when the next one opens', () => {
    open(paladin);

    expect(screen.getByText('0 of 1 slot in use')).toBeTruthy();
    expect(screen.getByText('Next slot at 5 villages (you own 1).')).toBeTruthy();
  });

  it('says when the world allows no more slots', () => {
    open({ ...paladin, villages: 20, slots: 5, used: 3, nextSlotAtVillages: null });

    expect(screen.getByText('3 of 5 slots in use')).toBeTruthy();
    expect(screen.getByText('This is the most slots the world allows.')).toBeTruthy();
  });
});

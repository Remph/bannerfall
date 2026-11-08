// @vitest-environment jsdom
import { cleanup } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { tutorialQueryKey, type Tutorial } from '../api/queries/tutorial';
import { tutorial } from '../testing/fixtures';
import { renderPage } from '../testing/renderPage';
import { useTutorialSync } from './useTutorialSync';

const showToast = vi.hoisted(() => vi.fn());
vi.mock('../components/toasts', () => ({ useShowToast: () => showToast }));

function Page() {
  // 0: no village data yet, so nothing is fetched in the test
  useTutorialSync(0);
  return <p>page</p>;
}

function open(shown: Tutorial) {
  return renderPage({ path: '/x', url: '/x', element: <Page />, data: [[tutorialQueryKey, shown]] });
}

describe('useTutorialSync', () => {
  afterEach(() => {
    cleanup();
    showToast.mockReset();
  });

  it('tells about a refund the server just paid', () => {
    open({
      ...tutorial,
      refunds: [{ key: 'WOODCUTTER', villageId: 7, amount: { wood: 63, clay: 75, iron: 50 } }],
    });

    expect(showToast).toHaveBeenCalledTimes(1);
    expect(showToast).toHaveBeenCalledWith(
      expect.objectContaining({ text: 'Tutorial refund: 63 Wood, 75 Clay, 50 Iron', to: { pathname: '/v/7' } }),
    );
  });

  it('adds several refunds up into one toast', () => {
    open({
      ...tutorial,
      refunds: [
        { key: 'WOODCUTTER', villageId: 7, amount: { wood: 63, clay: 75, iron: 50 } },
        { key: 'HEADQUARTERS', villageId: 7, amount: { wood: 100, clay: 0, iron: 20 } },
      ],
    });

    expect(showToast).toHaveBeenCalledTimes(1);
    expect(showToast).toHaveBeenCalledWith(
      expect.objectContaining({ text: 'Tutorial refund: 163 Wood, 75 Clay, 70 Iron', to: { pathname: '/v/7' } }),
    );
  });

  it('says the refund was lost when a full warehouse took nothing', () => {
    open({
      ...tutorial,
      refunds: [
        { key: 'WOODCUTTER', villageId: 7, amount: { wood: 0, clay: 0, iron: 0 } },
        { key: 'BARRACKS', villageId: 7, amount: { wood: 0, clay: 0, iron: 0 } },
      ],
    });

    expect(showToast).toHaveBeenCalledTimes(1);
    expect(showToast).toHaveBeenCalledWith({ tone: 'info', text: 'Tutorial refund lost: the warehouse is full.' });
  });

  it('says nothing when nothing was paid', () => {
    open({ ...tutorial, refunds: [] });

    expect(showToast).not.toHaveBeenCalled();
  });
});

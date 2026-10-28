// @vitest-environment jsdom
import { cleanup, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { tutorialQueryKey, type Tutorial } from '../api/queries/tutorial';
import { tutorial } from '../testing/fixtures';
import { renderPage } from '../testing/renderPage';
import { TutorialHint } from './TutorialHint';
import { useTutorialAck } from './useTutorialAck';

function Page({ steps }: { steps: Parameters<typeof TutorialHint>[0]['steps'] }) {
  return <TutorialHint steps={steps} />;
}

function AckPage({ happened }: { happened: boolean }) {
  useTutorialAck('WOODCUTTER', happened);
  return <p>page</p>;
}

function open(element: React.ReactElement, shown: Tutorial = tutorial) {
  return renderPage({ path: '/x', url: '/x', element, data: [[tutorialQueryKey, shown]] });
}

describe('TutorialHint', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('shows the current step on a page it is about', () => {
    open(<Page steps={['WOODCUTTER', 'HEADQUARTERS']} />);

    expect(screen.getByText('Raise a woodcutter.')).toBeTruthy();
    expect(screen.getByText(/Upgrade a woodcutter to level 2/)).toBeTruthy();
  });

  it('shows nothing on any other page, for a lesson, or without a tutorial', () => {
    const { unmount } = open(<Page steps={['ATTACK']} />);
    expect(screen.queryByText('Raise a woodcutter.')).toBeNull();
    unmount();

    open(<Page steps={['LESSON_CONQUEST']} />, {
      ...tutorial,
      current: { key: 'LESSON_CONQUEST', kind: 'LESSON', index: 0, total: 0, refund: null },
    });
    expect(screen.queryByText('Conquering villages.')).toBeNull();
    cleanup();

    open(<Page steps={['WOODCUTTER']} />, { ...tutorial, active: false, current: null, steps: [] });
    expect(screen.queryByText('Raise a woodcutter.')).toBeNull();
  });
});

describe('useTutorialAck', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('tells the server once when the current step happened', async () => {
    const post = vi.spyOn(api, 'POST').mockResolvedValue({
      data: { ...tutorial, current: null },
      response: new Response(),
    } as never);
    open(<AckPage happened />);

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post).toHaveBeenCalledWith('/api/tutorial/steps/{key}/done', {
      params: { path: { key: 'WOODCUTTER' } },
    });
  });

  it('says nothing while it has not happened, or when another step is the current one', async () => {
    const post = vi.spyOn(api, 'POST').mockResolvedValue({ data: tutorial, response: new Response() } as never);
    const { unmount } = open(<AckPage happened={false} />);
    unmount();
    open(<AckPage happened />, { ...tutorial, current: { ...tutorial.current!, key: 'ATTACK' } });

    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(post).not.toHaveBeenCalled();
  });
});

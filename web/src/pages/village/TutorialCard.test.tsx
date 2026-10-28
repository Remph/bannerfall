// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { meQueryKey } from '../../api/queries/auth';
import { recruitmentQueryKey } from '../../api/queries/recruitment';
import { troopsQueryKey, type Troops } from '../../api/queries/troops';
import { tutorialQueryKey, type Tutorial } from '../../api/queries/tutorial';
import { villageQueryKey } from '../../api/queries/villages';
import { FIXTURE_NOW, me, tutorial, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { VillagePage } from './VillagePage';

const noTroops: Troops = { home: {}, supportsHere: [], ownUnitsElsewhere: [], outgoing: [], incoming: [] };

function open(shown: Tutorial, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId',
    url: `/v/${village.id}`,
    element: <VillagePage />,
    data: [
      [meQueryKey, me],
      [villageQueryKey(village.id), village],
      [troopsQueryKey(village.id), noTroops],
      [recruitmentQueryKey(village.id), []],
      [tutorialQueryKey, shown],
    ],
    language,
  });
}

const lesson: Tutorial = {
  ...tutorial,
  current: { key: 'LESSON_CONQUEST', kind: 'LESSON', index: 0, total: 0, refund: null },
};

function mockPost(answer: Tutorial) {
  return vi.spyOn(api, 'POST').mockResolvedValue({ data: answer, response: new Response() } as never);
}

describe('the tutorial card on the village page', () => {
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
    vi.restoreAllMocks();
  });

  it('shows the current step with its place, text, refund and one lead button', () => {
    open(tutorial);

    expect(screen.getByRole('heading', { name: 'Raise a woodcutter' })).toBeTruthy();
    expect(screen.getByText('Step 1 of 11')).toBeTruthy();
    expect(screen.getByText(/Upgrade a woodcutter to level 2/)).toBeTruthy();
    expect(screen.getByText('Back when done: 63 Wood, 75 Clay, 50 Iron')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Show me' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Got it' })).toBeNull();
  });

  it('has no refund line for a step without a refund', () => {
    open({ ...tutorial, current: { ...tutorial.current!, key: 'ATTACK', index: 6, refund: null } });

    expect(screen.getByRole('heading', { name: 'Send your first attack' })).toBeTruthy();
    expect(screen.queryByText(/Back when done/)).toBeNull();
  });

  it('is in German when the game is', () => {
    open(tutorial, 'de');

    expect(screen.getByRole('heading', { name: 'Holzfäller ausbauen' })).toBeTruthy();
    expect(screen.getByText('Schritt 1 von 11')).toBeTruthy();
    expect(screen.getByText('Gibt es zurück: 63 Holz, 75 Lehm, 50 Eisen')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Zeig’s mir' })).toBeTruthy();
  });

  it('leaves the village page for the page of the step with "Show me"', () => {
    open(tutorial);

    fireEvent.click(screen.getByRole('button', { name: 'Show me' }));

    // the test's router only knows the village page: it is gone
    expect(screen.queryByRole('heading', { name: 'Raise a woodcutter' })).toBeNull();
  });

  it('is not there for a player without the tutorial, in a world without it, or with nothing left', () => {
    for (const hidden of [
      { ...tutorial, active: false, current: null, steps: [] },
      { ...tutorial, enabled: false, active: false, current: null, steps: [] },
      { ...tutorial, current: null },
    ]) {
      const { unmount } = open(hidden);
      expect(screen.getByRole('heading', { level: 1, name: village.name })).toBeTruthy();
      expect(screen.queryByText('Show me')).toBeNull();
      expect(screen.queryByText('Step 1 of 11')).toBeNull();
      unmount();
    }
  });

  it('shows a lesson as a tip with "Got it" as the lead and "Show me" next to it', async () => {
    const post = mockPost({ ...tutorial, current: null });
    open(lesson);

    expect(screen.getByText('Good to know')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Conquering villages' })).toBeTruthy();
    expect(screen.queryByText(/Step \d+ of/)).toBeNull();
    expect(screen.getByRole('button', { name: 'Show me' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Got it' }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post.mock.calls[0]).toEqual([
      '/api/tutorial/steps/{key}/done',
      { params: { path: { key: 'LESSON_CONQUEST' } } },
    ]);
    // the answer is the new tutorial: the card is gone
    await waitFor(() => expect(screen.queryByText('Good to know')).toBeNull());
  });

  it('skips a step from the menu, or the whole tutorial', async () => {
    const post = mockPost({ ...tutorial, current: null });
    open(tutorial);

    fireEvent.click(screen.getByRole('button', { name: 'More for the tutorial' }));
    fireEvent.click(screen.getByRole('button', { name: 'Skip this step' }));
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0]).toEqual(['/api/tutorial/steps/{key}/skip', { params: { path: { key: 'WOODCUTTER' } } }]);
  });

  it('stops the tutorial from the menu', async () => {
    const post = mockPost({ ...tutorial, active: false, current: null, steps: [] });
    open(tutorial);

    fireEvent.click(screen.getByRole('button', { name: 'More for the tutorial' }));
    fireEvent.click(screen.getByRole('button', { name: 'Skip tutorial' }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/tutorial/stop'));
    await waitFor(() => expect(screen.queryByText('Step 1 of 11')).toBeNull());
  });

  it('lists every step with where it stands in the "All steps" sheet', () => {
    open({
      ...tutorial,
      steps: tutorial.steps.map((line, i) => (i === 0 ? line : i === 1 ? { ...line, status: 'DONE' } : line)),
    });

    fireEvent.click(screen.getByRole('button', { name: 'More for the tutorial' }));
    fireEvent.click(screen.getByRole('button', { name: 'All steps' }));

    expect(screen.getByRole('heading', { name: 'Steps' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Good to know' })).toBeTruthy();
    expect(screen.getByText('Raise the Town Hall').closest('li')?.getAttribute('data-status')).toBe('DONE');
    expect(screen.getAllByText('Next')).toHaveLength(1);
    expect(screen.getByText('Village automation')).toBeTruthy();
  });

  it('marks only the step the card shows as "Next", other open steps say "Open"', () => {
    open({
      ...tutorial,
      steps: tutorial.steps.map((line) => (line.key === 'GUILD' ? { ...line, status: 'OPEN' } : line)),
    });

    fireEvent.click(screen.getByRole('button', { name: 'More for the tutorial' }));
    fireEvent.click(screen.getByRole('button', { name: 'All steps' }));

    expect(screen.getAllByText('Next')).toHaveLength(1);
    const rows = Array.from(document.querySelectorAll('li[data-status]'));
    const row = (name: string) => rows.find((li) => li.textContent?.includes(name));
    expect(row('Raise a woodcutter')?.textContent).toContain('Next');
    expect(row('Join or found a guild')?.textContent).toContain('Open');
  });
});

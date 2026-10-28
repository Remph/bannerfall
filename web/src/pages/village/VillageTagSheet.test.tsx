// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { recruitmentQueryKey } from '../../api/queries/recruitment';
import { troopsQueryKey, type Troops } from '../../api/queries/troops';
import { villageQueryKey } from '../../api/queries/villages';
import { FIXTURE_NOW, me, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { VillagePage } from './VillagePage';

const noTroops: Troops = { home: {}, supportsHere: [], ownUnitsElsewhere: [], outgoing: [], incoming: [] };

function open(tag: string | null) {
  const tagged: Me = { ...me, villages: me.villages.map((own) => ({ ...own, tag })) };
  return renderPage({
    path: '/v/:villageId',
    url: `/v/${village.id}`,
    element: <VillagePage />,
    data: [
      [meQueryKey, tagged],
      [villageQueryKey(village.id), village],
      [troopsQueryKey(village.id), noTroops],
      [recruitmentQueryKey(village.id), []],
    ],
  });
}

function mockPut(tag: string | null) {
  return vi.spyOn(api, 'PUT').mockResolvedValue({
    data: { villageId: village.id, tag },
    response: new Response(),
  } as never);
}

describe('the village tag on the village page', () => {
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

  it('shows the tag as a pill next to the name', () => {
    open('OFF');

    expect(screen.getByText('OFF')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Tag OFF. Change it' })).toBeTruthy();
  });

  it('sets a tag in capitals, only letters and digits, and reloads /api/me', async () => {
    const put = mockPut('OFF');
    const { queryClient } = open(null);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    fireEvent.click(screen.getByRole('button', { name: 'Add a tag' }));
    expect(screen.getByRole('heading', { name: `Tag · ${village.name}` })).toBeTruthy();
    expect(screen.getByText(/Only you can see it/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Remove' })).toBeNull();
    const save = screen.getByRole('button', { name: 'Save' }) as HTMLButtonElement;
    expect(save.disabled).toBe(true); // nothing to save yet

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'o-ff!x2y' } });
    expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('OFFX2');
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'off' } });
    fireEvent.click(save);

    await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: meQueryKey }));
    expect(put.mock.calls[0]?.[1]).toMatchObject({ params: { path: { id: village.id } }, body: { tag: 'OFF' } });
  });

  it('changes and removes a tag', async () => {
    const put = mockPut(null);
    open('DEF');

    fireEvent.click(screen.getByRole('button', { name: 'Tag DEF. Change it' }));
    expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('DEF');
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));

    await waitFor(() => expect(put).toHaveBeenCalled());
    expect(put.mock.calls[0]?.[1]).toMatchObject({ body: { tag: '' } });
  });
});

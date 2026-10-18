// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { farmQueryKey } from '../../api/queries/farm';
import { farmTemplatesQueryKey } from '../../api/queries/farmTemplates';
import { mapChunkQueryKey, type MapVillage } from '../../api/queries/map';
import { village as home } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { NoteBox, NoteSheet } from './VillageNote';
import { VillagePanel } from './VillagePanel';

const barbarian: MapVillage = {
  id: 90,
  x: 63,
  y: 57,
  name: null,
  playerId: null,
  playerName: null,
  npc: false,
  points: 26,
  loyalty: null,
  loyaltySeenAt: null,
  guildId: null,
  guildTag: null,
  nextBannerRollAt: null,
  keep: false,
  note: null,
};

const chunk = mapChunkQueryKey({ cx: 3, cy: 2 });

function open(village: MapVillage, onClose = vi.fn()) {
  const page = renderPage({
    path: '/',
    url: '/',
    element: <NoteSheet open village={village} onClose={onClose} />,
    data: [[chunk, [village]]],
  });
  return { ...page, onClose };
}

function mockPut(text: string | null) {
  return vi.spyOn(api, 'PUT').mockResolvedValue({
    data: { villageId: barbarian.id, text },
    response: new Response(),
  } as never);
}

beforeAll(() => {
  // jsdom has no modal dialogs
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.open = true;
  };
});

describe('NoteSheet', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('saves a new note, puts it on the map at once and closes', async () => {
    const put = mockPut('Wall 3, spears only');
    const { queryClient, onClose } = open(barbarian);

    expect(screen.getByRole('heading', { name: 'Note · Barbarian village (63|57)' })).toBeTruthy();
    expect(screen.getByText('Only you can see this note.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
    const save = screen.getByRole('button', { name: 'Save' }) as HTMLButtonElement;
    expect(save.disabled).toBe(true); // nothing to save yet

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Wall 3, spears only' } });
    expect(screen.getByText('19 / 500')).toBeTruthy();
    fireEvent.click(save);

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(put.mock.calls[0]?.[1]).toMatchObject({
      params: { path: { id: barbarian.id } },
      body: { text: 'Wall 3, spears only' },
    });
    expect(queryClient.getQueryData<MapVillage[]>(chunk)?.[0]?.note).toBe('Wall 3, spears only');
  });

  it('deletes a note', async () => {
    const put = mockPut(null);
    const { queryClient, onClose } = open({ ...barbarian, note: 'old' });

    expect((screen.getByRole('textbox') as HTMLTextAreaElement).value).toBe('old');
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(put.mock.calls[0]?.[1]).toMatchObject({ body: { text: '' } });
    expect(queryClient.getQueryData<MapVillage[]>(chunk)?.[0]?.note).toBeNull();
  });

  it('counts emoji as one character and holds back a note that is too long', () => {
    const put = mockPut('x');
    open(barbarian);

    fireEvent.change(screen.getByRole('textbox'), { target: { value: '🏰'.repeat(500) } });
    expect(screen.getByText('500 / 500')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Save' }) as HTMLButtonElement).disabled).toBe(false);

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'a'.repeat(501) } });
    expect(screen.getByText('501 / 500')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(put).not.toHaveBeenCalled();
  });
});

describe('NoteBox', () => {
  afterEach(cleanup);

  it('shows the note and opens the editor on a tap', () => {
    const onOpen = vi.fn();
    renderPage({ path: '/', url: '/', element: <NoteBox note="Wall 3" onOpen={onOpen} />, data: [] });

    fireEvent.click(screen.getByRole('button', { name: 'Your note: Wall 3. Change it' }));
    expect(onOpen).toHaveBeenCalledTimes(1);
  });
});

describe('VillagePanel with notes', () => {
  beforeAll(() => {
    // jsdom has no ResizeObserver; the panel reports the part of the map it covers with one
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
  });

  afterEach(cleanup);

  const me: Me = {
    accountId: 1,
    username: 'ada',
    role: 'PLAYER',
    playerId: 7,
    points: 1200,
    protectedUntil: '2026-10-12T00:00:00Z',
    needsRespawn: false,
    villages: [],
  };

  function panel(village: MapVillage, onClose = vi.fn()) {
    renderPage({
      path: '/',
      url: '/',
      element: (
        <VillagePanel village={village} kind="barbarian" currentVillage={home} onClose={onClose} onCover={vi.fn()} />
      ),
      data: [
        [meQueryKey, me],
        [farmQueryKey(home.id), []],
        [farmTemplatesQueryKey, []],
      ],
    });
    return onClose;
  }

  it('has only a pencil in its head without a note, and the box with one', () => {
    panel(barbarian);
    expect(screen.getByRole('button', { name: 'Write a note' })).toBeTruthy();
    cleanup();

    panel({ ...barbarian, note: 'Wall 3' });
    expect(screen.queryByRole('button', { name: 'Write a note' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Your note: Wall 3. Change it' })).toBeTruthy();
  });

  it('Escape in the note sheet leaves the panel open, elsewhere it closes the panel', () => {
    const onClose = panel(barbarian);
    fireEvent.click(screen.getByRole('button', { name: 'Write a note' }));

    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.keyDown(screen.getByRole('button', { name: 'Write a note' }), { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

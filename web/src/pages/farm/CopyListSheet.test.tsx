// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { renderPage } from '../../testing/renderPage';
import { CopyListSheet } from './CopyListSheet';

// the request itself is the typed client's; here only what the sheet asks for and shows
const copy = vi.hoisted(() => vi.fn());
const copyResult = vi.hoisted(() => ({
  current: undefined as { copied: number; alreadyThere: number; ownVillage: number; listFull: number } | undefined,
}));
vi.mock('../../api/queries/farm', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/queries/farm')>()),
  useCopyFarmList: () => ({
    mutate: copy,
    data: copyResult.current,
    variables: copyResult.current === undefined ? undefined : THERE,
    isPending: false,
    isError: false,
    error: null,
    reset: () => {},
  }),
}));

const HERE = 9;
const THERE = 3;
const COPIED = 4;
const FULL = 2;

const me: Me = {
  accountId: 1,
  username: 'ben',
  role: 'PLAYER',
  playerId: 7,
  points: 1200,
  protectedUntil: '2026-10-12T00:00:00Z',
  needsRespawn: false,
  linked: false,
  respawnNearGuild: false,
  villages: [
    { id: HERE, name: 'Dorf von Ben', x: 50, y: 60, points: 300, incomingAttacks: 0 },
    { id: THERE, name: 'Zweitdorf', x: 52, y: 61, points: 120, incomingAttacks: 0 },
  ],
};

function open() {
  renderPage({
    path: '/',
    url: '/',
    element: <CopyListSheet open onClose={() => {}} villageId={HERE} />,
    data: [[meQueryKey, me]],
  });
}

describe('CopyListSheet', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });

  afterEach(() => {
    cleanup();
    copy.mockClear();
    copyResult.current = undefined;
  });

  it('offers the other villages only', () => {
    open();

    expect(screen.getByRole('button', { name: /Zweitdorf/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Dorf von Ben/ })).toBeNull();
  });

  it('copies to the village that is tapped', () => {
    open();

    fireEvent.click(screen.getByRole('button', { name: /Zweitdorf/ }));

    expect(copy).toHaveBeenCalledWith(THERE);
  });

  it('says what was copied and what was passed over', () => {
    copyResult.current = { copied: COPIED, alreadyThere: 1, ownVillage: 0, listFull: FULL };
    open();

    expect(screen.getByRole('status').textContent).toBe(
      'Zweitdorf: 4 entries copied, 1 there already, 2 did not fit (at most 50).',
    );
  });
});

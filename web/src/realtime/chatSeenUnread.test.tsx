// @vitest-environment jsdom
import { cleanup, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey } from '../api/queries/auth';
import { chatQueryKey, type ChatSeen } from '../api/queries/chat';
import { guildChatQueryKey } from '../api/queries/chat';
import { myGuildQueryKey } from '../api/queries/guilds';
import { worldQueryKey } from '../api/queries/world';
import { me, world } from '../testing/fixtures';
import { renderPage } from '../testing/renderPage';
import { markChatSeen, storageKey, useChannelUnread } from './chatSeen';

const answers = vi.hoisted(() => ({ markers: [] as ChatSeen[], puts: [] as [number | undefined, number][] }));

vi.mock('../api/queries/chat', async (original) => ({
  ...(await original<typeof import('../api/queries/chat')>()),
  fetchChatSeen: () => Promise.resolve(answers.markers),
  putChatSeen: (guildId: number | undefined, id: number) => {
    answers.puts.push([guildId, id]);
    return Promise.resolve({ guildId: guildId ?? null, id });
  },
}));

const messages = [11, 12, 13].map((id) => ({
  id,
  playerId: 99,
  playerName: 'Other',
  text: `m${id}`,
  createdAt: '2026-10-15T20:00:00Z',
}));

function Badge() {
  return <p data-testid="badge">{useChannelUnread('world')}</p>;
}

function open() {
  return renderPage({
    path: '/',
    url: '/',
    element: <Badge />,
    data: [
      [meQueryKey, me],
      [worldQueryKey, { ...world, guildsEnabled: false }],
      [myGuildQueryKey, { guild: null, invitations: [] }],
      [chatQueryKey, { messages, complete: true }],
      [guildChatQueryKey(0), undefined],
    ],
  });
}

describe('the unread badge with the server’s read marker (1.7)', () => {
  beforeEach(() => {
    localStorage.clear();
    answers.markers = [];
    answers.puts = [];
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('takes the marker the server has from another device, not the first-visit rule', async () => {
    answers.markers = [{ guildId: null, id: 11 }];
    open();

    await waitFor(() => expect(screen.getByTestId('badge').textContent).toBe('2'));
    expect(localStorage.getItem(storageKey(me.playerId))).toBe('11');
  });

  it('counts the whole history as read when neither the device nor the server has a marker', async () => {
    open();

    await waitFor(() => expect(localStorage.getItem(storageKey(me.playerId))).toBe('13'));
    expect(screen.getByTestId('badge').textContent).toBe('0');
  });

  it('keeps a newer marker of this device when the server is behind', async () => {
    answers.markers = [{ guildId: null, id: 11 }];
    markChatSeen(me.playerId, 12);
    open();

    await waitFor(() => expect(screen.getByTestId('badge').textContent).toBe('1'));
    expect(localStorage.getItem(storageKey(me.playerId))).toBe('12');
  });
});

describe('what goes to the server (1.7)', () => {
  beforeEach(() => {
    localStorage.clear();
    answers.puts = [];
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('sends the marker of a chat that was read, once the second is over', () => {
    markChatSeen(7, 5);
    expect(answers.puts).toEqual([]);

    vi.advanceTimersByTime(1000);
    expect(answers.puts).toEqual([[undefined, 5]]);
  });

  it('sends nothing for an empty chat (the server takes markers from 1)', () => {
    markChatSeen(7, 0);

    vi.advanceTimersByTime(1000);
    expect(answers.puts).toEqual([]);
    expect(localStorage.getItem(storageKey(7))).toBe('0');
  });
});

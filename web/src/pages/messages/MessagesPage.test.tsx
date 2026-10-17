// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { blocksQueryKey, messagesQueryKey, type Conversation } from '../../api/queries/messages';
import { renderPage } from '../../testing/renderPage';
import { MessagesPage } from './MessagesPage';

function conversation(playerId: number, playerName: string): Conversation {
  return {
    playerId,
    playerName,
    guildTag: null,
    lastText: `Hello from ${playerName}`,
    lastAt: '2026-10-16T09:12:00Z',
    lastMine: false,
    unread: 0,
  };
}

function open(url: string) {
  return renderPage({
    path: '/messages',
    url,
    element: <MessagesPage />,
    // the conversation itself is ConversationPage's: here only where it goes
    children: [{ path: ':playerId', element: <p>The open conversation</p> }],
    data: [
      [
        [...messagesQueryKey, 'inbox'],
        [conversation(5, 'Tom'), conversation(6, 'Ann')],
      ],
      [blocksQueryKey, []],
    ],
  });
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('MessagesPage on phones', () => {
  it('shows an open conversation as a page of its own', () => {
    open('/messages/5');

    expect(screen.getByText('The open conversation')).toBeTruthy();
    expect(screen.queryByRole('heading', { level: 1, name: 'Messages' })).toBeNull();
  });
});

describe('MessagesPage on desktop', () => {
  beforeEach(() => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: true,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }));
  });

  it('asks to pick a conversation next to the inbox until one is open', () => {
    open('/messages');

    expect(screen.getByRole('heading', { level: 1, name: 'Messages' })).toBeTruthy();
    expect(
      screen.getByText('Pick a conversation on the left, or open a player’s profile to write to them.'),
    ).toBeTruthy();
  });

  it('shows the open conversation next to the inbox and marks its line', () => {
    open('/messages/5');

    expect(screen.getByRole('region', { name: 'Open conversation' }).textContent).toBe('The open conversation');
    const current = screen.getAllByRole('link').filter((link) => link.getAttribute('aria-current') === 'page');
    expect(current.map((link) => link.getAttribute('href'))).toEqual(['/messages/5']);
  });
});

// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { blocksQueryKey, conversationQueryKey } from '../../api/queries/messages';
import { playerQueryKey } from '../../api/queries/ranking';
import { renderPage } from '../../testing/renderPage';
import { ConversationPage } from './ConversationPage';

const BRITA = 8;

function open(blocked: boolean) {
  return renderPage({
    path: '/messages/:playerId',
    url: `/messages/${BRITA}`,
    element: <ConversationPage />,
    data: [
      // the page reads only the name from the profile
      [[...playerQueryKey, BRITA], { id: BRITA, name: 'Brita' }],
      [conversationQueryKey(BRITA), []],
      [blocksQueryKey, blocked ? [{ playerId: BRITA, name: 'Brita' }] : []],
    ],
  });
}

describe('ConversationPage', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    // jsdom does not scroll
    Element.prototype.scrollIntoView = () => {};
  });

  afterEach(cleanup);

  it('keeps "Block" behind "…" next to Profile, with the question first', () => {
    open(false);

    // not as a button of its own under the message field (2.0 UI review)
    expect(screen.queryByRole('button', { name: 'Block' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'More for Brita' }));
    fireEvent.click(screen.getByRole('button', { name: 'Block this player' }));

    expect(screen.getByRole('dialog', { name: 'Block Brita?' })).toBeTruthy();
  });

  it('has no "…" once the player is blocked: the way back is under the messages', () => {
    open(true);

    expect(screen.queryByRole('button', { name: 'More for Brita' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Unblock' })).toBeTruthy();
  });

  it('shows the letters of a lord in the language of the reader, with no field to answer (2.1 S2b)', () => {
    const LORD = 9;
    renderPage({
      path: '/messages/:playerId',
      url: `/messages/${LORD}`,
      element: <ConversationPage />,
      data: [
        [[...playerQueryKey, LORD], { id: LORD, name: 'Adalbert', npc: true }],
        [
          conversationQueryKey(LORD),
          [
            {
              id: 1,
              mine: false,
              text: '',
              createdAt: '2026-11-19T10:00:00Z',
              letter: { kind: 'JOINED', guild: '[IW] Iron Wolves' },
            },
          ],
        ],
        [blocksQueryKey, []],
      ],
      language: 'de',
    });

    expect(screen.getByText(/Von heute an weht mein Banner für \[IW\] Iron Wolves/)).toBeTruthy();
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.getByRole('link', { name: 'Diplomatie' }).getAttribute('href')).toBe('/diplomacy');
  });
});

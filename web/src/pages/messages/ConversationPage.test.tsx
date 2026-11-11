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
});

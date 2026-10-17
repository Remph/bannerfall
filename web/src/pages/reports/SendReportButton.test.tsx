// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { myGuildQueryKey, type MyGuild } from '../../api/queries/guilds';
import { inboxQueryKey, type Conversation } from '../../api/queries/messages';
import { recentRecipientsQueryKey } from '../../api/queries/reports';
import { renderPage } from '../../testing/renderPage';
import { SendReportButton } from './SendReportButton';

function conversation(playerId: number, playerName: string): Conversation {
  return {
    playerId,
    playerName,
    guildTag: null,
    lastText: 'Hello',
    lastAt: '2026-10-17T09:00:00Z',
    lastMine: true,
    unread: 0,
  };
}

const noGuild: MyGuild = { guild: null, role: null, invitations: [] };

describe('SendReportButton', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });

  afterEach(cleanup);

  it('offers the players last sent a report to before those of the inbox (1.3.1)', () => {
    renderPage({
      path: '/reports/3',
      url: '/reports/3',
      element: <SendReportButton reportId={3} title="Attack" onSent={() => {}} />,
      data: [
        [recentRecipientsQueryKey, { names: ['Cleo', 'Ben'] }],
        [inboxQueryKey, [conversation(2, 'Ben'), conversation(4, 'Dora')]],
        [myGuildQueryKey, noGuild],
      ],
    });

    fireEvent.click(screen.getByRole('button', { name: 'Send report: Attack' }));

    expect(screen.getByText('Recently contacted')).toBeTruthy();
    const picks = screen
      .getAllByRole('button')
      .map((button) => button.textContent)
      .filter((text) => text === 'Cleo' || text === 'Ben' || text === 'Dora');
    expect(picks).toEqual(['Cleo', 'Ben', 'Dora']);
  });

  it('shows no picks until the recent recipients are known, so none moves under a tap', () => {
    renderPage({
      path: '/reports/3',
      url: '/reports/3',
      element: <SendReportButton reportId={3} title="Attack" onSent={() => {}} />,
      data: [
        [inboxQueryKey, [conversation(2, 'Ben')]],
        [myGuildQueryKey, noGuild],
      ],
    });

    fireEvent.click(screen.getByRole('button', { name: 'Send report: Attack' }));

    expect(screen.queryByText('Recently contacted')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Ben' })).toBeNull();
  });
});

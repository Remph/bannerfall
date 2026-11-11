// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { useRef, useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { DesktopNav } from '../../layout/navigation';
import { renderPage } from '../../testing/renderPage';
import { ChatDock } from './ChatDock';

// the panel's own data (messages, socket) is not what this tests
vi.mock('./ChatPanel', () => ({ ChatPanel: () => <p>messages</p> }));

const me: Me = {
  accountId: 1,
  username: 'ben',
  role: 'PLAYER',
  playerId: 7,
  points: 300,
  protectedUntil: '2026-10-10T00:00:00Z',
  needsRespawn: false,
  linked: false,
  respawnNearGuild: false,
  villages: [{ id: 9, name: 'Dorf von Ben', x: 48, y: 52, points: 300, incomingAttacks: 0, tag: null }],
};

/** The rail and the dock wired like AppLayout does it. */
function Frame() {
  const [open, setOpen] = useState(true);
  const entry = useRef<HTMLButtonElement>(null);
  return (
    <>
      <DesktopNav villageId={9} chatOpen={open} onToggleChat={() => setOpen(!open)} chatEntryRef={entry} />
      <ChatDock open={open} onOpenChange={setOpen} focusAfterMinimise={entry} />
    </>
  );
}

describe('the chat panel on a PC (2.0 F2)', () => {
  afterEach(cleanup);

  it('hands the keyboard focus to the rail’s Chat entry when it is minimised', () => {
    renderPage({ path: '*', url: '/v/9', element: <Frame />, data: [[meQueryKey, me]] });
    const minimise = screen.getByRole('button', { name: 'Minimise the chat' });
    minimise.focus();
    fireEvent.click(minimise);
    expect(screen.queryByRole('button', { name: 'Minimise the chat' })).toBeNull();
    const entry = screen.getByRole('button', { name: /^Chat/ });
    expect(document.activeElement).toBe(entry);
    expect(entry.getAttribute('aria-expanded')).toBe('false');
  });
});

// @vitest-environment jsdom
import { act, cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { addBroadcast, broadcastsQueryKey, type Broadcast } from '../api/queries/broadcast';
import { renderPage } from '../testing/renderPage';
import { BroadcastPopup } from './BroadcastPopup';

const broadcast = (id: number, text: string): Broadcast => ({ id, text, sentAt: '2026-10-26T08:00:00Z' });

function open(list: Broadcast[], language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/',
    url: '/',
    element: <BroadcastPopup />,
    data: [[broadcastsQueryKey, list]],
    language,
  });
}

describe('the popup with the host’s messages (1.7)', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });

  beforeEach(() => localStorage.clear());
  afterEach(cleanup);

  it('shows nothing without a broadcast', () => {
    open([]);

    expect(screen.queryByText('OK', { exact: true })).toBeNull();
  });

  it('shows the text with one OK button, in German too', () => {
    open([broadcast(1000, 'Update in 5 Minuten')], 'de');

    expect(screen.getByText('Update in 5 Minuten')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Nachricht der Spielleitung', hidden: true })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'OK', hidden: true })).toBeTruthy();
  });

  it('focuses OK instead of the close button, as nothing was clicked to open it', () => {
    open([broadcast(1000, 'Update in 5 minutes')]);

    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'OK', hidden: true }));
  });

  it('puts a second broadcast into the same popup', async () => {
    const { queryClient } = open([broadcast(1000, 'Update in 5 minutes')]);

    act(() => addBroadcast(queryClient, broadcast(2000, 'Update in 1 minute')));

    expect(await screen.findByText('Update in 1 minute')).toBeTruthy();
    expect(screen.getByText('Update in 5 minutes')).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'OK', hidden: true })).toHaveLength(1);
  });

  it('is dismissed with OK and remembered, a newer broadcast opens it again', async () => {
    const { queryClient, unmount } = open([broadcast(1000, 'Update in 5 minutes')]);

    fireEvent.click(screen.getByRole('button', { name: 'OK', hidden: true }));

    expect(localStorage.getItem('bannerfall.broadcastSeen')).toBe('1000');
    act(() => addBroadcast(queryClient, broadcast(2000, 'Update now')));
    expect(await screen.findByText('Update now')).toBeTruthy();
    expect(screen.queryByText('Update in 5 minutes')).toBeNull();

    // a reload within the ten minutes: only what is newer than the dismissed one
    unmount();
    open([broadcast(1000, 'Update in 5 minutes')]);
    await waitFor(() => expect(screen.queryByText('Update in 5 minutes')).toBeNull());
  });
});

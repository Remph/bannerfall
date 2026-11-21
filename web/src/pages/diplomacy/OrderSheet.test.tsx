// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { orderSheetQueryKey, type OrderAlly, type OrderSheet as OrderSheetData } from '../../api/queries/diplomacy';
import { ToastProvider } from '../../components/ToastProvider';
import { renderPage } from '../../testing/renderPage';
import { OrderSheet } from './OrderSheet';

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock('../../api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/client')>();
  return { ...original, api: { ...original.api, POST: post } };
});

const ally = (over: Partial<OrderAlly> & Pick<OrderAlly, 'playerId' | 'name'>): OrderAlly => ({
  trust: 64,
  fields: 13.4,
  attackSeconds: 7830,
  supportSeconds: 9000,
  sendSeconds: 2400,
  order: null,
  ...over,
});

const sheet: OrderSheetData = {
  target: { id: 70, name: 'Wulframsfeste', x: 61, y: 52, playerId: 3, playerName: 'Baron Wulfram' },
  targetGuildTag: 'RC',
  kinds: ['ATTACK', 'HOLD'],
  ownAttackArrivesAt: null,
  allies: [
    ally({ playerId: 11, name: 'Dame Hildegard' }),
    ally({ playerId: 12, name: 'Lord Wilfried', trust: 41, fields: 12.2, attackSeconds: 4865 }),
    ally({
      playerId: 13,
      name: 'Graf Otto',
      attackSeconds: null,
      order: {
        kind: 'HOLD',
        target: { id: 71, name: 'Eichenau', x: 40, y: 40, playerId: 4, playerName: 'Hedwig' },
        state: 'ACTIVE',
        landTogether: false,
        answer: 'ORDER_HOLD',
        givenAt: '2026-11-19T10:00:00Z',
        departAt: null,
        arriveAt: null,
      },
    }),
  ],
};

function open(data: OrderSheetData = sheet, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/map',
    url: '/map',
    element: (
      <ToastProvider>
        <OrderSheet villageId={70} onClose={() => {}} />
      </ToastProvider>
    ),
    data: [[orderSheetQueryKey(70), data]],
    language,
  });
}

/** The order sheet (2.1 S3, 12-solo-and-allies §8). */
describe('OrderSheet', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  afterEach(() => {
    cleanup();
    post.mockReset();
  });

  it('shows the target with its owner and the kinds it takes', () => {
    open();
    expect(screen.getByRole('heading', { name: 'Order your allies' })).toBeTruthy();
    expect(screen.getByText('Baron Wulfram · [RC]')).toBeTruthy();
    const kinds = screen.getByRole('group', { name: 'Kind of order' });
    expect(
      within(kinds)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(['Attack', 'Hold']);
  });

  it('gives each ally its travel time as the biggest figure, and ticks those without an order', () => {
    open();
    const hildegard = screen.getByText('Dame Hildegard').closest('label')!;
    expect(within(hildegard).getByText('2:10:30')).toBeTruthy();
    expect(within(hildegard).getByText('arrives in · 13.4 fields · trust 64')).toBeTruthy();
    expect(within(hildegard).getByRole<HTMLInputElement>('checkbox').checked).toBe(true);
    // an ally with an open order stays unticked, so a new one doesn't replace it unasked
    const otto = screen.getByText('Graf Otto').closest('label')!;
    expect(within(otto).getByRole<HTMLInputElement>('checkbox').checked).toBe(false);
    expect(within(otto).getByText('Now: leave Eichenau (40\u2060|\u206040) alone')).toBeTruthy();
    expect(within(otto).getByText(/^no attackers at home/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Send order to 2 allies' })).toBeTruthy();
  });

  it('turns "land together" off until the player has his own attack on the way', () => {
    open();
    const land = screen.getByRole<HTMLInputElement>('switch');
    expect(land.disabled).toBe(true);
    expect(screen.getByText(/Send your own attack on this village first/)).toBeTruthy();
  });

  it('sends the order to the ticked allies, with "land together"', async () => {
    post.mockResolvedValue({ data: sheet, response: new Response(null, { status: 200 }) });
    open({ ...sheet, ownAttackArrivesAt: '2026-11-19T23:12:00Z' });
    fireEvent.click(screen.getByText('Lord Wilfried'));
    fireEvent.click(screen.getByRole('switch'));
    fireEvent.click(screen.getByRole('button', { name: 'Send order to 1 ally' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/diplomacy/orders', {
        body: { kind: 'ATTACK', villageId: 70, lordIds: [11], landTogether: true },
      }),
    );
  });

  it('shows no travel time and no "land together" for Hold', () => {
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Hold' }));
    expect(screen.queryByRole('switch')).toBeNull();
    expect(screen.queryByText('2:10:30')).toBeNull();
    expect(screen.getByText(/They leave this village alone/)).toBeTruthy();
  });

  it('asks for a tick before it sends', () => {
    open({ ...sheet, allies: [sheet.allies[2]!] });
    const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Tick an ally to send the order' });
    expect(button.disabled).toBe(true);
  });

  it('says how to get an ally when there is none', () => {
    open({ ...sheet, allies: [] });
    expect(screen.getByText(/No ally can take an order here/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Send order/ })).toBeNull();
  });

  it('reads in German too', () => {
    open(sheet, 'de');
    expect(screen.getByRole('heading', { name: 'Befehl an Verbündete' })).toBeTruthy();
    expect(screen.getByText('kommt an in · 13,4 Felder · Vertrauen 64')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Befehl an 2 Verbündete' })).toBeTruthy();
  });
});

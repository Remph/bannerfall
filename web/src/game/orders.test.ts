import { describe, expect, it } from 'vitest';
import type { Order, OrderAlly } from '../api/queries/diplomacy';
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import { orderLine, orderSeconds, preselected } from './orders';

const order = (over: Partial<Order>): Order => ({
  kind: 'ATTACK',
  target: { id: 70, name: 'Wulframsfeste', x: 61, y: 52, playerId: 3, playerName: 'Baron Wulfram' },
  state: 'OPEN',
  landTogether: false,
  answer: null,
  givenAt: '2026-11-19T10:00:00Z',
  departAt: null,
  arriveAt: null,
  ...over,
});

const ally = (id: number, current: Order | null): OrderAlly => ({
  playerId: id,
  name: `Lord ${id}`,
  trust: 50,
  fields: 10,
  attackSeconds: 600,
  supportSeconds: 900,
  sendSeconds: 300,
  order: current,
});

describe('orders (2.1 S3)', () => {
  it('ticks the allies without an open order', () => {
    const ticked = preselected([
      ally(1, null),
      ally(2, order({ state: 'ACTIVE' })),
      ally(3, order({ state: 'REFUSED' })),
    ]);
    expect([...ticked]).toEqual([1, 3]);
  });

  it('picks the travel time of the kind', () => {
    const a = ally(1, null);
    expect(orderSeconds(a, 'ATTACK')).toBe(600);
    expect(orderSeconds(a, 'SUPPORT')).toBe(900);
    expect(orderSeconds(a, 'SEND')).toBe(300);
    expect(orderSeconds(a, 'HOLD')).toBeNull();
  });

  it('says where an order stands', () => {
    const time = () => '23:10:30';
    expect(orderLine(en, order({}), time)).toBe(
      'Order: attack Wulframsfeste (61\u2060|\u206052), answer in the next round.',
    );
    expect(orderLine(en, order({ state: 'PLANNED', departAt: '2026-11-19T23:10:30Z' }), time)).toBe(
      'Order: attack Wulframsfeste (61\u2060|\u206052), marches at 23:10:30.',
    );
    expect(orderLine(en, order({ kind: 'HOLD', state: 'ACTIVE' }), time)).toBe(
      'Order: leave Wulframsfeste (61\u2060|\u206052) alone, carried out.',
    );
    expect(orderLine(de, order({ kind: 'SEND', state: 'REFUSED' }), time)).toBe(
      'Befehl: Rohstoffe nach Wulframsfeste (61\u2060|\u206052), abgelehnt.',
    );
  });
});

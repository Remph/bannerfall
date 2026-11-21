import type { Order, OrderAlly, OrderKind, OrderSheet } from '../api/queries/diplomacy';
import type { Dictionary } from '../i18n/en';
import { formatCoords } from './format';
import { bracketTag } from './guilds';

type Texts = Pick<Dictionary, 'diplomacy'>;
type VillageRef = OrderSheet['target'];

/**
 * The allies ticked when the sheet opens (2.1 S3): every ally without an open order. One that
 * already carries one stays unticked, so a new order never replaces it unasked.
 */
export function preselected(allies: readonly OrderAlly[]): ReadonlySet<number> {
  return new Set(
    allies.filter((ally) => ally.order === null || ally.order.state === 'REFUSED').map((ally) => ally.playerId),
  );
}

/** The ally's travel time for this kind of order in seconds; null if it has nothing at home for it. */
export function orderSeconds(ally: OrderAlly, kind: OrderKind): number | null {
  const byKind: Record<OrderKind, number | null> = {
    ATTACK: ally.attackSeconds,
    SUPPORT: ally.supportSeconds,
    SEND: ally.sendSeconds,
    HOLD: null,
  };
  return byKind[kind];
}

/** "Wulframsfeste (61|52)", or the barbarian village's name. */
export function targetName(t: Texts, village: VillageRef | null): string {
  if (village === null) {
    return '—';
  }
  return `${village.name ?? t.diplomacy.orders.barbarian} ${formatCoords(village.x, village.y)}`;
}

/** The target's owner under its name: "Baron Wulfram · [RC]", "Barbarian village". */
export function ownerLine(t: Texts, sheet: OrderSheet): string {
  const name = sheet.target.playerName;
  if (name === null) {
    return t.diplomacy.orders.barbarian;
  }
  return sheet.targetGuildTag === null ? name : `${name} · ${bracketTag(sheet.targetGuildTag)}`;
}

/** What an order is about: "attack Wulframsfeste (61|52)". */
export function orderWhat(t: Texts, kind: OrderKind, village: string): string {
  return t.diplomacy.orders.what[kind](village);
}

/**
 * The order on an ally's row (diplomacy page): "Order: attack Wulframsfeste (61|52), it marches at
 * Tue 19 Nov, 23:11:30." `formatTime` writes a planned march's departure.
 */
export function orderLine(t: Texts, order: Order, formatTime: (ms: number) => string): string {
  const labels = t.diplomacy.orders;
  const states: Record<Order['state'], () => string> = {
    OPEN: () => labels.state.OPEN,
    SCOUTING: () => labels.state.SCOUTING,
    PLANNED: () => labels.state.PLANNED(order.departAt === null ? '—' : formatTime(Date.parse(order.departAt))),
    ACTIVE: () => labels.state.ACTIVE,
    REFUSED: () => labels.state.REFUSED,
  };
  return labels.line(orderWhat(t, order.kind, targetName(t, order.target)), states[order.state]());
}

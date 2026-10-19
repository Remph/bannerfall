import type { components } from '../api/schema';

type Schemas = components['schemas'];

/** The switches of the Notifications page (1.5): `PushKind` on the server. */
export type PushKind = Schemas['PushKindDto']['kind'];

/** The live notifications a push can carry (the server's `PushNotifier` picks them). */
export type PushEvent =
  | Schemas['IncomingAttack']
  | Schemas['VillageLost']
  | Schemas['MessageReceived']
  | Schemas['GuildInvited']
  | Schemas['WorldEnded']
  | Schemas['VictoryClock']
  | Schemas['BuildComplete'];

/**
 * What one push carries (the server's `PushMessage`, 01-architecture §4.8b): the live notification
 * behind it, and what the lock screen needs on top of it. The service worker writes the text.
 */
export interface PushMessage {
  /** The device's game language when it signed up. */
  lang: string;
  /** The world's name: the notification's title. */
  world: string;
  /** Missing for a test. */
  event?: PushEvent;
  /** The village it is about, with its owner now: the attacked, lost or finished one. */
  village?: Schemas['VillageRefDto'];
  /** Attacks on the way to `village`, this one included. */
  attacks?: number;
  /** When the first of them arrives (ISO). */
  firstArrival?: string;
  /** A test the player sent from the Notifications page. */
  test?: boolean;
}

/** The parsed payload of a push, or undefined for anything that isn't one of ours. */
export function parsePushMessage(data: unknown): PushMessage | undefined {
  if (typeof data !== 'object' || data === null) {
    return undefined;
  }
  const message = data as Partial<PushMessage>;
  if (typeof message.lang !== 'string' || typeof message.world !== 'string') {
    return undefined;
  }
  return message.test === true || (typeof message.event === 'object' && message.event !== null)
    ? (message as PushMessage)
    : undefined;
}

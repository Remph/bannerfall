/**
 * The texts a push notification needs (1.5), kept apart from the rest of the dictionary: the service
 * worker (`/sw.js`) imports only this file, so it doesn't carry the other 3,000 lines of both
 * languages. `en.ts` and `de.ts` take these sections from here, so the app and the worker read the
 * same words.
 */
import type { BuildingType } from '../game/gameData';
import type { PushKind } from '../push/pushMessage';

export const enPushTexts = {
  /**
   * 1.5: push notifications. The Notifications page, and the notifications themselves (the service
   * worker writes them; the realtime texts below are reused where they fit).
   */
  push: {
    link: 'Notifications',
    title: 'Notifications',
    back: 'Back to the game',
    intro: 'Get a notification on this device while the game is closed, for example as soon as an attack is sent.',
    deviceTitle: 'This device',
    deviceOn: 'Notifications are on for this device.',
    deviceOff: 'Notifications are off for this device.',
    turnOn: 'Turn on',
    turnOff: 'Turn off',
    working: 'One moment…',
    test: 'Send a test',
    testSent: 'Test sent. It should arrive within a few seconds.',
    devices: (count: number) =>
      count === 1 ? 'Notifications reach 1 of your devices.' : `Notifications reach ${count} of your devices.`,
    blocked:
      'Notifications are blocked for this game in your browser. Allow them in the browser’s settings for this site, then try again.',
    unsupported: 'This browser can’t show notifications from the game.',
    iosInstall:
      'On an iPhone or iPad, notifications only work from the home screen: tap Share, then “Add to Home Screen”, and open the game from there.',
    unavailable: 'This world doesn’t send notifications.',
    failed: 'Notifications could not be turned on on this device.',
    kindsTitle: 'What to notify',
    kinds: {
      ATTACK: 'Incoming attacks',
      VILLAGE_LOST: 'Lost villages',
      MESSAGE: 'Private messages',
      GUILD_INVITE: 'Guild invitations',
      WORLD_END: 'Victory clock and end of the world',
      BUILDS_DONE: 'Build queue empty',
      ATTACK_RETURNED: 'Returning attacks',
      STORAGE_FULL: 'Warehouse full',
    } satisfies Record<PushKind, string>,
    kindHints: {
      ATTACK: 'As soon as an attack on one of your villages is sent, with its arrival.',
      VILLAGE_LOST: 'When one of your villages is conquered.',
      MESSAGE: 'When another player writes to you.',
      GUILD_INVITE: 'When a guild invites you.',
      WORLD_END: 'When a side starts or stops the victory clock, and when the world ends.',
      BUILDS_DONE: 'When the last building in a village’s build queue is done.',
      ATTACK_RETURNED:
        'When attacks you asked about are home again: one attack, a train or a farm list run. You choose when you send them, or with the bell.',
      STORAGE_FULL: 'When wood, clay or iron reaches the warehouse limit in a village. Once, until it drops again.',
    } satisfies Record<PushKind, string>,
    /** The notifications themselves. */
    testBody: 'Notifications work on this device.',
    attacks: (count: number, target: string, first: string) =>
      `${count} attacks on the way to ${target} – first arrival ${first}`,
    villageLost: (village: string, by: string | null) =>
      by === null ? `${village} was conquered` : `${by} conquered ${village}`,
    buildsDone: (village: string, building: string, level: number) =>
      `${village}: the build queue is empty. ${building} reached level ${level}.`,
    demolishedDone: (village: string, building: string, level: number) =>
      `${village}: the build queue is empty. ${building} was demolished to level ${level}.`,
  },
  /** Live notifications (M9b): toasts, the red counter of incoming attacks, the connection. */
  realtime: {
    yourVillage: 'your village',
    incomingAttack: (target: string, from: string, player: string | null, arrival: string) =>
      player === null
        ? `Attack on ${target} from ${from} – arrives ${arrival}`
        : `${player} attacks ${target} from ${from} – arrives ${arrival}`,
    newReport: (kind: string) => `New report: ${kind}`,
    /** M40: a copy of a report another player sent. */
    reportShared: (player: string, kind: string) => `${player} sent you a report: ${kind}`,
    buildComplete: (village: string, building: string, level: number) =>
      `${village}: ${building} reached level ${level}`,
    /** A demolition is done (1.6). */
    demolished: (village: string, building: string, level: number) =>
      `${village}: ${building} demolished to level ${level}`,
    /** Screen-reader name of the red counter in the top bar. */
    incomingBadge: (count: number) =>
      count === 1 ? '1 attack on the way to your villages' : `${count} attacks on the way to your villages`,
    notifications: 'Notifications',
    dismiss: 'Dismiss',
    worldOpened: 'The world has opened. Your village is ready!',
    worldEnded: (winner: string | null) =>
      winner === null ? 'The world has ended.' : `The world has ended: ${winner} won!`,
    /** v1.3: the victory clock started or passed on (`when` is "Sat 17 Oct, 01:49"), or stopped. */
    victoryClock: (side: string, when: string) =>
      `${side} meets the victory condition: they win on ${when} unless someone stops them`,
    victoryClockYours: (when: string) => `Your guild meets the victory condition: you win on ${when} if you hold on`,
    victoryClockYou: (when: string) => `You meet the victory condition: you win on ${when} if you hold on`,
    victoryClockStopped: (side: string) =>
      `${side} no longer meets the victory condition. The victory clock has stopped.`,
    victoryClockStoppedYours: 'Your guild no longer meets the victory condition. The victory clock has stopped.',
    victoryClockStoppedYou: 'You no longer meet the victory condition. The victory clock has stopped.',
    guildInvited: (guild: string) => `${guild} invited you to join their guild`,
    guildBannerRaised: (level: number) => `Your guild raised its banner to level ${level}`,
    messageReceived: (player: string) => `New message from ${player}`,
    achievementEarned: (name: string, level: string | null) =>
      level === null ? `Achievement earned: ${name}` : `Achievement earned: ${name} (${level})`,
    /** M32b; `banner` is "War banner, tier II". */
    bannerFound: (banner: string, village: string) => `Banner found at ${village}: ${banner}`,
    bannerLost: (banner: string, village: string) => `Banner stolen from ${village}: ${banner}`,
    bannerStolen: (banner: string, village: string) => `Your thieves stole a banner at ${village}: ${banner}`,
    /** 1.6: attacks the player asked about are home; `loot` is "1,240" (resources), null for none. */
    troopsBack: (village: string, from: string, loot: string | null) =>
      loot === null
        ? `Your troops from ${from} are back in ${village}.`
        : `Your troops from ${from} are back in ${village} with ${loot} resources.`,
    nobodyBack: (from: string) => `Nobody came back from ${from}.`,
    /** A train (`from` is its target) or a farm list run (`from` null). */
    attacksBack: (count: number, village: string, from: string | null, loot: string | null, lost: number) => {
      const attacks = count === 1 ? '1 attack' : `${count} attacks`;
      if (lost === count) {
        return from === null
          ? `Farm run from ${village}: nobody came back from ${count === 1 ? 'the attack' : `the ${attacks}`}.`
          : `Nobody came back from the ${attacks} on ${from}.`;
      }
      const head =
        from === null ? `Farm run back in ${village}: ${attacks}` : `${attacks} on ${from} back in ${village}`;
      return `${head}${loot === null ? '' : `, ${loot} resources`}${lost === 0 ? '' : `, ${lost} lost`}.`;
    },
    /** 1.6: `resources` is "wood, clay". */
    storageFull: (village: string, resources: string) => `Warehouse full in ${village}: ${resources}.`,
  },
  resources: {
    title: 'Resources',
    wood: 'Wood',
    clay: 'Clay',
    iron: 'Iron',
    population: 'Population',
    /** Production, e.g. "+30/h". The amount is already formatted. */
    perHour: (amount: string) => `+${amount}/h`,
    perHourLong: (amount: string) => `${amount} per hour`,
    capacity: (amount: string) => `max. ${amount}`,
    full: 'warehouse full',
    populationFull: 'farm full',
    populationAlmostFull: 'farm almost full',
    showDetails: 'Show resource details',
    hideDetails: 'Hide resource details',
    /** Tooltip on wood, clay and iron in the top bar: they link to the warehouse page. */
    toWarehouse: 'Open the warehouse',
  },
  /** Keyed by BuildingType (02-game-rules §4.1). `satisfies` makes the compiler check that all 15 are there. */
  buildings: {
    HEADQUARTERS: 'Town Hall',
    BARRACKS: 'Barracks',
    STABLE: 'Stable',
    WORKSHOP: 'Workshop',
    ACADEMY: 'Academy',
    SMITHY: 'Smithy',
    RALLY_POINT: 'Rally Point',
    MARKET: 'Market',
    WOODCUTTER: 'Woodcutter',
    CLAY_PIT: 'Clay Pit',
    IRON_MINE: 'Iron Mine',
    FARM: 'Farm',
    WAREHOUSE: 'Warehouse',
    HIDING_PLACE: 'Hiding Place',
    WALL: 'Wall',
    BANNER_HALL: 'Banner Hall',
  } satisfies Record<BuildingType, string>,
  map: {
    barbarianVillage: 'Barbarian village',
  },
};

/** The shape of these texts in every language. */
export type PushTexts = typeof enPushTexts;

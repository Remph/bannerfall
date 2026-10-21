import { formatCoords, formatDateTime, formatWhenExact } from '../game/format';
import { sideName } from '../game/victory';
import type { Dictionary } from '../i18n/en';
import type { Language } from '../i18n/language';
import { conversationPath } from '../pages/messages/messagePaths';
import { rankingPath } from '../pages/ranking/rankingPaths';
import { attacksReturnedText, storageFullText } from '../realtime/returnTexts';
import type { PushMessage } from './pushMessage';

/** What the service worker shows for a push. */
export interface PushNotificationContent {
  title: string;
  body: string;
  /** The page a tap opens. */
  url: string;
  /** A later notification with the same tag replaces this one (one per attacked village). */
  tag: string;
}

/**
 * The notification for a push (1.5), with the same words as the toast where there is one; undefined
 * for a message that says nothing (it isn't shown). `nowMs` is the device's clock: "today 14:02".
 */
export function pushNotificationContent(
  message: PushMessage,
  t: Dictionary,
  language: Language,
  nowMs: number,
): PushNotificationContent | undefined {
  const title = message.world;
  if (message.test === true) {
    return { title, body: t.push.testBody, url: '/notifications', tag: 'test' };
  }
  const event = message.event;
  const village = message.village;
  const place = village === undefined ? t.realtime.yourVillage : villageLabel(village, t);
  switch (event?.type) {
    case 'INCOMING_ATTACK': {
      const url = `/v/${event.targetVillageId}/rally#incoming`;
      const tag = `attack-${event.targetVillageId}`;
      const count = message.attacks ?? 1;
      if (count > 1 && message.firstArrival !== undefined) {
        const first = formatWhenExact(Date.parse(message.firstArrival), nowMs, language);
        return { title, body: t.push.attacks(count, place, first), url, tag };
      }
      const source = event.sourceVillage;
      const from = `${source.name ?? t.map.barbarianVillage} ${formatCoords(source.x, source.y)}`;
      const arrival = formatWhenExact(Date.parse(event.arriveAt), nowMs, language);
      return { title, body: t.realtime.incomingAttack(place, from, event.sourcePlayerName, arrival), url, tag };
    }
    case 'VILLAGE_LOST':
      return {
        title,
        body: t.push.villageLost(place, village?.playerName ?? null),
        url: '/reports',
        tag: `lost-${event.villageId}`,
      };
    case 'MESSAGE_RECEIVED':
      return {
        title,
        body: t.realtime.messageReceived(event.fromPlayerName),
        url: conversationPath(event.fromPlayerId),
        tag: `message-${event.fromPlayerId}`,
      };
    case 'GUILD_INVITED':
      return { title, body: t.realtime.guildInvited(event.guildName), url: '/guild', tag: `guild-${event.guildId}` };
    case 'WORLD_ENDED':
      return { title, body: t.realtime.worldEnded(event.winnerName), url: rankingPath(0), tag: 'world' };
    case 'VICTORY_CLOCK': {
      const body = victoryClockText(event, t, language);
      return body === undefined ? undefined : { title, body, url: rankingPath(0), tag: 'world' };
    }
    case 'BUILD_COMPLETE':
      return {
        title,
        body: (event.demolished ? t.push.demolishedDone : t.push.buildsDone)(
          place,
          t.buildings[event.building],
          event.level,
        ),
        url: `/v/${event.villageId}/hq`,
        tag: `builds-${event.villageId}`,
      };
    case 'ATTACKS_RETURNED':
      return {
        title,
        body: attacksReturnedText(event, place, t, language),
        url: '/reports',
        // a newer run from the same village takes the older one's place on the lock screen
        tag: `returned-${event.villageId}-${event.target?.id ?? 'run'}`,
      };
    case 'STORAGE_FULL':
      return {
        title,
        body: storageFullText(event, place, t),
        url: `/v/${event.villageId}/warehouse`,
        tag: `storage-${event.villageId}`,
      };
    case undefined:
      return undefined;
  }
}

/** "Hilltop 48|52"; a barbarian village by its localized name. */
function villageLabel(village: NonNullable<PushMessage['village']>, t: Dictionary): string {
  return `${village.name ?? t.map.barbarianVillage} ${formatCoords(village.x, village.y)}`;
}

/** The toast's words for the victory clock (`notifications.ts`), without its link. */
function victoryClockText(
  event: Extract<NonNullable<PushMessage['event']>, { type: 'VICTORY_CLOCK' }>,
  t: Dictionary,
  language: Language,
): string | undefined {
  const { leader, winsAt, previous } = event;
  if (leader !== null && winsAt !== null) {
    const when = formatDateTime(Date.parse(winsAt), language);
    if (!event.leaderYours) {
      return t.realtime.victoryClock(sideName(leader), when);
    }
    return leader.kind === 'GUILD' ? t.realtime.victoryClockYours(when) : t.realtime.victoryClockYou(when);
  }
  if (previous === null) {
    return undefined;
  }
  if (!event.previousYours) {
    return t.realtime.victoryClockStopped(sideName(previous));
  }
  return previous.kind === 'GUILD' ? t.realtime.victoryClockStoppedYours : t.realtime.victoryClockStoppedYou;
}

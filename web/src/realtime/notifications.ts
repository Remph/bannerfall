import type { QueryKey } from '@tanstack/react-query';
import { meQueryKey, type Me } from '../api/queries/auth';
import { worldSettingsKey } from '../api/queries/admin';
import { achievementsQueryKey } from '../api/queries/achievements';
import { automationQueryKey } from '../api/queries/automation';
import { bannersQueryKey } from '../api/queries/banners';
import { buildingsQueryKey } from '../api/queries/buildings';
import { farmListsQueryKey } from '../api/queries/farm';
import { mapMovementsQueryKey, mapQueryKey } from '../api/queries/map';
import { messagesQueryKey } from '../api/queries/messages';
import { marketQueryKey, offersQueryKey } from '../api/queries/market';
import { nobleQueryKey } from '../api/queries/noble';
import { paladinQueryKey } from '../api/queries/paladin';
import { playerQueryKey, rankingQueryKey } from '../api/queries/ranking';
import { recruitmentQueryKey } from '../api/queries/recruitment';
import { reportsQueryKey } from '../api/queries/reports';
import { hallTrainingQueryKey, smithyQueryKey } from '../api/queries/smithy';
import { myGuildQueryKey, guildsQueryKey } from '../api/queries/guilds';
import { troopsQueryKey } from '../api/queries/troops';
import { villageQueryKey } from '../api/queries/villages';
import { victoryQueryKey, worldQueryKey } from '../api/queries/world';
import type { components } from '../api/schema';
import type { ToastContent } from '../components/toasts';
import { formatCoords, formatDateTime, formatWhenExact } from '../game/format';
import { sideName } from '../game/victory';
import type { Dictionary } from '../i18n/en';
import { achievementName, levelName } from '../pages/achievements/achievementText';
import { bannersPath } from '../pages/banners/bannerPaths';
import { conversationPath } from '../pages/messages/messagePaths';
import { rankingPath } from '../pages/ranking/rankingPaths';
import type { Language } from '../i18n/language';
import { attacksReturnedText, storageFullText } from './returnTexts';

type Schemas = components['schemas'];

/**
 * What arrives on `/user/queue/events` (04-api *WebSocket*). The generated `GameNotification` is
 * only `{type: string}`; the union of its subtypes lets TypeScript narrow on `type`
 * (`switch (notification.type)`), like a sealed interface with records in Java.
 */
export type GameNotification =
  | Schemas['VillageUpdated']
  | Schemas['IncomingAttack']
  | Schemas['ReportCreated']
  | Schemas['BuildComplete']
  | Schemas['SupportArrived']
  | Schemas['VillageLost']
  | Schemas['VillageConquered']
  | Schemas['WorldOpened']
  | Schemas['WorldEnded']
  | Schemas['VictoryClock']
  | Schemas['GuildInvited']
  | Schemas['GuildChanged']
  | Schemas['GuildBannerRaised']
  | Schemas['MessageReceived']
  | Schemas['AchievementEarned']
  | Schemas['BannerFound']
  | Schemas['BannerStolen']
  | Schemas['AttacksReturned']
  | Schemas['StorageFull'];

const knownTypes: ReadonlySet<string> = new Set<GameNotification['type']>([
  'VILLAGE_UPDATED',
  'INCOMING_ATTACK',
  'REPORT_CREATED',
  'BUILD_COMPLETE',
  'SUPPORT_ARRIVED',
  'VILLAGE_LOST',
  'VILLAGE_CONQUERED',
  'WORLD_OPENED',
  'WORLD_ENDED',
  'VICTORY_CLOCK',
  'GUILD_INVITED',
  'GUILD_CHANGED',
  'GUILD_BANNER_RAISED',
  'MESSAGE_RECEIVED',
  'ACHIEVEMENT_EARNED',
  'BANNER_FOUND',
  'BANNER_STOLEN',
  'ATTACKS_RETURNED',
  'STORAGE_FULL',
]);

/**
 * The parsed body of a frame, or undefined for anything this client doesn't know (a newer server
 * may send types this web doesn't know yet: those are ignored, not errors).
 */
export function parseNotification(body: unknown): GameNotification | undefined {
  if (typeof body === 'object' && body !== null && 'type' in body && typeof body.type === 'string') {
    return knownTypes.has(body.type) ? (body as GameNotification) : undefined;
  }
  return undefined;
}

/** The empty prefix: every query. */
const allQueries: QueryKey = [];

/**
 * The queries a notification makes outdated (prefixes, for `invalidateQueries`). Only the queries
 * on screen are fetched again right away, the others when they are next used.
 */
export function staleQueries(notification: GameNotification): QueryKey[] {
  switch (notification.type) {
    case 'VILLAGE_UPDATED':
      // a battle changes units, wall, buildings (catapults) and resources, and points (/api/me);
      // a cancelled attack towards the village changes the red counter (/api/me)
      return [
        villageQueryKey(notification.villageId),
        troopsQueryKey(notification.villageId),
        buildingsQueryKey(notification.villageId),
        recruitmentQueryKey(notification.villageId),
        // a delivery or somebody accepting the village's offer (M13)
        marketQueryKey(notification.villageId),
        offersQueryKey,
        meQueryKey,
        // a cancelled attack towards the village, a support sent back: the markers on the map (M26)
        mapMovementsQueryKey,
        // a battle that landed unlocks the banner slot, catapults lower the Banner Hall or the
        // Smithy, dead paladins free their slots (1.2)
        bannersQueryKey,
        smithyQueryKey(notification.villageId),
        paladinQueryKey,
        // dead nobles and a conquest change the next noble's price (1.6)
        nobleQueryKey,
        // catapults also lower the hall that Hall training needs (1.4)
        hallTrainingQueryKey(notification.villageId),
        // the automation placed orders, or lost levels it had reached (1.6)
        automationQueryKey(notification.villageId),
      ];
    case 'INCOMING_ATTACK':
      return [
        villageQueryKey(notification.targetVillageId),
        troopsQueryKey(notification.targetVillageId),
        meQueryKey,
        mapMovementsQueryKey,
        // the banner slot is locked while an attack is on its way (02-game-rules §21)
        bannersQueryKey,
      ];
    case 'REPORT_CREATED':
      // an attack's report brings the loyalty the player has just seen on the map (M21); a copy
      // another player sent (M40) changes nothing there. A landed attack also changes the farm
      // list's banner chances: the attack is no longer on the way, and it may have used the roll
      // (1.2.3)
      if (notification.sharedBy !== null) {
        return [reportsQueryKey];
      }
      if (notification.kind === 'ATTACK') {
        return [reportsQueryKey, mapQueryKey, farmListsQueryKey];
      }
      return notification.kind === 'SCOUT' ? [reportsQueryKey, mapQueryKey] : [reportsQueryKey];
    case 'BUILD_COMPLETE':
      // a new level unlocks units and shortens recruit times (M5b review); points are in /api/me
      return [
        villageQueryKey(notification.villageId),
        buildingsQueryKey(notification.villageId),
        recruitmentQueryKey(notification.villageId),
        meQueryKey,
        // the Banner Hall's level caps the banner, the Smithy's opens research (1.2)
        bannersQueryKey,
        smithyQueryKey(notification.villageId),
        // the Banner Hall's level opens Hall training (1.4), the Market's gives merchants
        hallTrainingQueryKey(notification.villageId),
        marketQueryKey(notification.villageId),
        // a finished level moves the automation's line on (1.6)
        automationQueryKey(notification.villageId),
      ];
    case 'SUPPORT_ARRIVED':
      return [troopsQueryKey(notification.villageId), villageQueryKey(notification.villageId), mapMovementsQueryKey];
    case 'WORLD_OPENED':
      // the pre-game is over (the phase, the player's first village or the respawn screen), or the
      // host took the end of the world back (v1.3) and every time in the game moved on: everything
      return [allQueries];
    case 'WORLD_ENDED':
      // v1.3: the phase and the result (['world', 'victory'] is under ['world']), and the host's
      // admin cards
      return [worldQueryKey, meQueryKey, worldSettingsKey];
    case 'VICTORY_CLOCK':
      // v1.3: the leader and the clock on the victory card and the strip under the top bar
      return [victoryQueryKey];
    case 'GUILD_INVITED':
      // a new invitation shows on the guild page
      return [myGuildQueryKey];
    case 'MESSAGE_RECEIVED':
      // the inbox, the counter and the conversation if it is open
      return [messagesQueryKey];
    case 'ACHIEVEMENT_EARNED':
      // the list with its progress, and the badges on the profile
      return [achievementsQueryKey, playerQueryKey];
    case 'GUILD_CHANGED':
      // members, leader, description or tag changed: the guild pages, and wherever tags are shown
      return [guildsQueryKey, rankingQueryKey, playerQueryKey, mapQueryKey];
    case 'GUILD_BANNER_RAISED':
      // 1.7: the level changes the production and the defence of every village of the guild's members
      return [guildsQueryKey, ['village'], ['buildings']];
    case 'BANNER_FOUND':
      // M32b: a new banner in the stash
      return [bannersQueryKey];
    case 'BANNER_STOLEN':
      // the stash of the thief; the slot of the robbed village, and what its banner did there
      return notification.lost ? [bannersQueryKey, villageQueryKey(notification.village.id)] : [bannersQueryKey];
    case 'ATTACKS_RETURNED':
    case 'STORAGE_FULL':
      // 1.6, only words: the arrival brought its own VILLAGE_UPDATED, and resources tick on the screen
      return [];
    case 'VILLAGE_LOST':
    case 'VILLAGE_CONQUERED':
      // the village list (and needsRespawn) and everything about the village changed hands
      return [
        meQueryKey,
        villageQueryKey(notification.villageId),
        troopsQueryKey(notification.villageId),
        buildingsQueryKey(notification.villageId),
        recruitmentQueryKey(notification.villageId),
        // its movements went with it (02-game-rules §6)
        mapMovementsQueryKey,
        // and its banner (M32b, 02-game-rules §21.6.1)
        bannersQueryKey,
        // the village count gives the paladin slots, and paladins outside a lost village are gone
        paladinQueryKey,
        // the village count and the nobles that went with it set the next noble's price (1.6)
        nobleQueryKey,
        hallTrainingQueryKey(notification.villageId),
        // its owner's colour on the map, and the village count on the profiles
        mapQueryKey,
        playerQueryKey,
      ];
  }
}

/** The name of one of the player's villages for a message: "Hilltop", or "48|52" if unknown. */
function ownVillageName(me: Me, villageId: number): string | undefined {
  return me.villages.find((village) => village.id === villageId)?.name;
}

/**
 * The toast for a notification, or undefined for the ones that only refresh data (village updates,
 * supports, conquests: their report brings its own toast).
 */
export function notificationToast(
  notification: GameNotification,
  t: Dictionary,
  me: Me,
  nowMs: number,
  language: Language,
): ToastContent | undefined {
  switch (notification.type) {
    case 'INCOMING_ATTACK': {
      const source = notification.sourceVillage;
      const from = `${source.name ?? t.map.barbarianVillage} ${formatCoords(source.x, source.y)}`;
      const target = ownVillageName(me, notification.targetVillageId) ?? t.realtime.yourVillage;
      const arrival = formatWhenExact(Date.parse(notification.arriveAt), nowMs, language);
      return {
        text: t.realtime.incomingAttack(target, from, notification.sourcePlayerName, arrival),
        tone: 'danger',
        to: { pathname: `/v/${notification.targetVillageId}/rally`, hash: 'incoming' },
      };
    }
    case 'REPORT_CREATED':
      return {
        text:
          notification.sharedBy === null
            ? t.realtime.newReport(t.reports.kinds[notification.kind])
            : t.realtime.reportShared(notification.sharedBy, t.reports.kinds[notification.kind]),
        tone: 'info',
        to: { pathname: `/reports/${notification.reportId}` },
      };
    case 'BUILD_COMPLETE': {
      const village = ownVillageName(me, notification.villageId) ?? t.realtime.yourVillage;
      return {
        text: (notification.demolished ? t.realtime.demolished : t.realtime.buildComplete)(
          village,
          t.buildings[notification.building],
          notification.level,
        ),
        tone: 'success',
        to: { pathname: `/v/${notification.villageId}/hq` },
      };
    }
    case 'WORLD_OPENED':
      return { text: t.realtime.worldOpened, tone: 'success', to: { pathname: '/' } };
    case 'WORLD_ENDED':
      return { text: t.realtime.worldEnded(notification.winnerName), tone: 'info', to: { pathname: rankingPath(0) } };
    case 'VICTORY_CLOCK':
      return victoryClockToast(notification, t, language);
    case 'GUILD_INVITED':
      return { text: t.realtime.guildInvited(notification.guildName), tone: 'info', to: { pathname: '/guild' } };
    case 'GUILD_BANNER_RAISED':
      return { text: t.realtime.guildBannerRaised(notification.level), tone: 'success', to: { pathname: '/guild' } };
    case 'MESSAGE_RECEIVED':
      return {
        text: t.realtime.messageReceived(notification.fromPlayerName),
        tone: 'info',
        to: { pathname: conversationPath(notification.fromPlayerId) },
      };
    case 'ACHIEVEMENT_EARNED': {
      const name = achievementName(t, notification.achievement);
      // a one-time achievement is level 1 too, but is announced without one
      return {
        text: t.realtime.achievementEarned(name, notification.leveled ? levelName(t, notification.level) : null),
        tone: 'success',
        to: { pathname: '/achievements' },
      };
    }
    case 'BANNER_FOUND':
    case 'BANNER_STOLEN': {
      const banner = t.banners.label(t.banners.types[notification.bannerType], notification.tier);
      const where = notification.village;
      const village = `${where.name ?? t.map.barbarianVillage} ${formatCoords(where.x, where.y)}`;
      if (notification.type === 'BANNER_FOUND') {
        return { text: t.realtime.bannerFound(banner, village), tone: 'success', to: bannersLink(me) };
      }
      return notification.lost
        ? { text: t.realtime.bannerLost(banner, village), tone: 'danger', to: { pathname: bannersPath(where.id) } }
        : { text: t.realtime.bannerStolen(banner, village), tone: 'success', to: bannersLink(me) };
    }
    case 'ATTACKS_RETURNED': {
      const village = ownVillageName(me, notification.villageId) ?? t.realtime.yourVillage;
      return {
        text: attacksReturnedText(notification, village, t, language),
        tone: notification.lost === notification.attacks ? 'danger' : 'success',
        to: { pathname: '/reports' },
      };
    }
    case 'STORAGE_FULL': {
      const village = ownVillageName(me, notification.villageId) ?? t.realtime.yourVillage;
      // a full warehouse is one of the warnings (red, ui-design-principles)
      return {
        text: storageFullText(notification, village, t),
        tone: 'danger',
        to: { pathname: `/v/${notification.villageId}/warehouse` },
      };
    }
    // nothing to say: a conquest's CONQUEST / VILLAGE_LOST report brings its own toast, a change in
    // the guild only refreshes its page
    case 'GUILD_CHANGED':
    case 'VILLAGE_UPDATED':
    case 'SUPPORT_ARRIVED':
    case 'VILLAGE_LOST':
    case 'VILLAGE_CONQUERED':
      return undefined;
  }
}

/**
 * The victory clock started or passed to another side (when the leader wins), or stopped (who lost
 * it); "your guild" or "you" when the side is the player's own. Nothing if nobody held it.
 */
function victoryClockToast(
  notification: Schemas['VictoryClock'],
  t: Dictionary,
  language: Language,
): ToastContent | undefined {
  const to = { pathname: rankingPath(0) };
  const { leader, winsAt, previous } = notification;
  if (leader !== null && winsAt !== null) {
    const when = formatDateTime(Date.parse(winsAt), language);
    if (!notification.leaderYours) {
      return { text: t.realtime.victoryClock(sideName(leader), when), tone: 'info', to };
    }
    const text = leader.kind === 'GUILD' ? t.realtime.victoryClockYours(when) : t.realtime.victoryClockYou(when);
    return { text, tone: 'success', to };
  }
  if (previous === null) {
    return undefined;
  }
  if (!notification.previousYours) {
    return { text: t.realtime.victoryClockStopped(sideName(previous)), tone: 'info', to };
  }
  const text = previous.kind === 'GUILD' ? t.realtime.victoryClockStoppedYours : t.realtime.victoryClockStoppedYou;
  return { text, tone: 'info', to };
}

/**
 * Where a banner toast leads: the Banner Hall page of the player's first village (the stash is the
 * same from every village); the home page if he has none left.
 */
function bannersLink(me: Me): { pathname: string } {
  const first = me.villages[0];
  return { pathname: first === undefined ? '/' : bannersPath(first.id) };
}

/** All attacks on the way to the player's villages: the red counter in the top bar. */
export function totalIncomingAttacks(me: Me): number {
  return me.villages.reduce((sum, village) => sum + village.incomingAttacks, 0);
}

/**
 * Where the red counter leads: the incoming list of the current village if it is under attack,
 * else of the first village that is.
 */
export function attackedVillageId(me: Me, currentVillageId: number | undefined): number | undefined {
  const attacked = me.villages.filter((village) => village.incomingAttacks > 0);
  return (attacked.find((village) => village.id === currentVillageId) ?? attacked[0])?.id;
}

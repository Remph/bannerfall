import { describe, expect, it } from 'vitest';
import type { Me } from '../api/queries/auth';
import type { VictorySide } from '../api/queries/world';
import { formatDateTime } from '../game/format';
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import {
  attackedVillageId,
  notificationToast,
  parseNotification,
  staleQueries,
  totalIncomingAttacks,
  type GameNotification,
} from './notifications';

function me(villages: { id: number; name: string; incomingAttacks: number }[]): Me {
  return {
    accountId: 1,
    username: 'ben',
    role: 'PLAYER',
    playerId: 7,
    points: 100,
    protectedUntil: '2026-10-06T12:00:00Z',
    needsRespawn: false,
    linked: false,
    villages: villages.map((village) => ({ ...village, x: 50, y: 50, points: 100 })),
  };
}

const ben = me([
  { id: 1, name: 'Benhausen', incomingAttacks: 0 },
  { id: 2, name: 'Hilltop', incomingAttacks: 2 },
  { id: 3, name: 'Riverside', incomingAttacks: 1 },
]);

const now = Date.parse('2026-10-06T20:00:00Z');

describe('parseNotification', () => {
  it('accepts the known types', () => {
    expect(parseNotification({ type: 'VILLAGE_UPDATED', villageId: 4 })).toEqual({
      type: 'VILLAGE_UPDATED',
      villageId: 4,
    });
    expect(parseNotification({ type: 'VILLAGE_LOST', villageId: 4 })).toEqual({ type: 'VILLAGE_LOST', villageId: 4 });
  });

  it('ignores unknown types and anything else', () => {
    expect(parseNotification({ type: 'GUILD_INVITE', guildId: 4 })).toBeUndefined();
    expect(parseNotification({ villageId: 4 })).toBeUndefined();
    expect(parseNotification('VILLAGE_UPDATED')).toBeUndefined();
    expect(parseNotification(null)).toBeUndefined();
  });
});

describe('staleQueries', () => {
  it('refreshes the village, its troops, buildings, recruitment and market and /api/me after an update', () => {
    expect(staleQueries({ type: 'VILLAGE_UPDATED', villageId: 4 })).toEqual([
      ['village', 4],
      ['troops', 4],
      ['buildings', 4],
      ['recruitment', 4],
      ['market', 4],
      ['offers'],
      ['me'],
      ['mapMovements'],
      ['banners'],
      ['smithy', 4],
      ['paladin'],
    ]);
  });

  it('refreshes the attacked village and the red counter', () => {
    const attack: GameNotification = {
      type: 'INCOMING_ATTACK',
      targetVillageId: 2,
      sourceVillage: { id: 9, name: 'Tomburg', x: 40, y: 41, playerId: 3, playerName: 'Tom' },
      sourcePlayerName: 'Tom',
      arriveAt: '2026-10-06T21:14:05Z',
    };
    expect(staleQueries(attack)).toEqual([['village', 2], ['troops', 2], ['me'], ['mapMovements'], ['banners']]);
  });

  it('refreshes the reports list and counter for a new report', () => {
    expect(staleQueries({ type: 'REPORT_CREATED', reportId: 5, kind: 'DEFENSE', sharedBy: null })).toEqual([
      ['reports'],
    ]);
  });

  it('also reloads the map for the report of an own attack: it shows the loyalty just seen', () => {
    // and the farm lists: the attack is no longer on the way and may have used the banner roll
    expect(staleQueries({ type: 'REPORT_CREATED', reportId: 5, kind: 'ATTACK', sharedBy: null })).toEqual([
      ['reports'],
      ['mapChunk'],
      ['farm'],
    ]);
    expect(staleQueries({ type: 'REPORT_CREATED', reportId: 6, kind: 'SCOUT', sharedBy: null })).toEqual([
      ['reports'],
      ['mapChunk'],
    ]);
    // a copy another player sent (M40) changes nothing on the map
    expect(staleQueries({ type: 'REPORT_CREATED', reportId: 7, kind: 'ATTACK', sharedBy: 'Anna' })).toEqual([
      ['reports'],
    ]);
  });

  it('refreshes buildings and recruitment when a building is done', () => {
    expect(staleQueries({ type: 'BUILD_COMPLETE', villageId: 1, building: 'BARRACKS', level: 3 })).toEqual([
      ['village', 1],
      ['buildings', 1],
      ['recruitment', 1],
      ['me'],
      ['banners'],
      ['smithy', 1],
    ]);
  });

  it('refreshes the troops when a support arrived', () => {
    expect(staleQueries({ type: 'SUPPORT_ARRIVED', villageId: 1 })).toEqual([
      ['troops', 1],
      ['village', 1],
      ['mapMovements'],
    ]);
  });

  it('reloads everything when the pre-game ends or the host takes the end of the world back', () => {
    expect(staleQueries({ type: 'WORLD_OPENED' })).toEqual([[]]);
  });

  it('reloads the world, its result, the player and the admin cards when the world ends', () => {
    expect(staleQueries({ type: 'WORLD_ENDED', reason: 'DOMINATION', winnerName: 'Iron Wolves' })).toEqual([
      ['world'],
      ['me'],
      ['admin', 'world-settings'],
    ]);
  });

  it('reloads the victory state when the victory clock starts or stops', () => {
    expect(staleQueries(clock(null, null, false, false))).toEqual([['world', 'victory']]);
  });

  it('refreshes the invitations when a guild invites the player', () => {
    expect(staleQueries({ type: 'GUILD_INVITED', guildId: 3, guildName: 'Iron Wolves' })).toEqual([['guilds', 'mine']]);
  });

  it('refreshes the inbox, the counter and open conversations when a message arrives', () => {
    expect(staleQueries({ type: 'MESSAGE_RECEIVED', fromPlayerId: 7, fromPlayerName: 'Anna' })).toEqual([['messages']]);
  });

  it('refreshes the achievements and the profiles when one is earned', () => {
    expect(staleQueries({ type: 'ACHIEVEMENT_EARNED', achievement: 'VICTOR', leveled: true, level: 1 })).toEqual([
      ['achievements'],
      ['player'],
    ]);
  });

  it('refreshes the guilds and every list with tags when a guild changed', () => {
    expect(staleQueries({ type: 'GUILD_CHANGED' })).toEqual([['guilds'], ['ranking'], ['player'], ['mapChunk']]);
  });

  it('refreshes the village list and the village when it changed hands', () => {
    const expected = [
      ['me'],
      ['village', 4],
      ['troops', 4],
      ['buildings', 4],
      ['recruitment', 4],
      ['mapMovements'],
      ['banners'],
      ['paladin'],
    ];
    expect(staleQueries({ type: 'VILLAGE_LOST', villageId: 4 })).toEqual(expected);
    expect(staleQueries({ type: 'VILLAGE_CONQUERED', villageId: 4 })).toEqual(expected);
  });
});

describe('banner notifications (M32b)', () => {
  const tomburg = { id: 9, name: 'Tomburg', x: 40, y: 41, playerId: 3, playerName: 'Tom' };
  const found: GameNotification = { type: 'BANNER_FOUND', bannerType: 'WAR', tier: 2, village: tomburg };
  const lost: GameNotification = {
    type: 'BANNER_STOLEN',
    bannerType: 'OATH',
    tier: 1,
    village: { id: 2, name: 'Hilltop', x: 50, y: 52, playerId: 7, playerName: 'ben' },
    lost: true,
  };
  const stolen: GameNotification = {
    type: 'BANNER_STOLEN',
    bannerType: 'HASTE',
    tier: 3,
    village: tomburg,
    lost: false,
  };

  it('are known', () => {
    expect(parseNotification(found)).toEqual(found);
    expect(parseNotification(lost)).toEqual(lost);
  });

  it('refresh the banners, and the robbed village', () => {
    expect(staleQueries(found)).toEqual([['banners']]);
    expect(staleQueries(stolen)).toEqual([['banners']]);
    expect(staleQueries(lost)).toEqual([['banners'], ['village', 2]]);
  });

  it('toast the banner and the village', () => {
    expect(notificationToast(found, en, ben, now, 'en')).toEqual({
      text: 'Banner found at Tomburg (40|41): War banner, tier II',
      tone: 'success',
      to: { pathname: '/v/1/banners' },
    });
    expect(notificationToast(lost, en, ben, now, 'en')).toEqual({
      text: 'Banner stolen from Hilltop (50|52): Oath banner, tier I',
      tone: 'danger',
      to: { pathname: '/v/2/banners' },
    });
    expect(notificationToast(stolen, de, ben, now, 'de')?.text).toBe(
      'Deine Diebe haben in Tomburg (40|41) ein Banner gestohlen: Eilbanner, Stufe III',
    );
  });
});

const wolves: VictorySide = {
  kind: 'GUILD',
  id: 4,
  name: 'Iron Wolves',
  tag: 'IW',
  npc: false,
  villages: 9,
  points: 12000,
  keeps: 3,
};
const benSide: VictorySide = { ...wolves, kind: 'PLAYER', id: 7, name: 'ben', tag: null };
const winsAt = '2026-10-06T22:00:00Z';

function clock(
  leader: VictorySide | null,
  previous: VictorySide | null,
  leaderYours: boolean,
  previousYours: boolean,
): GameNotification {
  return {
    type: 'VICTORY_CLOCK',
    leader,
    winsAt: leader === null ? null : winsAt,
    previous,
    leaderYours,
    previousYours,
  };
}

describe('notificationToast', () => {
  it('tells every player when a side starts the victory clock, and when it wins', () => {
    const when = formatDateTime(Date.parse(winsAt), 'en');
    expect(notificationToast(clock(wolves, null, false, false), en, ben, now, 'en')).toEqual({
      text: `[IW] Iron Wolves meets the victory condition: they win on ${when} unless someone stops them`,
      tone: 'info',
      to: { pathname: '/ranking' },
    });
    // the player's own guild, or himself
    expect(notificationToast(clock(wolves, null, true, false), en, ben, now, 'en')).toMatchObject({
      text: `Your guild meets the victory condition: you win on ${when} if you hold on`,
      tone: 'success',
    });
    expect(notificationToast(clock(benSide, wolves, true, false), de, ben, now, 'de')?.text).toBe(
      `Du erfüllst die Siegbedingung: Sieg am ${formatDateTime(Date.parse(winsAt), 'de')}, wenn du durchhältst`,
    );
  });

  it('says who lost the victory clock, and nothing when nobody had it', () => {
    expect(notificationToast(clock(null, wolves, false, false), en, ben, now, 'en')?.text).toBe(
      '[IW] Iron Wolves no longer meets the victory condition. The victory clock has stopped.',
    );
    expect(notificationToast(clock(null, wolves, false, true), de, ben, now, 'de')?.text).toBe(
      'Deine Gilde erfüllt die Siegbedingung nicht mehr. Die Siegesuhr steht.',
    );
    expect(notificationToast(clock(null, null, false, false), en, ben, now, 'en')).toBeUndefined();
  });

  it('announces the end of the world with its winner and leads to the result', () => {
    const toast = notificationToast(
      { type: 'WORLD_ENDED', reason: 'KEEPS', winnerName: 'Iron Wolves' },
      en,
      ben,
      now,
      'en',
    );
    expect(toast).toEqual({
      text: 'The world has ended: Iron Wolves won!',
      tone: 'info',
      to: { pathname: '/ranking' },
    });
    const nobody = notificationToast({ type: 'WORLD_ENDED', reason: 'END_DATE', winnerName: null }, en, ben, now, 'en');
    expect(nobody?.text).toBe('The world has ended.');
  });

  it('warns of an attack with the village names and the arrival to the second', () => {
    const toast = notificationToast(
      {
        type: 'INCOMING_ATTACK',
        targetVillageId: 2,
        sourceVillage: { id: 9, name: 'Tomburg', x: 40, y: 41, playerId: 3, playerName: 'Tom' },
        sourcePlayerName: 'Tom',
        arriveAt: '2026-10-06T20:14:05Z',
      },
      en,
      ben,
      now,
      'en',
    );
    expect(toast?.tone).toBe('danger');
    expect(toast?.text).toMatch(/^Tom attacks Hilltop from Tomburg \(40\|41\) – arrives \d\d:14:05$/);
    expect(toast?.to).toEqual({ pathname: '/v/2/rally', hash: 'incoming' });
  });

  it('names a barbarian attacker without a player', () => {
    const toast = notificationToast(
      {
        type: 'INCOMING_ATTACK',
        targetVillageId: 99,
        sourceVillage: { id: 9, name: null, x: 40, y: 41, playerId: null, playerName: null },
        sourcePlayerName: null,
        arriveAt: '2026-10-06T20:14:05Z',
      },
      en,
      ben,
      now,
      'en',
    );
    expect(toast?.text).toContain('Attack on your village from Barbarian village (40|41) – arrives');
  });

  it('links a new report and a finished building', () => {
    expect(
      notificationToast({ type: 'REPORT_CREATED', reportId: 5, kind: 'DEFENSE', sharedBy: null }, en, ben, now, 'en'),
    ).toEqual({
      text: 'New report: Defence',
      tone: 'info',
      to: { pathname: '/reports/5' },
    });
    expect(
      notificationToast({ type: 'REPORT_CREATED', reportId: 6, kind: 'ATTACK', sharedBy: 'Anna' }, en, ben, now, 'en'),
    ).toEqual({ text: 'Anna sent you a report: Attack', tone: 'info', to: { pathname: '/reports/6' } });
    expect(
      notificationToast({ type: 'BUILD_COMPLETE', villageId: 1, building: 'BARRACKS', level: 3 }, en, ben, now, 'en'),
    ).toEqual({ text: 'Benhausen: Barracks reached level 3', tone: 'success', to: { pathname: '/v/1/hq' } });
  });

  it('toasts an invitation to a guild and links to the guild page', () => {
    expect(
      notificationToast({ type: 'GUILD_INVITED', guildId: 3, guildName: 'Iron Wolves' }, en, ben, now, 'en'),
    ).toEqual({ text: 'Iron Wolves invited you to join their guild', tone: 'info', to: { pathname: '/guild' } });
  });

  it('toasts a private message and links to the conversation', () => {
    expect(
      notificationToast({ type: 'MESSAGE_RECEIVED', fromPlayerId: 7, fromPlayerName: 'Anna' }, en, ben, now, 'en'),
    ).toEqual({ text: 'New message from Anna', tone: 'info', to: { pathname: '/messages/7' } });
  });

  it('toasts an earned achievement with its level, or without one for a one-time achievement', () => {
    expect(
      notificationToast(
        { type: 'ACHIEVEMENT_EARNED', achievement: 'VICTOR', leveled: true, level: 2 },
        en,
        ben,
        now,
        'en',
      ),
    ).toEqual({ text: 'Achievement earned: Victor (Silver)', tone: 'success', to: { pathname: '/achievements' } });
    expect(
      notificationToast(
        { type: 'ACHIEVEMENT_EARNED', achievement: 'FIRST_BLOOD', leveled: false, level: 1 },
        en,
        ben,
        now,
        'en',
      )?.text,
    ).toBe('Achievement earned: First Blood');
  });

  it('shows nothing for updates that only refresh data', () => {
    expect(notificationToast({ type: 'VILLAGE_UPDATED', villageId: 1 }, en, ben, now, 'en')).toBeUndefined();
    expect(notificationToast({ type: 'SUPPORT_ARRIVED', villageId: 1 }, en, ben, now, 'en')).toBeUndefined();
    // the conquest's report toasts
    expect(notificationToast({ type: 'VILLAGE_LOST', villageId: 1 }, en, ben, now, 'en')).toBeUndefined();
    expect(notificationToast({ type: 'GUILD_CHANGED' }, en, ben, now, 'en')).toBeUndefined();
  });
});

describe('the red counter', () => {
  it('adds up the attacks on all villages', () => {
    expect(totalIncomingAttacks(ben)).toBe(3);
    expect(totalIncomingAttacks(me([{ id: 1, name: 'A', incomingAttacks: 0 }]))).toBe(0);
  });

  it('leads to the current village if it is attacked, else to the first attacked one', () => {
    expect(attackedVillageId(ben, 3)).toBe(3);
    expect(attackedVillageId(ben, 1)).toBe(2);
    expect(attackedVillageId(ben, undefined)).toBe(2);
    expect(attackedVillageId(me([{ id: 1, name: 'A', incomingAttacks: 0 }]), 1)).toBeUndefined();
  });
});

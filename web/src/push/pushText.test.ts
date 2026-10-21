import { describe, expect, it } from 'vitest';
import { formatWhenExact } from '../game/format';
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import { parsePushMessage, type PushMessage } from './pushMessage';
import { pushNotificationContent } from './pushText';

const now = Date.parse('2026-10-19T12:00:00Z');
const hilltop = { id: 7, name: 'Hilltop', x: 48, y: 52, playerId: 3, playerName: 'Nora' };
const attack = {
  type: 'INCOMING_ATTACK' as const,
  targetVillageId: 7,
  sourceVillage: { id: 9, name: 'Ironhold', x: 50, y: 50, playerId: 4, playerName: 'Bram' },
  sourcePlayerName: 'Bram',
  arriveAt: '2026-10-19T12:30:00Z',
};

function message(fields: Partial<PushMessage>): PushMessage {
  return { lang: 'en', world: 'Realm Two', ...fields };
}

describe('pushNotificationContent', () => {
  it('words one attack like its toast and opens the incoming list', () => {
    const content = pushNotificationContent(
      message({ event: attack, village: hilltop, attacks: 1, firstArrival: attack.arriveAt }),
      en,
      'en',
      now,
    );
    expect(content).toEqual({
      title: 'Realm Two',
      body: en.realtime.incomingAttack(
        'Hilltop (48|52)',
        'Ironhold (50|50)',
        'Bram',
        formatWhenExact(Date.parse(attack.arriveAt), now, 'en'),
      ),
      url: '/v/7/rally#incoming',
      tag: 'attack-7',
    });
  });

  it('counts the attacks on a village in one notification', () => {
    const content = pushNotificationContent(
      message({ event: attack, village: hilltop, attacks: 3, firstArrival: '2026-10-19T12:10:00Z' }),
      de,
      'de',
      now,
    );
    expect(content?.body).toContain('3 Angriffe auf dem Weg nach Hilltop (48|52)');
    expect(content?.tag).toBe('attack-7');
  });

  it('says who took a lost village and opens the reports', () => {
    const content = pushNotificationContent(
      message({ event: { type: 'VILLAGE_LOST', villageId: 7 }, village: { ...hilltop, playerName: 'Bram' } }),
      en,
      'en',
      now,
    );
    expect(content).toMatchObject({ body: 'Bram conquered Hilltop (48|52)', url: '/reports', tag: 'lost-7' });
  });

  it('names the sender of a message, not its text', () => {
    const content = pushNotificationContent(
      message({ event: { type: 'MESSAGE_RECEIVED', fromPlayerId: 4, fromPlayerName: 'Bram' } }),
      en,
      'en',
      now,
    );
    expect(content).toMatchObject({ body: 'New message from Bram', url: '/messages/4', tag: 'message-4' });
  });

  it('tells about an empty build queue with the last building', () => {
    const content = pushNotificationContent(
      message({
        event: { type: 'BUILD_COMPLETE', villageId: 7, building: 'CLAY_PIT', level: 5, demolished: false },
        village: hilltop,
      }),
      de,
      'de',
      now,
    );
    expect(content).toMatchObject({
      body: `Hilltop (48|52): Die Bauschleife ist leer. ${de.buildings.CLAY_PIT} hat Stufe 5 erreicht.`,
      url: '/v/7/hq',
      tag: 'builds-7',
    });
  });

  it('shows the victory clock like the toast, and nothing for a clock nobody held', () => {
    const side = {
      kind: 'GUILD' as const,
      id: 2,
      name: 'Knights',
      tag: 'KN',
      npc: false,
      villages: 9,
      points: 900,
      keeps: 0,
    };
    const started = pushNotificationContent(
      message({
        event: {
          type: 'VICTORY_CLOCK',
          leader: side,
          winsAt: '2026-10-21T12:00:00Z',
          previous: null,
          leaderYours: true,
          previousYours: false,
        },
      }),
      en,
      'en',
      now,
    );
    expect(started?.body).toMatch(/^Your guild meets the victory condition/);
    expect(
      pushNotificationContent(
        message({
          event: {
            type: 'VICTORY_CLOCK',
            leader: null,
            winsAt: null,
            previous: null,
            leaderYours: false,
            previousYours: false,
          },
        }),
        en,
        'en',
        now,
      ),
    ).toBeUndefined();
  });

  it('confirms a test', () => {
    expect(pushNotificationContent(message({ test: true }), de, 'de', now)).toEqual({
      title: 'Realm Two',
      body: de.push.testBody,
      url: '/notifications',
      tag: 'test',
    });
  });
});

describe('pushNotificationContent (1.6)', () => {
  it('says a farm list run is back, with the home village from the message', () => {
    const content = pushNotificationContent(
      message({
        event: {
          type: 'ATTACKS_RETURNED',
          villageId: 7,
          target: null,
          attacks: 4,
          lost: 1,
          loot: { wood: 100, clay: 100, iron: 100 },
          farmRun: true,
        },
        village: hilltop,
      }),
      en,
      'en',
      now,
    );
    expect(content).toEqual({
      title: 'Realm Two',
      body: 'Farm run back in Hilltop (48|52): 4 attacks, 300 resources, 1 lost.',
      url: '/reports',
      tag: 'returned-7-run',
    });
  });

  it('counts a run of one attack in the singular, in both languages', () => {
    const run = (lost: number) =>
      message({
        event: {
          type: 'ATTACKS_RETURNED',
          villageId: 7,
          target: null,
          attacks: 1,
          lost,
          loot: { wood: 0, clay: 0, iron: 0 },
          farmRun: true,
        },
        village: hilltop,
      });
    expect(pushNotificationContent(run(0), en, 'en', now)?.body).toBe('Farm run back in Hilltop (48|52): 1 attack.');
    expect(pushNotificationContent(run(0), de, 'de', now)?.body).toBe(
      'Farmlisten-Runde zurück in Hilltop (48|52): 1 Angriff.',
    );
    expect(pushNotificationContent(run(1), en, 'en', now)?.body).toBe(
      'Farm run from Hilltop (48|52): nobody came back from the attack.',
    );
    expect(pushNotificationContent(run(1), de, 'de', now)?.body).toBe(
      'Farmlisten-Runde aus Hilltop (48|52): Von dem Angriff ist niemand zurückgekehrt.',
    );
  });

  it('names the full resources and opens the warehouse', () => {
    const content = pushNotificationContent(
      message({ event: { type: 'STORAGE_FULL', villageId: 7, full: ['CLAY'] }, village: hilltop }),
      de,
      'de',
      now,
    );
    expect(content).toEqual({
      title: 'Realm Two',
      body: 'Speicher voll in Hilltop (48|52): Lehm.',
      url: '/v/7/warehouse',
      tag: 'storage-7',
    });
  });
});

describe('parsePushMessage', () => {
  it('takes only what the server sends', () => {
    expect(parsePushMessage({ lang: 'en', world: 'W', test: true })).toBeDefined();
    expect(parsePushMessage({ lang: 'en', world: 'W', event: attack })).toBeDefined();
    expect(parsePushMessage({ lang: 'en', world: 'W' })).toBeUndefined();
    expect(parsePushMessage({ title: 'spam' })).toBeUndefined();
    expect(parsePushMessage(null)).toBeUndefined();
  });
});

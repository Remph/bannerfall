import type { AdminWorldSettings } from '../api/queries/admin';
import type { Me } from '../api/queries/auth';
import type { Banners } from '../api/queries/banners';
import type { Noble } from '../api/queries/noble';
import type { Paladin } from '../api/queries/paladin';
import type { HallTraining, Smithy } from '../api/queries/smithy';
import type { Tutorial } from '../api/queries/tutorial';
import type { Village } from '../api/queries/villages';
import type { World } from '../api/queries/world';

/*
 * Answers of a real dev server (1.3), for the render tests: player Aldric with one village (59),
 * a Banner Hall at level 2 with a Harvest banner II flying, a War banner III, two Bulwark banners I
 * and a Haste banner I in the stash, Smithy 10 with unit levels on and the spearman's level 2 being
 * researched. Typed with the API's types, so a changed API breaks them at compile time.
 */

/** The moment the answers were taken: tests set the clock here. */
export const FIXTURE_NOW = '2026-10-15T20:27:07.144Z';

export const village: Village = {
  id: 59,
  name: "Aldric's village",
  x: 61,
  y: 58,
  points: 39,
  loyalty: 100,
  resources: {
    wood: 4200.4107491666655,
    clay: 4300.4107491666655,
    iron: 4700.4107491666655,
    at: '2026-10-15T20:27:07.144Z',
  },
  production: {
    wood: 33.0,
    clay: 33.0,
    iron: 33.0,
  },
  storageCapacity: 6397,
  hidingCapacity: 0,
  population: {
    used: 150,
    capacity: 1151,
  },
  buildings: {
    HEADQUARTERS: 10,
    BARRACKS: 5,
    STABLE: 0,
    WORKSHOP: 0,
    ACADEMY: 0,
    SMITHY: 10,
    RALLY_POINT: 1,
    MARKET: 0,
    WOODCUTTER: 1,
    CLAY_PIT: 1,
    IRON_MINE: 1,
    FARM: 10,
    WAREHOUSE: 10,
    HIDING_PLACE: 0,
    WALL: 0,
    BANNER_HALL: 2,
  },
  buildQueue: [],
  unitsHome: {},
  incomingAttacks: 0,
  banner: {
    type: 'HARVEST',
    tier: 2,
    effectiveTier: 2,
  },
};

/** The logged-in player who owns {@link village}; the beginner protection is long over. */
export const me: Me = {
  accountId: 1,
  username: 'aldric',
  role: 'PLAYER',
  playerId: 5,
  points: 39,
  protectedUntil: '2026-10-10T00:00:00Z',
  needsRespawn: false,
  linked: false,
  respawnNearGuild: false,
  villages: [
    {
      id: village.id,
      name: village.name,
      x: village.x,
      y: village.y,
      points: village.points,
      incomingAttacks: 0,
      tag: null,
    },
  ],
};

/** The running world the fixture village belongs to (a 100 × 100 map, banners and paladin on). */
export const world: World = {
  name: 'Bannerfall 1',
  speed: 3,
  unitSpeed: 1,
  mapSize: 100,
  mapSpread: 'NORMAL',
  buildQueueSize: 5,
  buildQueueMax: 10,
  recruitQueueSize: 5,
  tradeMaxRatio: 3,
  startedAt: '2026-10-10T12:00:00Z',
  startResources: 500,
  luckRange: 0.25,
  beginnerProtectionHours: 72,
  nobleMaxDistance: 70,
  loyaltyRegenPerHour: 1,
  cancelWindowSeconds: 600,
  barbarianGrowthEnabled: true,
  npcLordCount: 4,
  npcDifficulty: 'NORMAL',
  guildsEnabled: false,
  guildMaxMembers: 10,
  guildStartTogether: true,
  guildBannersEnabled: false,
  npcGuildMaxMembers: 3,
  barbarianWallsEnabled: false,
  barbarianWallStartDays: 3,
  barbarianWallMaxLevel: 5,
  barbarianWallsRebuild: false,
  unitLevelsEnabled: true,
  paladinVillagesPerSlot: 4,
  paladinMaxSlots: 5,
  bannersEnabled: true,
  bannerDropFactor: 1,
  marketExchangeRatio: 1.5,
  victoryMode: 'DOMINATION',
  dominationPercent: 60,
  victoryHoldHours: 48,
  keepCount: 5,
  keepPercent: 51,
  endsAt: null,
  endedAt: null,
  phase: 'OPEN',
  opensAt: null,
  registeredPlayers: 6,
  version: '1.7.0',
  nightBonus: null,
  archersEnabled: false,
  moraleFloor: 30,
  noblePriceStep: 0.25,
};

export const banners: Banners = {
  enabled: true,
  hallMaxLevel: 3,
  effects: {
    HARVEST: [0.05, 0.1, 0.15],
    BUILDERS: [0.05, 0.1, 0.15],
    DRILL: [0.05, 0.1, 0.15],
    WAR: [0.03, 0.06, 0.1],
    BULWARK: [0.03, 0.06, 0.1],
    HASTE: [0.05, 0.1, 0.15],
    TREASURY: [0.1, 0.2, 0.3],
    PLUNDER: [0.1, 0.2, 0.3],
    OATH: [0.04, 0.08, 0.12],
    SPEAR_STANDARD: [0.05, 0.1, 0.15],
    SWORD_STANDARD: [0.05, 0.1, 0.15],
    AXE_STANDARD: [0.05, 0.1, 0.15],
    LANCE_STANDARD: [0.05, 0.1, 0.15],
    KNIGHT_STANDARD: [0.04, 0.08, 0.12],
    RAM_STANDARD: [0.1, 0.2, 0.3],
    SIEGE_STANDARD: [0.1, 0.2, 0.3],
  },
  banners: [
    {
      id: 1,
      type: 'HARVEST',
      tier: 2,
      villageId: 59,
      equippedAt: '2026-10-15T20:26:38.451623Z',
    },
    {
      id: 2,
      type: 'WAR',
      tier: 3,
      villageId: null,
      equippedAt: null,
    },
    {
      id: 3,
      type: 'BULWARK',
      tier: 1,
      villageId: null,
      equippedAt: null,
    },
    {
      id: 4,
      type: 'BULWARK',
      tier: 1,
      villageId: null,
      equippedAt: null,
    },
    {
      id: 5,
      type: 'HASTE',
      tier: 1,
      villageId: null,
      equippedAt: null,
    },
  ],
  villages: [
    {
      id: 59,
      name: "Aldric's village",
      x: 61,
      y: 58,
      hallLevel: 2,
      bannerId: 1,
      effectiveTier: 2,
      locked: false,
      standardId: null,
      standardEffectiveTier: 0,
      standardLocked: false,
    },
  ],
  paladinsEnabled: true,
  paladinLevel: 1,
  standardTheftEnabled: true,
};

export const smithy: Smithy = {
  smithyLevel: 10,
  levelsEnabled: true,
  units: [
    {
      unit: 'SPEARMAN',
      trainedIn: 'SMITHY',
      attackType: 'INFANTRY',
      minutesPerField: 18.0,
      carry: 25,
      level: 1,
      maxLevel: 3,
      stats: [
        {
          attack: 10,
          defenseInfantry: 15,
          defenseCavalry: 40,
          defenseArcher: 20,
        },
        {
          attack: 11,
          defenseInfantry: 17,
          defenseCavalry: 44,
          defenseArcher: 22,
        },
        {
          attack: 12,
          defenseInfantry: 18,
          defenseCavalry: 48,
          defenseArcher: 24,
        },
      ],
      researching: {
        id: 1,
        villageId: 59,
        unit: 'SPEARMAN',
        level: 2,
        startAt: '2026-10-15T20:27:06.951Z',
        endAt: '2026-10-16T03:07:57.951Z',
      },
      next: null,
    },
    {
      unit: 'SWORDSMAN',
      trainedIn: 'SMITHY',
      attackType: 'INFANTRY',
      minutesPerField: 22.0,
      carry: 15,
      level: 1,
      maxLevel: 3,
      stats: [
        {
          attack: 20,
          defenseInfantry: 50,
          defenseCavalry: 20,
          defenseArcher: 40,
        },
        {
          attack: 22,
          defenseInfantry: 55,
          defenseCavalry: 22,
          defenseArcher: 44,
        },
        {
          attack: 24,
          defenseInfantry: 60,
          defenseCavalry: 24,
          defenseArcher: 48,
        },
      ],
      researching: null,
      next: {
        level: 2,
        cost: {
          wood: 600,
          clay: 600,
          iron: 1400,
        },
        durationSeconds: 24051,
        requires: [
          {
            building: 'BARRACKS',
            level: 1,
          },
          {
            building: 'SMITHY',
            level: 10,
          },
        ],
        missing: [],
        affordable: true,
        villageBusy: true,
      },
    },
    {
      unit: 'AXEMAN',
      trainedIn: 'SMITHY',
      attackType: 'INFANTRY',
      minutesPerField: 18.0,
      carry: 10,
      level: 1,
      maxLevel: 3,
      stats: [
        {
          attack: 40,
          defenseInfantry: 10,
          defenseCavalry: 5,
          defenseArcher: 10,
        },
        {
          attack: 44,
          defenseInfantry: 11,
          defenseCavalry: 6,
          defenseArcher: 11,
        },
        {
          attack: 48,
          defenseInfantry: 12,
          defenseCavalry: 7,
          defenseArcher: 12,
        },
      ],
      researching: null,
      next: {
        level: 2,
        cost: {
          wood: 1000,
          clay: 700,
          iron: 900,
        },
        durationSeconds: 24051,
        requires: [
          {
            building: 'BARRACKS',
            level: 2,
          },
          {
            building: 'SMITHY',
            level: 10,
          },
        ],
        missing: [],
        affordable: true,
        villageBusy: true,
      },
    },
    {
      unit: 'SCOUT',
      trainedIn: 'SMITHY',
      attackType: 'INFANTRY',
      minutesPerField: 9.0,
      carry: 0,
      level: 1,
      maxLevel: 2,
      stats: [
        {
          attack: 0,
          defenseInfantry: 2,
          defenseCavalry: 1,
          defenseArcher: 2,
        },
        {
          attack: 0,
          defenseInfantry: 3,
          defenseCavalry: 2,
          defenseArcher: 3,
        },
      ],
      researching: null,
      next: {
        level: 2,
        cost: {
          wood: 1000,
          clay: 1000,
          iron: 400,
        },
        durationSeconds: 24051,
        requires: [
          {
            building: 'STABLE',
            level: 1,
          },
          {
            building: 'SMITHY',
            level: 10,
          },
        ],
        missing: [
          {
            building: 'STABLE',
            level: 1,
          },
        ],
        affordable: true,
        villageBusy: true,
      },
    },
    {
      unit: 'LIGHT_CAVALRY',
      trainedIn: 'SMITHY',
      attackType: 'CAVALRY',
      minutesPerField: 10.0,
      carry: 80,
      level: 1,
      maxLevel: 3,
      stats: [
        {
          attack: 120,
          defenseInfantry: 30,
          defenseCavalry: 40,
          defenseArcher: 30,
        },
        {
          attack: 132,
          defenseInfantry: 33,
          defenseCavalry: 44,
          defenseArcher: 33,
        },
        {
          attack: 144,
          defenseInfantry: 36,
          defenseCavalry: 48,
          defenseArcher: 36,
        },
      ],
      researching: null,
      next: {
        level: 2,
        cost: {
          wood: 2500,
          clay: 2000,
          iron: 5000,
        },
        durationSeconds: 24051,
        requires: [
          {
            building: 'STABLE',
            level: 3,
          },
          {
            building: 'SMITHY',
            level: 10,
          },
        ],
        missing: [
          {
            building: 'STABLE',
            level: 3,
          },
        ],
        affordable: false,
        villageBusy: true,
      },
    },
    {
      unit: 'HEAVY_CAVALRY',
      trainedIn: 'SMITHY',
      attackType: 'CAVALRY',
      minutesPerField: 11.0,
      carry: 50,
      level: 1,
      maxLevel: 3,
      stats: [
        {
          attack: 150,
          defenseInfantry: 200,
          defenseCavalry: 80,
          defenseArcher: 180,
        },
        {
          attack: 165,
          defenseInfantry: 220,
          defenseCavalry: 88,
          defenseArcher: 198,
        },
        {
          attack: 180,
          defenseInfantry: 240,
          defenseCavalry: 96,
          defenseArcher: 216,
        },
      ],
      researching: null,
      next: {
        level: 2,
        cost: {
          wood: 4000,
          clay: 3000,
          iron: 12000,
        },
        durationSeconds: 24051,
        requires: [
          {
            building: 'STABLE',
            level: 10,
          },
          {
            building: 'SMITHY',
            level: 15,
          },
        ],
        missing: [
          {
            building: 'STABLE',
            level: 10,
          },
          {
            building: 'SMITHY',
            level: 15,
          },
        ],
        affordable: false,
        villageBusy: true,
      },
    },
    {
      unit: 'RAM',
      trainedIn: 'SMITHY',
      attackType: 'INFANTRY',
      minutesPerField: 30.0,
      carry: 0,
      level: 1,
      maxLevel: 1,
      stats: [
        {
          attack: 2,
          defenseInfantry: 20,
          defenseCavalry: 50,
          defenseArcher: 20,
        },
      ],
      researching: null,
      next: null,
    },
    {
      unit: 'CATAPULT',
      trainedIn: 'SMITHY',
      attackType: 'INFANTRY',
      minutesPerField: 30.0,
      carry: 0,
      level: 1,
      maxLevel: 1,
      stats: [
        {
          attack: 100,
          defenseInfantry: 100,
          defenseCavalry: 50,
          defenseArcher: 100,
        },
      ],
      researching: null,
      next: null,
    },
    {
      unit: 'NOBLE',
      trainedIn: 'SMITHY',
      attackType: 'INFANTRY',
      minutesPerField: 35.0,
      carry: 0,
      level: 1,
      maxLevel: 1,
      stats: [
        {
          attack: 30,
          defenseInfantry: 100,
          defenseCavalry: 50,
          defenseArcher: 100,
        },
      ],
      researching: null,
      next: null,
    },
    {
      unit: 'PALADIN',
      trainedIn: 'BANNER_HALL',
      attackType: 'INFANTRY',
      minutesPerField: 14.0,
      carry: 0,
      level: 2,
      maxLevel: 3,
      stats: [
        { attack: 120, defenseInfantry: 150, defenseCavalry: 150, defenseArcher: 150 },
        { attack: 126, defenseInfantry: 158, defenseCavalry: 158, defenseArcher: 158 },
        { attack: 132, defenseInfantry: 165, defenseCavalry: 165, defenseArcher: 165 },
      ],
      researching: null,
      next: null,
    },
    {
      unit: 'BANNER_THIEF',
      trainedIn: 'BANNER_HALL',
      attackType: 'INFANTRY',
      minutesPerField: 18.0,
      carry: 0,
      level: 1,
      maxLevel: 3,
      stats: [
        { attack: 0, defenseInfantry: 3, defenseCavalry: 3, defenseArcher: 3 },
        { attack: 0, defenseInfantry: 3, defenseCavalry: 3, defenseArcher: 3 },
        { attack: 0, defenseInfantry: 3, defenseCavalry: 3, defenseArcher: 3 },
      ],
      researching: null,
      next: null,
    },
  ],
  running: {
    id: 1,
    villageId: 59,
    unit: 'SPEARMAN',
    level: 2,
    startAt: '2026-10-15T20:27:06.951Z',
    endAt: '2026-10-16T03:07:57.951Z',
  },
};

/** GET …/banner-hall (Hall training, 1.4): paladin at level 2 training level 3, thief at level 1. */
export const hallTraining: HallTraining = {
  hallLevel: 3,
  units: [
    {
      unit: 'PALADIN',
      trainedIn: 'BANNER_HALL',
      attackType: 'INFANTRY',
      minutesPerField: 2.333,
      carry: 0,
      level: 2,
      maxLevel: 3,
      stats: [
        { attack: 120, defenseInfantry: 150, defenseCavalry: 150, defenseArcher: 150 },
        { attack: 126, defenseInfantry: 158, defenseCavalry: 158, defenseArcher: 158 },
        { attack: 132, defenseInfantry: 165, defenseCavalry: 165, defenseArcher: 165 },
      ],
      researching: {
        id: 7,
        villageId: 59,
        unit: 'PALADIN',
        level: 3,
        startAt: '2026-10-15T20:27:06.951Z',
        endAt: '2026-10-16T03:07:57.951Z',
      },
      next: null,
    },
    {
      unit: 'BANNER_THIEF',
      trainedIn: 'BANNER_HALL',
      attackType: 'INFANTRY',
      minutesPerField: 3.0,
      carry: 0,
      level: 1,
      maxLevel: 3,
      stats: [
        { attack: 0, defenseInfantry: 3, defenseCavalry: 3, defenseArcher: 3 },
        { attack: 0, defenseInfantry: 3, defenseCavalry: 3, defenseArcher: 3 },
        { attack: 0, defenseInfantry: 3, defenseCavalry: 3, defenseArcher: 3 },
      ],
      researching: null,
      next: {
        level: 2,
        cost: { wood: 2000, clay: 2400, iron: 3000 },
        durationSeconds: 6746,
        requires: [{ building: 'BANNER_HALL', level: 2 }],
        missing: [],
        affordable: true,
        villageBusy: true,
      },
    },
  ],
  running: {
    id: 7,
    villageId: 59,
    unit: 'PALADIN',
    level: 3,
    startAt: '2026-10-15T20:27:06.951Z',
    endAt: '2026-10-16T03:07:57.951Z',
  },
  theftChancePerThief: [0.03, 0.04, 0.05],
};

export const paladin: Paladin = {
  maxSlots: 5,
  villagesPerSlot: 4,
  villages: 1,
  slots: 1,
  used: 0,
  nextSlotAtVillages: 5,
  minutesPerField: 14.0,
};

/** GET /api/noble of a player with 2 villages and 3 nobles at step 0.25 (1.6): rank 1. */
export const noble: Noble = {
  villages: 2,
  nobles: 3,
  freeRanks: 4,
  rank: 1,
  step: 0.25,
  maxFactor: 7,
  factor: 1.25,
  baseCost: { wood: 40_000, clay: 50_000, iron: 50_000 },
  cost: { wood: 50_000, clay: 62_500, iron: 62_500 },
  costAfter: { wood: 60_000, clay: 75_000, iron: 75_000 },
};

/**
 * The admin's world settings of a fresh pre-game world, before the guided setup (1.6). A new world
 * setting is added here once; tests pass only the fields they care about.
 */
export function adminSettingsFixture(overrides: Partial<AdminWorldSettings> = {}): AdminWorldSettings {
  return { ...pregameDefaults, ...overrides };
}

const pregameDefaults: AdminWorldSettings = {
  name: 'Bannerfall 1',
  speed: 3,
  unitSpeed: 1,
  mapSize: 100,
  mapSpread: 'NORMAL',
  startResources: 500,
  luckRange: 0.25,
  beginnerProtectionHours: 72,
  nobleMaxDistance: 70,
  loyaltyRegenPerHour: 1,
  buildQueueSize: 5,
  buildQueueMax: 10,
  recruitQueueSize: 5,
  cancelWindowSeconds: 600,
  barbarianInitialCount: 30,
  barbariansPerNewPlayer: 3,
  barbarianGrowthEnabled: true,
  tradeMaxRatio: 3,
  inviteCode: 'friends-only',
  npcLordCount: 4,
  npcDifficulty: 'NORMAL',
  npcThinkMinutes: 20,
  guildsEnabled: false,
  guildMaxMembers: 10,
  guildStartTogether: true,
  guildBannersEnabled: false,
  tutorialEnabled: true,
  npcGuildsEnabled: false,
  npcGuildMaxMembers: 3,
  unitLevelsEnabled: false,
  paladinVillagesPerSlot: 4,
  paladinMaxSlots: 5,
  noblePriceStep: 0.25,
  bannersEnabled: true,
  bannerDropFactor: 1,
  standardTheftEnabled: true,
  npcBannersEnabled: false,
  npcLearningEnabled: false,
  npcShipmentsEnabled: true,
  marketExchangeRatio: 1.5,
  villageNameLanguage: 'EN',
  nightBonusEnabled: false,
  nightBonusStartHour: 23,
  nightBonusEndHour: 7,
  nightBonusFactor: 2,
  archersEnabled: false,
  moraleEnabled: true,
  moraleFloor: 30,
  automationVillages: 3,
  barbarianWallsEnabled: false,
  barbarianWallStartDays: 3,
  barbarianWallMaxLevel: 5,
  barbarianWallsRebuild: false,
  victoryMode: 'DOMINATION',
  dominationPercent: 60,
  victoryHoldHours: 48,
  keepCount: 5,
  keepPercent: 51,
  phase: 'PREGAME',
  setupDone: false,
  opensAt: null,
  endsAt: null,
  endedAt: null,
};

export const pregameWorldSettings: AdminWorldSettings = adminSettingsFixture();

/** A new player's tutorial (1.7): the first of 11 steps is up, the world has no unit levels. */
export const tutorial: Tutorial = {
  enabled: true,
  active: true,
  current: { key: 'WOODCUTTER', kind: 'STEP', index: 1, total: 11, refund: { wood: 63, clay: 75, iron: 50 } },
  steps: [
    { key: 'WOODCUTTER', kind: 'STEP', status: 'OPEN' },
    { key: 'HEADQUARTERS', kind: 'STEP', status: 'UPCOMING' },
    { key: 'BARRACKS', kind: 'STEP', status: 'UPCOMING' },
    { key: 'SPEARMEN', kind: 'STEP', status: 'UPCOMING' },
    { key: 'MAP_BARBARIAN', kind: 'STEP', status: 'UPCOMING' },
    { key: 'ATTACK', kind: 'STEP', status: 'UPCOMING' },
    { key: 'REPORT', kind: 'STEP', status: 'UPCOMING' },
    { key: 'SIMULATOR', kind: 'STEP', status: 'UPCOMING' },
    { key: 'FARM_LIST', kind: 'STEP', status: 'UPCOMING' },
    { key: 'MARKET', kind: 'STEP', status: 'UPCOMING' },
    { key: 'GUILD', kind: 'STEP', status: 'UPCOMING' },
    { key: 'LESSON_CONQUEST', kind: 'LESSON', status: 'UPCOMING' },
    { key: 'LESSON_AUTOMATION', kind: 'LESSON', status: 'UPCOMING' },
  ],
  refunds: [],
};

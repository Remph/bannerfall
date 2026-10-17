import type { Banners } from '../api/queries/banners';
import type { Paladin } from '../api/queries/paladin';
import type { Smithy } from '../api/queries/smithy';
import type { Village } from '../api/queries/villages';

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
    },
  ],
};

export const smithy: Smithy = {
  smithyLevel: 10,
  levelsEnabled: true,
  units: [
    {
      unit: 'SPEARMAN',
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
        },
        {
          attack: 11,
          defenseInfantry: 17,
          defenseCavalry: 44,
        },
        {
          attack: 12,
          defenseInfantry: 18,
          defenseCavalry: 48,
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
        },
        {
          attack: 22,
          defenseInfantry: 55,
          defenseCavalry: 22,
        },
        {
          attack: 24,
          defenseInfantry: 60,
          defenseCavalry: 24,
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
        },
        {
          attack: 44,
          defenseInfantry: 11,
          defenseCavalry: 6,
        },
        {
          attack: 48,
          defenseInfantry: 12,
          defenseCavalry: 7,
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
        },
        {
          attack: 0,
          defenseInfantry: 3,
          defenseCavalry: 2,
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
        },
        {
          attack: 132,
          defenseInfantry: 33,
          defenseCavalry: 44,
        },
        {
          attack: 144,
          defenseInfantry: 36,
          defenseCavalry: 48,
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
        },
        {
          attack: 165,
          defenseInfantry: 220,
          defenseCavalry: 88,
        },
        {
          attack: 180,
          defenseInfantry: 240,
          defenseCavalry: 96,
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
        },
      ],
      researching: null,
      next: null,
    },
    {
      unit: 'CATAPULT',
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
        },
      ],
      researching: null,
      next: null,
    },
    {
      unit: 'NOBLE',
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
        },
      ],
      researching: null,
      next: null,
    },
    {
      unit: 'PALADIN',
      attackType: 'INFANTRY',
      minutesPerField: 14.0,
      carry: 0,
      level: 1,
      maxLevel: 1,
      stats: [
        {
          attack: 120,
          defenseInfantry: 150,
          defenseCavalry: 150,
        },
      ],
      researching: null,
      next: null,
    },
    {
      unit: 'BANNER_THIEF',
      attackType: 'INFANTRY',
      minutesPerField: 18.0,
      carry: 0,
      level: 1,
      maxLevel: 1,
      stats: [
        {
          attack: 0,
          defenseInfantry: 3,
          defenseCavalry: 3,
        },
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

export const paladin: Paladin = {
  maxSlots: 5,
  villagesPerSlot: 4,
  villages: 1,
  slots: 1,
  used: 0,
  nextSlotAtVillages: 5,
  minutesPerField: 14.0,
};

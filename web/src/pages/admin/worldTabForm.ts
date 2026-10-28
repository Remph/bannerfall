import type { AdminWorldSettings, WorldSettingsUpdate } from '../../api/queries/admin';
import { fromLocalInput, toLocalInput } from '../pregame/openingTime';
import { barbarianWallsRequest, barbarianWallsToForm, type BarbarianWallsForm } from './barbarianWallsForm';
import { victoryRequest, victoryToForm, type VictoryForm } from './victoryForm';
import { worldFromForm, worldToForm, type WorldForm } from './worldForm';

/**
 * The admin's World tab (1.6): every world setting in the setup wizard's five sections, one draft
 * and one save bar for all of them. The settings still go out in one PUT /api/admin/world-settings;
 * only the end date has its own endpoint (PUT /api/admin/world-end).
 */
export const worldSections = ['basics', 'pace', 'map', 'rules', 'victory'] as const;
export type WorldSection = (typeof worldSections)[number];

/** The section of a `?section=` value; Basics for anything unknown. */
export function parseSection(value: string | null): WorldSection {
  return worldSections.find((section) => section === value) ?? 'basics';
}

/** Everything the tab edits: the world settings, the victory and wall settings, and the end date (`datetime-local` text, '' = none). */
export type WorldTabForm = WorldForm & VictoryForm & BarbarianWallsForm & { endsAt: string };
export type WorldTabKey = keyof WorldTabForm;

export function worldTabToForm(settings: AdminWorldSettings): WorldTabForm {
  return {
    ...worldToForm(settings),
    ...victoryToForm(settings),
    ...barbarianWallsToForm(settings),
    endsAt: toLocalInput(settings.endsAt),
  };
}

/**
 * A row of the tab: one switch with its numbers, or one field. The save bar names changes by row
 * ("Regeln: Moral, Standarten stehlen"), so a switch and its numbers count once.
 */
export const worldRows = {
  name: { section: 'basics', keys: ['name'] },
  inviteCode: { section: 'basics', keys: ['inviteCode'] },
  villageNameLanguage: { section: 'basics', keys: ['villageNameLanguage'] },
  tutorial: { section: 'basics', keys: ['tutorialEnabled'] },
  speed: { section: 'pace', keys: ['speed'] },
  unitSpeed: { section: 'pace', keys: ['unitSpeed'] },
  beginnerProtectionHours: { section: 'pace', keys: ['beginnerProtectionHours'] },
  startResources: { section: 'pace', keys: ['startResources'] },
  buildQueue: { section: 'pace', keys: ['buildQueueSize', 'buildQueueMax'] },
  recruitQueueSize: { section: 'pace', keys: ['recruitQueueSize'] },
  cancelWindowSeconds: { section: 'pace', keys: ['cancelWindowSeconds'] },
  mapSpread: { section: 'map', keys: ['mapSpread'] },
  mapSize: { section: 'map', keys: ['mapSize'] },
  nobleMaxDistance: { section: 'map', keys: ['nobleMaxDistance'] },
  barbarianInitialCount: { section: 'map', keys: ['barbarianInitialCount'] },
  barbariansPerNewPlayer: { section: 'map', keys: ['barbariansPerNewPlayer'] },
  barbarianGrowth: { section: 'map', keys: ['barbarianGrowthEnabled'] },
  barbarianWalls: {
    section: 'map',
    keys: ['barbarianWallsEnabled', 'barbarianWallStartDays', 'barbarianWallMaxLevel', 'barbarianWallsRebuild'],
  },
  npcLordCount: { section: 'map', keys: ['npcLordCount'] },
  npcGuilds: { section: 'map', keys: ['npcGuildsEnabled', 'npcGuildMaxMembers'] },
  npcLearning: { section: 'map', keys: ['npcLearningEnabled'] },
  npcBanners: { section: 'map', keys: ['npcBannersEnabled'] },
  npcShipments: { section: 'map', keys: ['npcShipmentsEnabled'] },
  npcThinkMinutes: { section: 'map', keys: ['npcThinkMinutes'] },
  guilds: { section: 'rules', keys: ['guildsEnabled', 'guildMaxMembers'] },
  guildStartTogether: { section: 'rules', keys: ['guildStartTogether'] },
  guildBanners: { section: 'rules', keys: ['guildBannersEnabled'] },
  morale: { section: 'rules', keys: ['moraleEnabled', 'moraleFloor'] },
  nightBonus: {
    section: 'rules',
    keys: ['nightBonusEnabled', 'nightBonusStartHour', 'nightBonusEndHour', 'nightBonusFactor'],
  },
  unitLevels: { section: 'rules', keys: ['unitLevelsEnabled'] },
  archers: { section: 'rules', keys: ['archersEnabled'] },
  paladin: { section: 'rules', keys: ['paladinMaxSlots', 'paladinVillagesPerSlot'] },
  luckRange: { section: 'rules', keys: ['luckRange'] },
  banners: { section: 'rules', keys: ['bannersEnabled', 'bannerDropFactor'] },
  standardTheft: { section: 'rules', keys: ['standardTheftEnabled'] },
  noblePrice: { section: 'rules', keys: ['noblePriceStep'] },
  loyaltyRegenPerHour: { section: 'rules', keys: ['loyaltyRegenPerHour'] },
  market: { section: 'rules', keys: ['marketExchangeRatio'] },
  tradeMaxRatio: { section: 'rules', keys: ['tradeMaxRatio'] },
  automation: { section: 'rules', keys: ['automationVillages'] },
  victory: {
    section: 'victory',
    keys: ['victoryMode', 'dominationPercent', 'victoryHoldHours', 'keepCount', 'keepPercent'],
  },
  endsAt: { section: 'victory', keys: ['endsAt'] },
} as const satisfies Record<string, { section: WorldSection; keys: readonly WorldTabKey[] }>;

export type WorldRow = keyof typeof worldRows;

const rowNames = Object.keys(worldRows) as WorldRow[];

/** The draft's fields that differ from the stored settings, in the order of the form. */
export function changedKeys(stored: WorldTabForm, form: WorldTabForm): WorldTabKey[] {
  return (Object.keys(form) as WorldTabKey[]).filter((key) => form[key] !== stored[key]);
}

/** The rows with a changed field, in the order of the tab. */
export function changedRows(keys: readonly WorldTabKey[]): WorldRow[] {
  return rowNames.filter((row) => worldRows[row].keys.some((key) => keys.includes(key)));
}

/** The sections with a changed row. */
export function changedSections(rows: readonly WorldRow[]): WorldSection[] {
  return worldSections.filter((section) => rows.some((row) => worldRows[row].section === section));
}

/**
 * The settings request for the draft, or null while a field is not valid: the world settings card's,
 * victory card's and walls card's checks together (worldForm.ts, victoryForm.ts, barbarianWallsForm.ts).
 */
export function worldTabRequest(settings: AdminWorldSettings, form: WorldTabForm): WorldSettingsUpdate | null {
  const world = worldFromForm(form, settings.phase === 'PREGAME');
  const victory = victoryRequest(settings, form);
  const walls = barbarianWallsRequest(settings, form);
  if (world === null || victory === null || walls === null) {
    return null;
  }
  return {
    ...world,
    victoryMode: victory.victoryMode,
    dominationPercent: victory.dominationPercent,
    victoryHoldHours: victory.victoryHoldHours,
    keepCount: victory.keepCount,
    keepPercent: victory.keepPercent,
    barbarianWallsEnabled: walls.barbarianWallsEnabled,
    barbarianWallStartDays: walls.barbarianWallStartDays,
    barbarianWallMaxLevel: walls.barbarianWallMaxLevel,
    barbarianWallsRebuild: walls.barbarianWallsRebuild,
  };
}

/** The end date to send: null = none; undefined while the field holds no valid time. */
export function endsAtRequest(form: WorldTabForm): string | null | undefined {
  if (form.endsAt.trim() === '') {
    return null;
  }
  return fromLocalInput(form.endsAt) ?? undefined;
}

/**
 * The changed rows that keep the draft from being saved: each changed row is tried alone on top of
 * the stored settings. Fields that must agree share a row (the build queue's two sizes, the night's
 * start and end); should no row fail alone anyway, all changed rows are named.
 */
export function invalidRows(settings: AdminWorldSettings, form: WorldTabForm): WorldRow[] {
  const stored = worldTabToForm(settings);
  const rows = changedRows(changedKeys(stored, form));
  const fails = (candidate: WorldTabForm) =>
    worldTabRequest(settings, candidate) === null || endsAtRequest(candidate) === undefined;
  if (!fails(form)) {
    return [];
  }
  const alone = rows.filter((row) => {
    const patch = Object.fromEntries(worldRows[row].keys.map((key) => [key, form[key]]));
    return fails({ ...stored, ...patch });
  });
  return alone.length > 0 ? alone : rows;
}

/**
 * What a switch that stands for a number (0 = off) turns back on to: the stored number if it was on,
 * else the game's default (the paladin's 5 slots, the market's 1.5, the noble price's 25 %,
 * automation from the third village). The setup wizard switches the first three the same way.
 */
export const switchedOnDefaults = {
  paladinMaxSlots: '5',
  marketExchangeRatio: '1.5',
  noblePriceStep: '0.25',
  automationVillages: '3',
} as const;

export type NumberSwitch = keyof typeof switchedOnDefaults;

export function switchedOnValue(settings: AdminWorldSettings, key: NumberSwitch): string {
  return settings[key] > 0 ? String(settings[key]) : switchedOnDefaults[key];
}

/** A number switch is on while its number is above 0 (an unfinished entry counts as on, so the field stays). */
export function numberSwitchOn(value: string): boolean {
  return value.trim() === '' || Number(value) !== 0;
}

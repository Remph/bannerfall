import type { AdminWorldSettings, WorldSettingsUpdate } from '../../api/queries/admin';

/** The language of the names the game gives villages (v1.3): "Ben's village" or "Dorf von Ben". */
export type VillageNameLanguage = AdminWorldSettings['villageNameLanguage'];

export const villageNameLanguages: readonly VillageNameLanguage[] = ['EN', 'DE'];

/** How far apart the sides start (1.6, 02-game-rules §2). */
export type MapSpread = AdminWorldSettings['mapSpread'];

export const mapSpreads: readonly MapSpread[] = ['CLOSE', 'NORMAL', 'WIDE', 'VAST'];

/** The factors of the server's `MapSpread`: every placement and NPC radius is multiplied by it. */
const spreadFactors: Record<MapSpread, number> = { CLOSE: 0.8, NORMAL: 1.0, WIDE: 1.3, VAST: 1.6 };

/**
 * The form with another spread and the map size and noble range that go with it (100 and 70 fields
 * for NORMAL, scaled like on the server); the host can still change both afterwards.
 */
export function withSpread<F extends WorldForm>(form: F, mapSpread: MapSpread): F {
  const factor = spreadFactors[mapSpread];
  return {
    ...form,
    mapSpread,
    mapSize: String(Math.round(100 * factor)),
    nobleMaxDistance: String(Math.round(70 * factor)),
  };
}

/** The world settings as the form holds them: numbers as the text in the field. */
export interface WorldForm {
  name: string;
  inviteCode: string;
  speed: string;
  unitSpeed: string;
  startResources: string;
  luckRange: string;
  beginnerProtectionHours: string;
  nobleMaxDistance: string;
  loyaltyRegenPerHour: string;
  buildQueueSize: string;
  buildQueueMax: string;
  recruitQueueSize: string;
  cancelWindowSeconds: string;
  barbariansPerNewPlayer: string;
  tradeMaxRatio: string;
  npcThinkMinutes: string;
  /** Pre-game only (M14g): fixed once the world is open. */
  mapSize: string;
  mapSpread: MapSpread;
  barbarianInitialCount: string;
  npcLordCount: string;
  barbarianGrowthEnabled: boolean;
  /** Guilds (M15a): can be founded and joined; the most members one may have. */
  guildsEnabled: boolean;
  guildMaxMembers: string;
  /** 1.6 (02-game-rules §2): guild mates start next to each other at the opening and on a respawn. */
  guildStartTogether: boolean;
  /** Lord guilds (09-npc-lords §13): NPC lords form guilds; the most lords one may have. */
  npcGuildsEnabled: boolean;
  npcGuildMaxMembers: string;
  /** Unit levels (M30): the smithy can raise units; off = every unit has its base values. */
  unitLevelsEnabled: boolean;
  /** Archers (1.6, 02-game-rules §5.6): the archer and the mounted archer can be recruited. */
  archersEnabled: boolean;
  /** Paladin (M31): villages for each further slot, and the most slots (0 = no paladin). */
  paladinVillagesPerSlot: string;
  paladinMaxSlots: string;
  /** 1.6 (02-game-rules §5.7): how much dearer each village and noble beyond four makes the next noble; 0 = flat. */
  noblePriceStep: string;
  /** Banners (M32): the Banner Hall and equipping work; the drop chance as a factor (0 = no drops, 1 = normal). */
  bannersEnabled: boolean;
  bannerDropFactor: string;
  /** 1.4 (02-game-rules §21.18): defenders' thieves can take the standard of an attack they beat. */
  standardTheftEnabled: boolean;
  /** 1.4 (09-npc-lords §14): NPC lords find, fly and fuse banners like players. */
  npcBannersEnabled: boolean;
  /** 1.6 (09-npc-lords §15): NPC lords learn from the attacks on them and from human wins. */
  npcLearningEnabled: boolean;
  /** 1.6 (09-npc-lords §5.5): NPC lords ship what would overflow to their own villages. */
  npcShipmentsEnabled: boolean;
  /** Trading with the market (v1.3): what 1 of another resource costs; 0 = off, else 1 to 10. */
  marketExchangeRatio: string;
  /** Default village names (v1.3); a switch renames the villages that still carry one. */
  villageNameLanguage: VillageNameLanguage;
  /** Night bonus (1.4, 02-game-rules §8.1): owned villages defend more strongly at night. */
  nightBonusEnabled: boolean;
  nightBonusStartHour: string;
  nightBonusEndHour: string;
  nightBonusFactor: string;
  /** Morale (1.6, 02-game-rules §8.2): attacks on much smaller players hit weaker, down to the floor (percent). */
  moraleEnabled: boolean;
  moraleFloor: string;
  /** Village automation (1.6, 02-game-rules §25): from this many villages on; 0 = off. */
  automationVillages: string;
}

export function worldToForm(settings: AdminWorldSettings): WorldForm {
  return {
    name: settings.name,
    inviteCode: settings.inviteCode,
    speed: String(settings.speed),
    unitSpeed: String(settings.unitSpeed),
    startResources: String(settings.startResources),
    luckRange: String(settings.luckRange),
    beginnerProtectionHours: String(settings.beginnerProtectionHours),
    nobleMaxDistance: String(settings.nobleMaxDistance),
    loyaltyRegenPerHour: String(settings.loyaltyRegenPerHour),
    buildQueueSize: String(settings.buildQueueSize),
    buildQueueMax: String(settings.buildQueueMax),
    recruitQueueSize: String(settings.recruitQueueSize),
    cancelWindowSeconds: String(settings.cancelWindowSeconds),
    barbariansPerNewPlayer: String(settings.barbariansPerNewPlayer),
    tradeMaxRatio: String(settings.tradeMaxRatio),
    npcThinkMinutes: String(settings.npcThinkMinutes),
    mapSize: String(settings.mapSize),
    mapSpread: settings.mapSpread,
    barbarianInitialCount: String(settings.barbarianInitialCount),
    npcLordCount: String(settings.npcLordCount),
    barbarianGrowthEnabled: settings.barbarianGrowthEnabled,
    guildsEnabled: settings.guildsEnabled,
    guildMaxMembers: String(settings.guildMaxMembers),
    guildStartTogether: settings.guildStartTogether,
    npcGuildsEnabled: settings.npcGuildsEnabled,
    npcGuildMaxMembers: String(settings.npcGuildMaxMembers),
    unitLevelsEnabled: settings.unitLevelsEnabled,
    archersEnabled: settings.archersEnabled,
    paladinVillagesPerSlot: String(settings.paladinVillagesPerSlot),
    paladinMaxSlots: String(settings.paladinMaxSlots),
    noblePriceStep: String(settings.noblePriceStep),
    bannersEnabled: settings.bannersEnabled,
    bannerDropFactor: String(settings.bannerDropFactor),
    standardTheftEnabled: settings.standardTheftEnabled,
    npcBannersEnabled: settings.npcBannersEnabled,
    npcLearningEnabled: settings.npcLearningEnabled,
    npcShipmentsEnabled: settings.npcShipmentsEnabled,
    marketExchangeRatio: String(settings.marketExchangeRatio),
    villageNameLanguage: settings.villageNameLanguage,
    nightBonusEnabled: settings.nightBonusEnabled,
    nightBonusStartHour: String(settings.nightBonusStartHour),
    nightBonusEndHour: String(settings.nightBonusEndHour),
    nightBonusFactor: String(settings.nightBonusFactor),
    moraleEnabled: settings.moraleEnabled,
    moraleFloor: String(settings.moraleFloor),
    automationVillages: String(settings.automationVillages),
  };
}

/** A field that must be a finite number in [min, max] (a whole number if `integer`); null if it isn't. */
function number(text: string, min: number, max: number, integer = false): number | null {
  if (text.trim() === '') {
    return null;
  }
  const value = Number(text);
  if (!Number.isFinite(value) || value < min || value > max || (integer && !Number.isInteger(value))) {
    return null;
  }
  return value;
}

/** Text of 1 to 64 characters that isn't blank (the server's @NotBlank @Size(max = 64)). */
function text(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length >= 1 && trimmed.length <= 64 ? trimmed : null;
}

/**
 * The request to send, or null while a field is not valid: the same ranges as the server's
 * `WorldSettingsUpdateRequest`, the queue's paid slots must not be fewer than the normal ones and
 * the market's exchange ratio is 0 (off) or at least 1, the night must not start and end at the same
 * hour. Map size,
 * barbarians and NPC lords are only sent (and checked) in the pre-game: later they are fixed.
 */
export function worldFromForm(form: WorldForm, pregame = false): WorldSettingsUpdate | null {
  const name = text(form.name);
  const inviteCode = text(form.inviteCode);
  const speed = number(form.speed, 0.1, 100);
  const unitSpeed = number(form.unitSpeed, 0.1, 100);
  const startResources = number(form.startResources, 0, 1_000_000, true);
  const luckRange = number(form.luckRange, 0, 1);
  const beginnerProtectionHours = number(form.beginnerProtectionHours, 0, 720, true);
  const nobleMaxDistance = number(form.nobleMaxDistance, 1, 1000, true);
  const loyaltyRegenPerHour = number(form.loyaltyRegenPerHour, 0, 100);
  const buildQueueSize = number(form.buildQueueSize, 1, 20, true);
  const buildQueueMax = number(form.buildQueueMax, 1, 30, true);
  const recruitQueueSize = number(form.recruitQueueSize, 1, 20, true);
  const cancelWindowSeconds = number(form.cancelWindowSeconds, 0, 86_400, true);
  const barbariansPerNewPlayer = number(form.barbariansPerNewPlayer, 0, 20, true);
  const tradeMaxRatio = number(form.tradeMaxRatio, 1, 10);
  const npcThinkMinutes = number(form.npcThinkMinutes, 1, 1440, true);
  const mapSize = number(form.mapSize, 20, 500, true);
  const barbarianInitialCount = number(form.barbarianInitialCount, 0, 5000, true);
  const npcLordCount = number(form.npcLordCount, 0, 50, true);
  const guildMaxMembers = number(form.guildMaxMembers, 2, 50, true);
  const npcGuildMaxMembers = number(form.npcGuildMaxMembers, 2, 50, true);
  const paladinVillagesPerSlot = number(form.paladinVillagesPerSlot, 1, 100, true);
  const paladinMaxSlots = number(form.paladinMaxSlots, 0, 20, true);
  const noblePriceStep = number(form.noblePriceStep, 0, 1);
  const bannerDropFactor = number(form.bannerDropFactor, 0, 3);
  const marketExchangeRatio = number(form.marketExchangeRatio, 0, 10);
  const nightBonusStartHour = number(form.nightBonusStartHour, 0, 23, true);
  const nightBonusEndHour = number(form.nightBonusEndHour, 0, 23, true);
  const nightBonusFactor = number(form.nightBonusFactor, 1.1, 3);
  const moraleFloor = number(form.moraleFloor, 30, 100, true);
  const automationVillages = number(form.automationVillages, 0, 100, true);
  if (pregame && (mapSize === null || barbarianInitialCount === null || npcLordCount === null)) {
    return null;
  }
  if (
    name === null ||
    inviteCode === null ||
    speed === null ||
    unitSpeed === null ||
    startResources === null ||
    luckRange === null ||
    beginnerProtectionHours === null ||
    nobleMaxDistance === null ||
    loyaltyRegenPerHour === null ||
    buildQueueSize === null ||
    buildQueueMax === null ||
    recruitQueueSize === null ||
    cancelWindowSeconds === null ||
    barbariansPerNewPlayer === null ||
    tradeMaxRatio === null ||
    npcThinkMinutes === null ||
    guildMaxMembers === null ||
    npcGuildMaxMembers === null ||
    paladinVillagesPerSlot === null ||
    paladinMaxSlots === null ||
    noblePriceStep === null ||
    bannerDropFactor === null ||
    marketExchangeRatio === null ||
    (marketExchangeRatio > 0 && marketExchangeRatio < 1) ||
    buildQueueMax < buildQueueSize ||
    nightBonusStartHour === null ||
    nightBonusEndHour === null ||
    nightBonusStartHour === nightBonusEndHour ||
    nightBonusFactor === null ||
    moraleFloor === null ||
    automationVillages === null
  ) {
    return null;
  }
  return {
    name,
    inviteCode,
    speed,
    unitSpeed,
    startResources,
    luckRange,
    beginnerProtectionHours,
    nobleMaxDistance,
    loyaltyRegenPerHour,
    buildQueueSize,
    buildQueueMax,
    recruitQueueSize,
    cancelWindowSeconds,
    barbariansPerNewPlayer,
    tradeMaxRatio,
    npcThinkMinutes,
    barbarianGrowthEnabled: form.barbarianGrowthEnabled,
    guildsEnabled: form.guildsEnabled,
    guildMaxMembers,
    guildStartTogether: form.guildStartTogether,
    npcGuildsEnabled: form.npcGuildsEnabled,
    npcGuildMaxMembers,
    unitLevelsEnabled: form.unitLevelsEnabled,
    archersEnabled: form.archersEnabled,
    paladinVillagesPerSlot,
    paladinMaxSlots,
    noblePriceStep,
    bannersEnabled: form.bannersEnabled,
    bannerDropFactor,
    standardTheftEnabled: form.standardTheftEnabled,
    npcBannersEnabled: form.npcBannersEnabled,
    npcLearningEnabled: form.npcLearningEnabled,
    npcShipmentsEnabled: form.npcShipmentsEnabled,
    marketExchangeRatio,
    villageNameLanguage: form.villageNameLanguage,
    nightBonusEnabled: form.nightBonusEnabled,
    nightBonusStartHour,
    nightBonusEndHour,
    nightBonusFactor,
    moraleEnabled: form.moraleEnabled,
    moraleFloor,
    automationVillages,
    // null = keep: the wall settings have their own card (BarbarianWallsCard, M29)
    barbarianWallsEnabled: null,
    barbarianWallStartDays: null,
    barbarianWallMaxLevel: null,
    barbarianWallsRebuild: null,
    // null = keep: the victory settings have their own card (VictoryCard, v1.3)
    victoryMode: null,
    dominationPercent: null,
    victoryHoldHours: null,
    keepCount: null,
    keepPercent: null,
    // null = keep: in an open world these are fixed
    mapSize: pregame ? mapSize : null,
    mapSpread: pregame ? form.mapSpread : null,
    barbarianInitialCount: pregame ? barbarianInitialCount : null,
    npcLordCount: pregame ? npcLordCount : null,
  };
}

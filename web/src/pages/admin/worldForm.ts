import type { AdminWorldSettings, WorldSettingsUpdate } from '../../api/queries/admin';

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
  barbarianInitialCount: string;
  npcLordCount: string;
  barbarianGrowthEnabled: boolean;
  /** Guilds (M15a): can be founded and joined; the most members one may have. */
  guildsEnabled: boolean;
  guildMaxMembers: string;
  /** Unit levels (M30): the smithy can raise units; off = every unit has its base values. */
  unitLevelsEnabled: boolean;
  /** Paladin (M31): villages for each further slot, and the most slots (0 = no paladin). */
  paladinVillagesPerSlot: string;
  paladinMaxSlots: string;
  /** Banners (M32): the Banner Hall and equipping work; the drop chance as a factor (0 = no drops, 1 = normal). */
  bannersEnabled: boolean;
  bannerDropFactor: string;
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
    barbarianInitialCount: String(settings.barbarianInitialCount),
    npcLordCount: String(settings.npcLordCount),
    barbarianGrowthEnabled: settings.barbarianGrowthEnabled,
    guildsEnabled: settings.guildsEnabled,
    guildMaxMembers: String(settings.guildMaxMembers),
    unitLevelsEnabled: settings.unitLevelsEnabled,
    paladinVillagesPerSlot: String(settings.paladinVillagesPerSlot),
    paladinMaxSlots: String(settings.paladinMaxSlots),
    bannersEnabled: settings.bannersEnabled,
    bannerDropFactor: String(settings.bannerDropFactor),
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
 * `WorldSettingsUpdateRequest`, and the paid slots must not be fewer than the normal ones. Map size,
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
  const paladinVillagesPerSlot = number(form.paladinVillagesPerSlot, 1, 100, true);
  const paladinMaxSlots = number(form.paladinMaxSlots, 0, 20, true);
  const bannerDropFactor = number(form.bannerDropFactor, 0, 3);
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
    paladinVillagesPerSlot === null ||
    paladinMaxSlots === null ||
    bannerDropFactor === null ||
    buildQueueMax < buildQueueSize
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
    unitLevelsEnabled: form.unitLevelsEnabled,
    paladinVillagesPerSlot,
    paladinMaxSlots,
    bannersEnabled: form.bannersEnabled,
    bannerDropFactor,
    // null = keep: the wall settings have their own card (BarbarianWallsCard, M29)
    barbarianWallsEnabled: null,
    barbarianWallStartDays: null,
    barbarianWallMaxLevel: null,
    barbarianWallsRebuild: null,
    // null = keep: in an open world these are fixed
    mapSize: pregame ? mapSize : null,
    barbarianInitialCount: pregame ? barbarianInitialCount : null,
    npcLordCount: pregame ? npcLordCount : null,
  };
}

import type { AdminWorldSettings, NpcDifficulty, WorldSettingsUpdate } from '../../api/queries/admin';
import type { VictoryMode } from '../../api/queries/world';
import { withSpread, worldFromForm, worldToForm, type WorldForm } from '../admin/worldForm';

/** The wizard's steps in order. */
export const setupSteps = ['start', 'basics', 'pace', 'map', 'rules', 'victory', 'opening'] as const;
export type SetupStep = (typeof setupSteps)[number];

/** The starting points on the wizard's first screen (1.6, guided world setup). */
export const setupPresets = ['friends', 'long', 'coop', 'solo', 'custom'] as const;
export type SetupPreset = (typeof setupPresets)[number];

/** Everything the wizard asks: the world form plus the settings that have their own cards on the admin page. */
export interface SetupForm extends WorldForm {
  npcDifficulty: NpcDifficulty;
  barbarianWallsEnabled: boolean;
  victoryMode: VictoryMode;
  dominationPercent: string;
  victoryHoldHours: string;
  keepCount: string;
  /** `datetime-local` text, empty = "set it later". */
  opensAt: string;
  endsAt: string;
}

export function setupFromSettings(settings: AdminWorldSettings): SetupForm {
  return {
    ...worldToForm(settings),
    npcDifficulty: settings.npcDifficulty,
    barbarianWallsEnabled: settings.barbarianWallsEnabled,
    victoryMode: settings.victoryMode,
    dominationPercent: String(settings.dominationPercent),
    victoryHoldHours: String(settings.victoryHoldHours),
    keepCount: String(settings.keepCount),
    opensAt: instantToLocal(settings.opensAt ?? null),
    endsAt: instantToLocal(settings.endsAt ?? null),
  };
}

const INVITE_ALPHABET = 'abcdefghjkmnpqrstuvwxyz23456789';
const INVITE_LENGTH = 10;

/** A random invite code without look-alike letters (no i, l, o, 0, 1). */
export function newInviteCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(INVITE_LENGTH));
  return Array.from(bytes, (byte) => INVITE_ALPHABET[byte % INVITE_ALPHABET.length]).join('');
}

/** An instant as the value of a `datetime-local` field in the browser's time zone ("2026-11-07T18:00"). */
export function instantToLocal(instant: string | null): string {
  if (instant === null) {
    return '';
  }
  const date = new Date(instant);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** A whole number in [min, max], or null. */
function whole(text: string, min: number, max: number): number | null {
  const value = Number(text);
  return text.trim() !== '' && Number.isInteger(value) && value >= min && value <= max ? value : null;
}

/**
 * The request the wizard saves, or null while a value is out of range: the world form of the admin
 * page (pre-game: map size, barbarians and lords included) plus the walls switch and the victory
 * settings, which have their own cards there. The same ranges as `WorldSettingsUpdateRequest`.
 */
export function setupToRequest(form: SetupForm): WorldSettingsUpdate | null {
  const world = worldFromForm(form, true);
  const dominationPercent = whole(form.dominationPercent, 10, 100);
  const victoryHoldHours = whole(form.victoryHoldHours, 1, 720);
  const keepCount = whole(form.keepCount, 1, 12);
  if (world === null || dominationPercent === null || victoryHoldHours === null || keepCount === null) {
    return null;
  }
  return {
    ...world,
    barbarianWallsEnabled: form.barbarianWallsEnabled,
    victoryMode: form.victoryMode,
    dominationPercent,
    victoryHoldHours,
    keepCount,
  };
}

/** A `datetime-local` value as an instant, or null for an empty field. */
export function localToInstant(text: string): string | null {
  if (text.trim() === '') {
    return null;
  }
  const time = new Date(text).getTime();
  return Number.isNaN(time) ? null : new Date(time).toISOString();
}

/**
 * What each preset changes; Custom keeps what is stored (today's defaults on a fresh world). A
 * preset's map spread brings its map size and noble range (`withSpread`); the rest keeps what is
 * stored (archers off, morale on at 30 %, noble price +25 % on a fresh world).
 */
const presetValues: Record<SetupPreset, Partial<SetupForm>> = {
  friends: {
    speed: '6',
    unitSpeed: '1',
    beginnerProtectionHours: '36',
    mapSpread: 'NORMAL',
    barbarianInitialCount: '30',
    npcLordCount: '4',
    npcDifficulty: 'NORMAL',
    npcGuildsEnabled: false,
    guildsEnabled: true,
    guildStartTogether: true,
    nightBonusEnabled: true,
    victoryMode: 'DOMINATION',
    dominationPercent: '60',
    victoryHoldHours: '48',
    soloMode: false,
  },
  long: {
    speed: '3',
    unitSpeed: '1',
    beginnerProtectionHours: '72',
    mapSpread: 'WIDE',
    barbarianInitialCount: '60',
    npcLordCount: '6',
    npcDifficulty: 'NORMAL',
    npcGuildsEnabled: true,
    npcGuildMaxMembers: '3',
    guildsEnabled: true,
    nightBonusEnabled: true,
    victoryMode: 'NONE',
    soloMode: false,
  },
  coop: {
    speed: '4',
    unitSpeed: '1',
    beginnerProtectionHours: '48',
    mapSpread: 'NORMAL',
    barbarianInitialCount: '50',
    npcLordCount: '20',
    npcDifficulty: 'HARD',
    npcGuildsEnabled: true,
    npcGuildMaxMembers: '10',
    npcBannersEnabled: true,
    guildsEnabled: true,
    guildMaxMembers: '4',
    guildStartTogether: true,
    nightBonusEnabled: true,
    victoryMode: 'DOMINATION',
    dominationPercent: '60',
    victoryHoldHours: '48',
    soloMode: false,
  },
  /**
   * 2.1 (12-solo-and-allies §1, §3): you alone against the lords. The world sleeps while you are away,
   * so the lords keep no quiet hours (the server drops them in a solo world) and there is no night
   * bonus: both protect a real night, and a sleeping world already does.
   */
  solo: {
    speed: '4',
    unitSpeed: '1',
    beginnerProtectionHours: '48',
    mapSpread: 'NORMAL',
    barbarianInitialCount: '40',
    npcLordCount: '8',
    npcDifficulty: 'NORMAL',
    npcGuildsEnabled: true,
    npcGuildMaxMembers: '3',
    guildsEnabled: true,
    guildStartTogether: true,
    nightBonusEnabled: false,
    victoryMode: 'DOMINATION',
    dominationPercent: '60',
    victoryHoldHours: '48',
    soloMode: true,
  },
  custom: {},
};

/** The invite code a fresh world starts with (`WorldSettings`), known to everybody who read the docs. */
export const DEFAULT_INVITE_CODE = 'change-me';

export function applyPreset(base: SetupForm, preset: SetupPreset): SetupForm {
  const values = presetValues[preset];
  const map = values.mapSpread === undefined ? base : withSpread(base, values.mapSpread);
  const form = { ...map, ...values };
  // The solo world on a PC has nobody to invite: a fresh code instead of the well-known default, so
  // nobody else can register there (2.1 security review, finding 2). A code the host chose stays.
  const stored = base.inviteCode.trim();
  if (preset === 'solo' && (stored === '' || stored === DEFAULT_INVITE_CODE)) {
    return { ...form, inviteCode: newInviteCode() };
  }
  return form;
}

/** The three facts a preset card shows: speed, protection, lords. */
export function presetFacts(base: SetupForm, preset: SetupPreset) {
  const form = applyPreset(base, preset);
  return {
    speed: form.speed,
    protectionHours: form.beginnerProtectionHours,
    lords: form.npcLordCount,
  };
}

/**
 * When nobles can come at a speed, in real days: scaled from the NPC simulation at speed 3
 * (academy around day 12 to 15, 09-npc-lords §11). null for no or a bad speed.
 */
export function noblesFromDays(speedText: string): [number, number] | null {
  const speed = Number(speedText);
  if (!Number.isFinite(speed) || speed <= 0) {
    return null;
  }
  return [
    Math.max(1, Math.round(NOBLES_EARLIEST_DAY_AT_SPEED_1 / speed)),
    Math.max(1, Math.round(NOBLES_LATEST_DAY_AT_SPEED_1 / speed)),
  ];
}

/** The simulation's day 12 to 15 at speed 3, as days at speed 1. */
const NOBLES_EARLIEST_DAY_AT_SPEED_1 = 36;
const NOBLES_LATEST_DAY_AT_SPEED_1 = 45;

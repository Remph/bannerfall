import type { IconName } from '../components/Icon';

/** The game's sections, in the order of the desktop tab row (05-frontend §3). */
export type SectionKey =
  | 'village'
  | 'townHall'
  | 'recruit'
  | 'map'
  | 'rallyPoint'
  | 'farm'
  | 'market'
  | 'reports'
  | 'messages'
  | 'ranking'
  | 'achievements'
  | 'guild';

export interface Section {
  key: SectionKey;
  icon: IconName;
  /** Where the section's screen lives. Missing = not built yet, shown as "coming soon". */
  path?: (villageId: number) => string;
  /**
   * The same for every village, so it works without one too: a player whose last village was
   * conquered still reads his reports (M11).
   */
  global?: boolean;
}

// Each milestone that builds a screen adds its `path` here.
export const sections: Record<SectionKey, Section> = {
  village: { key: 'village', icon: 'village', path: (villageId) => `/v/${villageId}` },
  townHall: { key: 'townHall', icon: 'HEADQUARTERS', path: (villageId) => `/v/${villageId}/hq` },
  // opens the barracks (/v/:id/recruit/barracks); the link stays active for all recruiting buildings
  recruit: { key: 'recruit', icon: 'BARRACKS', path: (villageId) => `/v/${villageId}/recruit` },
  map: { key: 'map', icon: 'map', path: (villageId) => `/v/${villageId}/map` },
  rallyPoint: { key: 'rallyPoint', icon: 'troops', path: (villageId) => `/v/${villageId}/rally` },
  // the farm list of the village (M18)
  farm: { key: 'farm', icon: 'FARM', path: (villageId) => `/v/${villageId}/farm` },
  market: { key: 'market', icon: 'MARKET', path: (villageId) => `/v/${villageId}/market` },
  // the same for every village (the argument is ignored)
  reports: { key: 'reports', icon: 'reports', path: () => '/reports', global: true },
  // private messages (M17): like the reports, the same for every village
  messages: { key: 'messages', icon: 'mail', path: () => '/messages', global: true },
  ranking: { key: 'ranking', icon: 'RALLY_POINT', path: () => '/ranking', global: true },
  // honours without any effect on the game (M16): the same for every village
  achievements: { key: 'achievements', icon: 'medal', path: () => '/achievements', global: true },
  // only shown while guilds are on, or to a member of one (M15a), see `visibleSections`
  guild: { key: 'guild', icon: 'guild', path: () => '/guild', global: true },
};

/** Desktop: all sections in one tab row. */
export const desktopSections: readonly SectionKey[] = [
  'village',
  'townHall',
  'recruit',
  'map',
  'rallyPoint',
  'farm',
  'market',
  'reports',
  'messages',
  'ranking',
  'achievements',
  'guild',
];

/** Phone: four tabs plus "More"; the rest is in the More sheet. */
export const phoneTabs: readonly SectionKey[] = ['village', 'map', 'rallyPoint', 'reports'];
export const moreSections: readonly SectionKey[] = [
  'townHall',
  'recruit',
  'farm',
  'market',
  'messages',
  'ranking',
  'achievements',
  'guild',
];

/** The sections without the ones that exist only if the host switched them on. */
export function visibleSections(keys: readonly SectionKey[], showGuild: boolean): SectionKey[] {
  return keys.filter((key) => key !== 'guild' || showGuild);
}

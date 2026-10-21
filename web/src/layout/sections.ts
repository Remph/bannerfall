import type { IconName } from '../components/Icon';
import { recruitPath } from '../pages/recruit/recruitPaths';

/** The game's sections, in the order of the desktop tab row (05-frontend §3). */
export type SectionKey =
  | 'village'
  | 'townHall'
  | 'recruit'
  | 'bannerHall'
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
  // paladin training, banners and standards (1.4): only while the world has paladins or banners, see `visibleSections`
  bannerHall: { key: 'bannerHall', icon: 'BANNER_HALL', path: (villageId) => bannerHallPath(villageId, true) },
  map: { key: 'map', icon: 'map', path: (villageId) => `/v/${villageId}/map` },
  rallyPoint: { key: 'rallyPoint', icon: 'troops', path: (villageId) => `/v/${villageId}/rally` },
  // the farm list of the village (M18)
  farm: { key: 'farm', icon: 'FARM', path: (villageId) => `/v/${villageId}/farm` },
  market: { key: 'market', icon: 'MARKET', path: (villageId) => `/v/${villageId}/market` },
  // the same for every village (the argument is ignored)
  reports: { key: 'reports', icon: 'reports', path: () => '/reports', global: true },
  // private messages (M17): like the reports, the same for every village
  messages: { key: 'messages', icon: 'mail', path: () => '/messages', global: true },
  ranking: { key: 'ranking', icon: 'ranking', path: () => '/ranking', global: true },
  // honours without any effect on the game (M16): the same for every village
  achievements: { key: 'achievements', icon: 'medal', path: () => '/achievements', global: true },
  // only shown while guilds are on, or to a member of one (M15a), see `visibleSections`
  guild: { key: 'guild', icon: 'guild', path: () => '/guild', global: true },
};

/**
 * Desktop: all sections in one tab row (1.4 order): the village's buildings, then its war pages
 * (map, rally point, farm list), then the pages that are the same for every village.
 */
export const desktopSections: readonly SectionKey[] = [
  'village',
  'townHall',
  'recruit',
  'bannerHall',
  'market',
  'map',
  'rallyPoint',
  'farm',
  'reports',
  'messages',
  'ranking',
  'achievements',
  'guild',
];

/** Phone: four tabs plus "More"; the rest is in the More sheet. */
export const phoneTabs: readonly SectionKey[] = ['village', 'map', 'rallyPoint', 'reports'];

/**
 * The More sheet in the desktop's two groups (1.4): the village's pages, then the player's. The
 * chat (a sheet, not a section) follows the messages.
 */
export const moreGroups: Readonly<Record<'village' | 'player', readonly SectionKey[]>> = {
  village: ['townHall', 'recruit', 'bannerHall', 'market', 'farm'],
  player: ['messages', 'ranking', 'achievements', 'guild'],
};

/** Which of the sections that exist only if the host switched them on are in the navigation. */
export interface OptionalSections {
  guild: boolean;
  bannerHall: boolean;
}

/** The sections without the ones the host didn't switch on. */
export function visibleSections(keys: readonly SectionKey[], shown: OptionalSections): SectionKey[] {
  return keys.filter((key) => (key !== 'guild' || shown.guild) && (key !== 'bannerHall' || shown.bannerHall));
}

/**
 * Where the Banner Hall item leads: the hall's page, or, in a world without banners where the hall
 * only trains the paladin, the recruit screen's hall tab, like the building in the village picture
 * (1.4, `buildingTarget`).
 */
export function bannerHallPath(villageId: number, bannersOn: boolean): string {
  return bannersOn ? `/v/${villageId}/banners` : recruitPath(villageId, 'BANNER_HALL');
}

/**
 * What a page lights in the navigation (1.5.1): a section, the own profile, which has its own entry,
 * or one of the settings menu's pages (the gear on desktop, More on phones).
 */
export type NavEntry = SectionKey | 'profile' | 'settings';

/**
 * The navigation entry a page belongs to (1.5.1): its own section, or for a page without an entry
 * the section its back link leads to, as the warehouse and smithy light the village overview. Other
 * players' profiles and the statistics light the ranking, another guild's page the guild; the own
 * profile its own entry. Nothing for anything unknown.
 * `bannersOn`: without banners the Banner Hall item opens the recruit screen's hall tab (1.4).
 */
export function navEntry(pathname: string, ownPlayerId: number, bannersOn: boolean): NavEntry | undefined {
  const village = /^\/v\/\d+(?:\/([^/]+)(?:\/([^/]+))?)?\/?/.exec(pathname);
  if (village !== null) {
    const [, page, sub] = village;
    switch (page) {
      case undefined:
      case 'warehouse':
      case 'smithy':
        return 'village';
      // the automation page belongs to the Town Hall (1.6)
      case 'hq':
      case 'automation':
        return 'townHall';
      case 'recruit':
        return !bannersOn && sub === 'banner_hall' ? 'bannerHall' : 'recruit';
      case 'banners':
        return 'bannerHall';
      case 'map':
        return 'map';
      case 'rally':
        return 'rallyPoint';
      case 'farm':
        return 'farm';
      case 'market':
        return 'market';
      default:
        return undefined;
    }
  }
  const [, first, second] = pathname.split('/');
  switch (first) {
    case 'reports':
      return 'reports';
    case 'messages':
      return 'messages';
    case 'ranking':
    case 'statistics':
      return 'ranking';
    case 'players':
      return second === String(ownPlayerId) ? 'profile' : 'ranking';
    case 'achievements':
      return 'achievements';
    case 'guild':
    case 'guilds':
      return 'guild';
    case 'notifications':
    case 'password':
    case 'about':
    case 'privacy':
    case 'admin':
      return 'settings';
    default:
      return undefined;
  }
}

import type { BuildingEffect } from '../api/queries/buildings';
import type { EndReason, VictoryMode } from '../api/queries/world';
import { tierName } from '../game/banners';
import type { BannerType, BuildingType, UnitType } from '../game/gameData';
import type { MarkerKind } from '../game/mapMovements';
import type { VillageKind } from '../game/mapView';
import type { ReportKind, TheftResult } from '../game/reports';
import type { CommandKind, MovementKind } from '../game/troops';
import type { WorldRow, WorldSection } from '../pages/admin/worldTabForm';
import type { MapActionKey } from '../pages/map/mapActions';
import type { RankingKind } from '../api/queries/ranking';
import type { PushKind } from '../push/pushMessage';
import type { Palette } from '../theme/theme';

/** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th, 21st (the automation's "from your 3rd village"). */
function ordinal(n: number): string {
  const hundreds = n % 100;
  const teen = hundreds >= 11 && hundreds <= 13;
  const suffix = teen ? 'th' : (['th', 'st', 'nd', 'rd'][n % 10] ?? 'th');
  return `${n}${suffix}`;
}

/**
 * English texts – the source dictionary. de.ts must have exactly the same keys (the compiler checks it).
 * Sections follow docs/plan/05-frontend.md §9.
 */
export const en = {
  common: {
    appName: 'Bannerfall',
    loading: 'Loading…',
    retry: 'Try again',
    /** Label of the language switch: the name of the *other* language. */
    otherLanguage: 'Deutsch',
    switchLanguage: 'Switch language',
    close: 'Close',
    save: 'Save',
    cancel: 'Cancel',
    comingSoon: 'coming soon',
  },
  auth: {
    bannedUntil: (when: string, reason: string | null) =>
      `This account is banned until ${when}.${reason === null ? '' : ` Reason: ${reason}`}`,
    bannedForGood: (reason: string | null) =>
      `This account is banned for good.${reason === null ? '' : ` Reason: ${reason}`}`,
    loginTitle: 'Log in',
    registerTitle: 'Create account',
    username: 'Username',
    password: 'Password',
    inviteCode: 'Invite code',
    usernameHint: '3–24 characters: letters, digits, _ and -',
    passwordHint: 'At least 8 characters',
    inviteCodeHint: 'You get it from whoever invited you.',
    loginButton: 'Log in',
    loggingIn: 'Logging in…',
    registerButton: 'Create account',
    registering: 'Creating account…',
    noAccount: 'No account yet?',
    toRegister: 'Create one',
    haveAccount: 'Already have an account?',
    toLogin: 'Log in',
    logout: 'Log out',
    validation: {
      required: 'Please fill in this field.',
      usernameFormat: 'Use 3–24 characters: letters, digits, _ and -.',
      passwordLength: 'The password needs 8 to 128 characters.',
    },
  },
  /** Logging in through the account server (1.5): its name comes from the server, e.g. "bannerfall.de". */
  accountServer: {
    loginButton: (server: string) => `Log in with ${server}`,
    or: 'or with your password here',
    requiredHint: (server: string) => `On this world you play with your ${server} account.`,
    linkingHint: (server: string) =>
      `Already playing here? Log in with your password as before. Under “Change password” you can link your account to ${server}.`,
    problems: {
      FAILED: (server: string) => `The login at ${server} did not work. Please try again.`,
      UNAVAILABLE: (server: string) => `${server} cannot be reached right now. Please try again later.`,
      BANNED: (server: string) => `This account is banned on this world (${server}).`,
      DELETED: () => 'This player was deleted on this world.',
      ALREADY_LINKED: (server: string) =>
        `Your ${server} account already belongs to another player on this world. Log in with it instead.`,
      LINK_NOT_ALLOWED: () => 'Linking is not possible on this world (any more).',
    },
    joinTitle: 'Join this world',
    joinText: (name: string, server: string) =>
      `You are logged in at ${server} as ${name}. This world needs an invite code before you can start.`,
    joinButton: 'Start playing',
    joining: 'Founding your village…',
    joinMissing: (server: string) => `Log in with ${server} first.`,
    joinNameTaken: (name: string, server: string) =>
      `A player named ${name} already plays on this world. If that is you, log in with your password and link the account to ${server} under “Change password”.`,
    linkTitle: (server: string) => `Log in with ${server}`,
    linkText: (server: string) =>
      `Link this account to your ${server} account: from then on you log in there, with the same account on every world. Your name here stays. The password of this world stops working.`,
    linkButton: (server: string) => `Link with ${server}`,
    linking: 'Going there…',
    linked: (server: string) =>
      `This account is linked to ${server}. You log in there, and you change your password there too.`,
  },
  home: {
    noVillage: 'You have no village at the moment.',
  },
  /** The screen of a player whose last village was conquered (02-game-rules §10). */
  respawn: {
    title: 'Your last village has fallen',
    text: 'All your villages were conquered. You can start again with a new village somewhere else on the map, with fresh beginner protection.',
    reportsHint: 'Your reports tell how it happened.',
    toReports: 'Read the reports',
    start: 'Start a new village',
    starting: 'Founding your village…',
    guildText: 'Your guild is still in the game: you can also start a few fields from a guild mate.',
    nearGuild: 'Start next to my guild',
    elsewhere: 'Start somewhere new',
  },
  nav: {
    label: 'Game sections',
    village: 'Village',
    townHall: 'Town Hall',
    recruit: 'Recruit',
    bannerHall: 'Banner Hall',
    map: 'Map',
    rallyPoint: 'Rally Point',
    farm: 'Farm list',
    troops: 'Troops',
    market: 'Market',
    reports: 'Reports',
    messages: 'Messages',
    ranking: 'Ranking',
    achievements: 'Achievements',
    guild: 'Guild',
    more: 'More',
    settings: 'Settings',
    chat: 'Chat',
    /** The two groups of the phone's More sheet (1.4). */
    moreVillage: 'Village',
    morePlayer: 'Player',
    /** The player's own profile (1.5.1): More sheet on phones, the gear menu on desktop. */
    profile: 'My profile',
  },
  settings: {
    loggedInAs: (name: string) => `Logged in as ${name}`,
    language: 'Language',
    theme: 'Theme',
    themeModes: { system: 'Device', light: 'Light', dark: 'Dark' },
    /** Colour themes (1.6, theme/theme.ts `palettes`), each with a light and a dark set. */
    palette: 'Colours',
    palettes: {
      green: 'Green banner',
      blue: 'Royal blue',
      black: 'Black and gold',
      purple: 'Purple',
      leather: 'Leather and forest',
    } satisfies Record<Palette, string>,
    /** 1.6: the medieval icons from game-icons.net or the classic hand-drawn ones. */
    icons: 'Icons',
    iconStyles: { medieval: 'Medieval', classic: 'Classic' },
    serverTime: 'Server time',
    version: 'Version',
    changePassword: 'Change password',
    worldInfo: 'World settings',
    /** The link to the public source code (1.6): footer on desktop, a row in the menu everywhere. */
    source: 'GitHub',
    sourceTitle: 'Source code on GitHub (opens a new tab)',
  },
  /**
   * 1.5: push notifications. The Notifications page, and the notifications themselves (the service
   * worker writes them; the realtime texts above are reused where they fit).
   */
  push: {
    link: 'Notifications',
    title: 'Notifications',
    back: 'Back to the game',
    intro: 'Get a notification on this device while the game is closed, for example as soon as an attack is sent.',
    deviceTitle: 'This device',
    deviceOn: 'Notifications are on for this device.',
    deviceOff: 'Notifications are off for this device.',
    turnOn: 'Turn on',
    turnOff: 'Turn off',
    working: 'One moment…',
    test: 'Send a test',
    testSent: 'Test sent. It should arrive within a few seconds.',
    devices: (count: number) =>
      count === 1 ? 'Notifications reach 1 of your devices.' : `Notifications reach ${count} of your devices.`,
    blocked:
      'Notifications are blocked for this game in your browser. Allow them in the browser’s settings for this site, then try again.',
    unsupported: 'This browser can’t show notifications from the game.',
    iosInstall:
      'On an iPhone or iPad, notifications only work from the home screen: tap Share, then “Add to Home Screen”, and open the game from there.',
    unavailable: 'This world doesn’t send notifications.',
    failed: 'Notifications could not be turned on on this device.',
    kindsTitle: 'What to notify',
    kinds: {
      ATTACK: 'Incoming attacks',
      VILLAGE_LOST: 'Lost villages',
      MESSAGE: 'Private messages',
      GUILD_INVITE: 'Guild invitations',
      WORLD_END: 'Victory clock and end of the world',
      BUILDS_DONE: 'Build queue empty',
      ATTACK_RETURNED: 'Returning attacks',
      STORAGE_FULL: 'Warehouse full',
    } satisfies Record<PushKind, string>,
    kindHints: {
      ATTACK: 'As soon as an attack on one of your villages is sent, with its arrival.',
      VILLAGE_LOST: 'When one of your villages is conquered.',
      MESSAGE: 'When another player writes to you.',
      GUILD_INVITE: 'When a guild invites you.',
      WORLD_END: 'When a side starts or stops the victory clock, and when the world ends.',
      BUILDS_DONE: 'When the last building in a village’s build queue is done.',
      ATTACK_RETURNED:
        'When attacks you asked about are home again: one attack, a train or a farm list run. You choose when you send them, or with the bell.',
      STORAGE_FULL: 'When wood, clay or iron reaches the warehouse limit in a village. Once, until it drops again.',
    } satisfies Record<PushKind, string>,
    /** The notifications themselves. */
    testBody: 'Notifications work on this device.',
    attacks: (count: number, target: string, first: string) =>
      `${count} attacks on the way to ${target} – first arrival ${first}`,
    villageLost: (village: string, by: string | null) =>
      by === null ? `${village} was conquered` : `${by} conquered ${village}`,
    buildsDone: (village: string, building: string, level: number) =>
      `${village}: the build queue is empty. ${building} reached level ${level}.`,
    demolishedDone: (village: string, building: string, level: number) =>
      `${village}: the build queue is empty. ${building} was demolished to level ${level}.`,
  },
  /** The banner after a new version was deployed (M28): links to the changelog on bannerfall.de. */
  changelogBanner: {
    label: 'What is new',
    text: (version: string) => `Bannerfall ${version} is live.`,
    link: 'See what is new',
  },
  /** The end of a world (v1.3, 02-game-rules §22): the frozen look and the end screen. */
  worldEnd: {
    paused: 'stopped',
    frozen: 'The world has ended',
    title: 'This world has ended',
    when: (when: string) => `on ${when}`,
    numbers: 'Your numbers',
    rank: 'Rank',
    rankValue: (rank: number, total: number) => `${rank} of ${total}`,
    points: 'Points',
    villages: 'Villages',
    achievements: 'Achievements',
    seeResult: 'See the result',
    lookAround: 'Look around',
    stays: 'Reports, messages and the chat stay open.',
  },
  /** v1.3: the strip under the top bar once the world has ended (02-game-rules §22). */
  endedBanner: {
    label: 'World ended',
    link: 'See the result',
  },
  /** The pre-game (M14g): the world exists but has not opened; players can chat and read the settings. */
  pregame: {
    title: (world: string) => `${world} has not opened yet`,
    opensAt: (when: string) => `The world opens ${when}.`,
    noTime: 'The host has not announced an opening time yet.',
    countdown: 'Time left',
    days: (count: number) => (count === 1 ? '1 day' : `${count} days`),
    registered: (count: number) =>
      count === 1 ? '1 player has registered so far.' : `${count} players have registered so far.`,
    explainer:
      'Your account is ready. When the world opens, every registered player gets a village at the same moment and the beginner protection starts. Until then you can chat and read the world settings below.',
    adminHint: 'You are the host: set the opening time and the world settings on the admin page.',
    toAdmin: 'Open the admin page',
    guildsHint: 'Guilds are switched on in this world: you can found one now, or wait for an invitation.',
    guildsTogetherHint:
      'Guilds are switched on in this world, and guild mates start next to each other: found one now with your friends, or wait for an invitation.',
    toGuilds: 'Open guilds',
    /** The notice under the login and register forms. */
    notice: {
      opensAt: (world: string, when: string) => `${world} opens ${when}.`,
      noTime: (world: string) => `${world} has not opened yet. The opening time will be announced.`,
      register: 'You can create your account now; your village is founded when the world opens.',
    },
  },
  /** The world settings every player may see (pre-game page and the settings menu). */
  /** The guided world setup (1.6): the host's wizard in the pre-game. */
  setup: {
    title: 'World setup',
    stepOf: (step: number, total: number) => `Step ${step} of ${total}`,
    back: 'Back',
    next: 'Next',
    finish: 'Finish setup',
    skip: 'Skip setup',
    stepsLabel: 'Steps',
    steps: {
      start: 'Starting point',
      basics: 'Basics',
      pace: 'Pace',
      map: 'Map and opponents',
      rules: 'Rules',
      victory: 'Victory',
      opening: 'Opening',
    },
    intros: {
      start: 'Pick what kind of world you want. Every later step starts from it, and you can change each value.',
      basics: 'What the world is called and how players get in.',
      pace: 'How fast everything runs. Production, building, recruiting and marching all scale with the speed.',
      map: 'The map is created when the world opens: these values are fixed from then on.',
      rules: 'Which parts of the game are switched on. Everything here can still be changed while the world runs.',
      victory: 'How the world ends. You can change this later, too.',
      opening: 'Check everything once more, then pick when the world opens.',
    },
    presets: {
      friends: {
        name: 'Friends’ round',
        text: 'One to two weeks with a few friends: a quick pace, short protection and four lords as opponents.',
      },
      long: {
        name: 'Long-running world',
        text: 'A world that stays: a calm pace, full protection, lords in guilds and no planned end.',
      },
      coop: {
        name: 'Together against the lords',
        text: 'Two to four friends in one guild against about 20 hard lords in one or two lord guilds.',
      },
      custom: {
        name: 'Custom',
        text: 'Start from the default settings and set everything yourself.',
      },
    },
    facts: {
      speed: (value: string) => `Speed ${value}`,
      protection: (hours: string) => `${hours} h protection`,
      lords: (count: string) => `${count} lords`,
    },
    name: 'World name',
    inviteCode: 'Invite code',
    inviteHint: 'Players need it to register. Share it only with the people you invite.',
    newCode: 'Make a new code',
    villageNames: 'Village names',
    speed: 'World speed',
    unitSpeed: 'Unit speed',
    unitSpeedHint: 'Only marching troops and merchants; 1 keeps them at the world speed.',
    protection: 'Beginner protection (hours)',
    protectionHint: 'Real hours from the opening in which nobody can attack a new player.',
    startResources: 'Start resources (each)',
    paceHint: (from: number, to: number) => `At this speed the first nobles come around day ${from} to ${to}.`,
    mapSpread: 'Map spread',
    mapSpreadHint: 'Picking one also sets the map size and the noble range; you can still change both.',
    mapSize: 'Map size (fields per side)',
    nobleMaxDistance: 'Noble range (fields)',
    barbarians: 'Barbarian villages',
    barbarianInitialCount: 'At the start',
    barbariansPerNewPlayer: 'Per new player',
    barbarianGrowth: 'Barbarian villages grow',
    barbarianGrowthHint: 'They build up slowly, so late farms give more.',
    barbarianWalls: 'Barbarian walls',
    barbarianWallsHint: 'Barbarian villages build a wall after a few days.',
    lords: 'NPC lords',
    npcLordCount: 'Lords at the start',
    difficulty: 'Difficulty',
    difficulties: { EASY: 'Easy', NORMAL: 'Normal', HARD: 'Hard', CUSTOM: 'Custom' },
    npcGuilds: 'Lords form guilds',
    npcGuildsHint: 'Lords near each other team up, defend and avenge each other.',
    npcGuildMaxMembers: 'Most lords per lord guild',
    npcBanners: 'Lords use banners',
    npcBannersHint: 'They find, fly and fuse banners like players.',
    guilds: 'Guilds',
    guildsHint: 'Players can found and join guilds.',
    guildMaxMembers: 'Most members per guild',
    guildStartTogether: 'Guild mates start next to each other',
    guildStartTogetherHint:
      'Players in a guild at the opening get villages a few fields apart; who loses his last village may start again next to his guild.',
    guildsStartTogether: 'on, mates start together',
    morale: 'Morale',
    moraleHint: 'Attacks on much smaller players hit weaker. Only the attack changes, never the defence.',
    /** 1.6 UI review: the range in the hint, so the label fits on one line on a phone */
    moraleFloor: 'Lowest morale (%)',
    moraleFloorHint: '30 to 100',
    moraleValue: (floor: string) => `on, at least ${floor} %`,
    noblePrice: 'Rising noble price',
    noblePriceShort: 'Noble price',
    noblePriceValue: (percent: string) => `+${percent} % per village and noble`,
    noblePriceHint: (percent: string) =>
      `Each village and noble beyond four adds ${percent} % of the base price to the next noble, so big players don’t snowball.`,
    unitLevels: 'Unit levels',
    unitLevelsHint: 'The smithy can raise units to higher levels.',
    archers: 'Archers',
    archersHint: 'The archer and the mounted archer can be recruited; NPC lords use them too.',
    paladin: 'Paladin',
    paladinHint: 'One hero per player, more with more villages.',
    banners: 'Banners',
    bannersHint: 'Banners drop from battles and are carried in the Banner Hall.',
    standardTheft: 'Standard theft',
    standardTheftHint: 'Defenders can take the standard of an attack they beat.',
    nightBonus: 'Night bonus',
    nightBonusHint: (from: string, to: string) => `Villages defend more strongly from ${from}:00 to ${to}:00.`,
    market: 'Market exchange',
    marketHint: 'Trade with the market itself, not only with players.',
    victoryMode: 'Victory',
    victoryModes: { DOMINATION: 'Domination', KEEPS: 'Banner keeps', NONE: 'None' },
    victoryHints: {
      DOMINATION: 'A side wins once it holds a share of all villages long enough.',
      KEEPS: 'Banner keeps stand around the map centre; a side wins by holding most of them.',
      NONE: 'No side wins on its own: you end the world when you like.',
    },
    dominationPercent: 'Share of all villages (%)',
    victoryHoldHours: 'Held for (hours)',
    keepCount: 'Number of keeps',
    endsAt: 'End date (optional)',
    endsAtHint: 'At this time the most points win, in every mode.',
    opensAt: 'Opening time',
    opensAtHint: 'Every registered player gets a village at that moment. Leave it empty to set it later.',
    summary: 'Summary',
    change: 'Change',
    on: 'on',
    off: 'off',
    server: 'Set on the server',
    serverHint: 'These come from the server’s .env file (hosting guide); the wizard only shows them.',
    loginMode: 'Login',
    push: 'Push notifications',
    invalid: 'A value is out of range: check the fields of this step.',
    claiming: 'Making you the host of this world…',
    toHome: 'Back to the start page',
    worldOpen:
      'This world is open already: the setup only runs before the opening. Its settings are on the admin page.',
    toAdmin: 'Open the admin page',
    loginModes: { OFF: 'own accounts', LINKING: 'own accounts or the account server', REQUIRED: 'account server only' },
    entry: {
      title: 'Set up this world',
      text: 'A few short steps: pick a starting point, check pace, map and rules, and set the opening time. Until the world opens you can still change everything on the admin page.',
      start: 'Start the setup',
      skip: 'Not now',
    },
  },
  worldInfo: {
    title: 'World settings',
    speed: 'World speed',
    unitSpeed: 'Unit speed',
    mapSize: 'Map',
    mapSizeValue: (size: number) => `${size} × ${size} fields`,
    /** How far apart the sides start (1.6). */
    mapSpread: 'Map spread',
    mapSpreadValue: (spread: string, distance: string) => `${spread}: ${distance}`,
    mapSpreads: { CLOSE: 'Close', NORMAL: 'Normal', WIDE: 'Wide', VAST: 'Vast' },
    mapSpreadDistances: {
      CLOSE: 'neighbours start about 3 to 4 fields apart.',
      NORMAL: 'neighbours start about 4 to 5 fields apart.',
      WIDE: 'neighbours start about 6 fields apart.',
      VAST: 'neighbours start about 7 fields apart.',
    },
    startResources: 'Start resources',
    startResourcesValue: (amount: string) => `${amount} of each`,
    beginnerProtection: 'Beginner protection',
    beginnerProtectionValue: (hours: number) => (hours === 0 ? 'none' : `${hours} hours`),
    nobleMaxDistance: 'Noble range',
    nobleMaxDistanceValue: (fields: number) => `${fields} fields`,
    loyaltyRegen: 'Loyalty regeneration',
    loyaltyRegenValue: (perHour: string) => `${perHour} per hour`,
    luck: 'Luck in battle',
    luckValue: (percent: string) => `±${percent}`,
    buildQueue: 'Build queue',
    buildQueueValue: (size: number, max: number) =>
      size === max ? `${size} orders` : `${size} orders, up to ${max} with paid slots`,
    recruitQueue: 'Recruit queue',
    recruitQueueValue: (size: number) => `${size} orders per building`,
    cancelWindow: 'Cancel window',
    cancelWindowValue: (minutes: string) => `${minutes} min`,
    barbarianGrowth: 'Barbarian villages grow',
    barbarianWalls: 'Barbarian walls',
    barbarianWallsValue: (days: number, maxLevel: number, rebuild: boolean) =>
      `after ${days} ${days === 1 ? 'day' : 'days'}, up to level ${maxLevel}${rebuild ? ', rebuilt after attacks' : ''}`,
    npcLords: 'NPC lords',
    npcLordsValue: (count: number, difficulty: string) => (count === 0 ? 'none' : `${count}, ${difficulty}`),
    marketRatio: 'Widest market ratio',
    marketRatioValue: (ratio: string) => `1 : ${ratio}`,
    /** v1.3: trading with the market itself, "1.5 for 1" (or "No" when the world has it off). */
    marketExchange: 'Trade with the market',
    marketExchangeValue: (ratio: string) => `${ratio} for 1, up to a daily amount`,
    guilds: 'Guilds',
    guildsValue: (max: number, together: boolean) =>
      together ? `up to ${max} members each; guild mates start next to each other` : `up to ${max} members each`,
    unitLevels: 'Unit levels',
    unitLevelsValue: 'researched in the smithy',
    archers: 'Archers',
    archersValue: 'archer and mounted archer',
    paladin: 'Paladin',
    paladinValue: (perSlot: number, max: number) =>
      max === 1
        ? 'one paladin'
        : `one slot, another ${perSlot === 1 ? 'for every village' : `for every ${perSlot} villages`}, up to ${max}`,
    banners: 'Banners',
    bannersValue: 'Banner Hall, equip and swap banners',
    /** 1.6 (02-game-rules §5.7): "25" */
    noblePrice: 'Noble price',
    noblePriceValue: (percent: string) => `+${percent} % for every village and noble beyond four`,
    noblePriceFlat: 'the same for every noble',
    /** 1.4 (02-game-rules §8.1): "23:00–07:00", "2" */
    nightBonus: 'Night bonus',
    nightBonusValue: (hours: string, factor: string) =>
      `${hours} German time: player and NPC lord villages defend ×${factor}`,
    /** 1.6 (02-game-rules §8.2): "30%" */
    morale: 'Morale',
    moraleValue: (floor: string) =>
      `attacks on players with less than about a quarter of your points hit weaker, down to ${floor}`,
    /** v1.3 (02-game-rules §22): how the world is won, and its end date. */
    victory: 'Victory',
    victoryDomination: (percent: number, hours: number) => `${percent}% of all villages, held for ${hours} hours`,
    victoryKeeps: (needed: number, count: number, hours: number) =>
      `${needed} of ${count} banner keeps, held for ${hours} hours`,
    victoryNone: 'the host ends the world',
    endsAt: 'World ends',
    endsAtNone: 'no date set',
    yes: 'yes',
    no: 'no',
  },
  /** /password: the player changes his own password (M14d). */
  password: {
    title: 'Change password',
    back: 'Back to the game',
    current: 'Current password',
    new: 'New password',
    repeat: 'New password again',
    mismatch: 'The two new passwords are not the same.',
    save: 'Change password',
    saving: 'Changing…',
    saved: 'Your password is changed. Use the new one the next time you log in.',
  },
  resources: {
    title: 'Resources',
    wood: 'Wood',
    clay: 'Clay',
    iron: 'Iron',
    population: 'Population',
    /** Production, e.g. "+30/h". The amount is already formatted. */
    perHour: (amount: string) => `+${amount}/h`,
    perHourLong: (amount: string) => `${amount} per hour`,
    capacity: (amount: string) => `max. ${amount}`,
    full: 'warehouse full',
    populationFull: 'farm full',
    populationAlmostFull: 'farm almost full',
    showDetails: 'Show resource details',
    hideDetails: 'Hide resource details',
    /** Tooltip on wood, clay and iron in the top bar: they link to the warehouse page. */
    toWarehouse: 'Open the warehouse',
  },
  /** Keyed by BuildingType (02-game-rules §4.1). `satisfies` makes the compiler check that all 15 are there. */
  buildings: {
    HEADQUARTERS: 'Town Hall',
    BARRACKS: 'Barracks',
    STABLE: 'Stable',
    WORKSHOP: 'Workshop',
    ACADEMY: 'Academy',
    SMITHY: 'Smithy',
    RALLY_POINT: 'Rally Point',
    MARKET: 'Market',
    WOODCUTTER: 'Woodcutter',
    CLAY_PIT: 'Clay Pit',
    IRON_MINE: 'Iron Mine',
    FARM: 'Farm',
    WAREHOUSE: 'Warehouse',
    HIDING_PLACE: 'Hiding Place',
    WALL: 'Wall',
    BANNER_HALL: 'Banner Hall',
  } satisfies Record<BuildingType, string>,
  /** Keyed by UnitType (02-game-rules §5.1). */
  units: {
    SPEARMAN: 'Spearman',
    SWORDSMAN: 'Swordsman',
    AXEMAN: 'Axeman',
    SCOUT: 'Scout',
    LIGHT_CAVALRY: 'Light Cavalry',
    HEAVY_CAVALRY: 'Heavy Cavalry',
    RAM: 'Ram',
    CATAPULT: 'Catapult',
    NOBLE: 'Noble',
    PALADIN: 'Paladin',
    BANNER_THIEF: 'Banner Thief',
    ARCHER: 'Archer',
    MOUNTED_ARCHER: 'Mounted Archer',
  } satisfies Record<UnitType, string>,
  village: {
    switcherTitle: 'Your villages',
    /** The tag of the village held longest, first in the switcher (1.3). */
    mainVillage: 'Main village',
    rename: 'Rename village',
    nameLabel: 'Village name',
    nameHint: '1–32 characters',
    nameEmpty: 'Please enter a name.',
    nameTooLong: 'Use at most 32 characters.',
    points: (points: string) => `${points} points`,
    protection: (until: string) => `Beginner protection until ${until}`,
    protectionHint: 'Nobody can attack you until then.',
    incomingAttacks: (count: number) =>
      count === 1 ? '1 attack is on its way here.' : `${count} attacks are on their way here.`,
    toIncoming: 'See incoming troops',
    buildingsTitle: 'Buildings',
    level: (level: number) => `Lv ${level}`,
    notBuilt: 'not built',
    /** The switch in the Buildings card (1.3): the village picture or the list. */
    viewLabel: 'Show the buildings as',
    viewPicture: 'Picture',
    viewList: 'List',
    infoTitle: 'Village',
    population: 'Population',
    storage: 'Warehouse (per resource)',
    hiding: 'Hiding place (per resource)',
    loyaltyLabel: 'Loyalty',
    banner: 'Banner',
    troopsTitle: 'Troops at home',
    noTroops: 'No troops at home yet.',
    toFirstVillage: 'Go to my village',
  },
  warehouse: {
    title: 'Warehouse',
    back: 'Village overview',
    /** "Lv 5 · holds 1,000 of each resource". */
    holds: (level: string, capacity: string) => `${level} · holds ${capacity} of each resource`,
    fullTitle: 'When is it full?',
    /** In front of the countdown. */
    fullIn: (time: string) => `Full in ${time}`,
    fullAt: (when: string) => `at ${when}`,
    isFull: 'Full: production is lost until you spend some',
    notProduced: 'Not produced',
    assumption:
      'Assuming nothing is spent and the production stays as it is. What is above the capacity stays, it just stops growing.',
    upgradeTitle: 'More room',
    upgradeText: 'A bigger warehouse holds more of each resource and also lets you pay for the bigger buildings.',
    toTownHall: 'Upgrade the warehouse in the Town Hall',
  } /** The smithy (M30, 02-game-rules §5.4): what every unit can do and, with unit levels, research. */,
  smithy: {
    title: 'Smithy',
    back: 'Village overview',
    notBuilt: 'The smithy is not built in this village yet. Research needs it.',
    toTownHall: 'Build the smithy in the Town Hall',
    levelsOff: 'Unit levels are switched off in this world: every unit has its base values.',
    levelsOn:
      'Every unit starts at level 1. A research raises one unit by one level in all your villages, and the better values count in every battle.',
    unitsTitle: 'Units',
    scoutHint:
      "A level 2 scout is an upgraded scout: when scouting succeeds, your report also shows the defenders' unit levels.",
    paladinHint:
      'A support with a paladin walks at his pace: all units in it need his minutes per field (attacks still go at the slowest unit).',
    /** Screen-reader name of the value list of a unit. */
    statsLabel: (unit: string) => `Values of ${unit}`,
    attack: 'Attack',
    defenseInfantry: 'Defence vs. infantry',
    defenseInfantryShort: 'Def. inf.',
    defenseCavalry: 'Defence vs. cavalry',
    defenseCavalryShort: 'Def. cav.',
    defenseArcher: 'Defence vs. archers',
    defenseArcherShort: 'Def. arch.',
    archerHint: 'Attacks as an archer: the defenders count their defence against archers.',
    minutesPerField: 'Minutes per field',
    carry: 'Carries',
    level: (level: number) => `Lv ${level}`,
    /** "Lv 2 of 3". */
    levelOf: (level: number, max: number) => `Lv ${level} of ${max}`,
    maxed: 'Top level reached',
    levelsTable: 'Values per level',
    researchTitle: 'Research',
    nothingRunning: 'Nothing is being researched in this village.',
    /** "Spearman to Lv 2". */
    researching: (unit: string, level: number) => `${unit} to Lv ${level}`,
    readyIn: 'Ready in',
    elsewhere: 'Being researched in another village of yours.',
    next: (level: number) => `Research Lv ${level}`,
    research: 'Research',
    starting: 'Starting…',
    time: 'Research time',
    costsLabel: 'Cost of the research',
    busy: 'The smithy of this village is busy with another research.',
    cancel: (what: string) => `Cancel the research of ${what}`,
    cancelTitle: 'Cancel the research?',
    cancelText: (what: string) => `${what} stops and its progress is lost.`,
    cancelRefund: 'You get the full cost back, as far as the warehouse has room.',
    confirmCancel: 'Cancel research',
  },

  /** Hall training (1.4): the Banner Hall trains the paladin's and the banner thief's levels. */
  hallTraining: {
    title: 'Hall training',
    /** Which of the two the world has: the paladin (`paladin`), the thieves (banners on). */
    intro: (paladin: boolean, thieves: boolean) =>
      `The Banner Hall trains the levels of ${paladin && thieves ? 'the paladin and the banner thieves' : paladin ? 'the paladin' : 'the banner thieves'}. A level counts in all your villages, also in worlds without unit levels. One training at a time per Banner Hall.`,
    paladinLevels: 'A higher level makes every paladin of yours a little stronger.',
    thiefLevels: 'A higher level raises the chance of every surviving thief to steal a banner.',
    /** Header of the thief's table: the chance one surviving thief adds. */
    chancePerThief: 'Chance per thief',
    /** Screen-reader name of the thief's table. */
    chanceLabel: 'Chance per surviving thief at each level',
    researchTitle: 'Training',
    nothingRunning: 'Nothing is being trained in this Banner Hall.',
    researching: (unit: string, level: number) => `${unit} to Lv ${level}`,
    elsewhere: 'Being trained in another village of yours.',
    next: (level: number) => `Train Lv ${level}`,
    research: 'Train',
    time: 'Training time',
    costsLabel: 'Cost of the training',
    busy: 'The Banner Hall of this village is busy with another training.',
    cancel: (what: string) => `Cancel the training of ${what}`,
    cancelTitle: 'Cancel the training?',
    confirmCancel: 'Cancel training',
    /** On the smithy page: the paladin's and thief's levels come from the hall. */
    inTheHall: 'Its levels are trained in the Banner Hall.',
    toHall: 'To the Banner Hall',
  },

  townHall: {
    title: 'Town Hall',
    back: 'Village overview',
    buildingsTitle: 'Buildings',
    lockedTitle: 'Not available yet',
    build: 'Build',
    upgrade: (level: number) => `Upgrade to Lv ${level}`,
    queueing: 'Queueing…',
    /** Level after everything queued for the building is done. */
    queuedLevel: (level: number) => `→ Lv ${level}`,
    queuedLevelLong: (level: number) => `(Lv ${level} once the queue is done)`,
    maxLevel: 'Maximum level reached',
    /** The switch at the top of the Town Hall (1.6): upgrading or demolishing. */
    modeLabel: 'Upgrade or demolish',
    modes: { build: 'Upgrade', demolish: 'Demolish' },
    /** Instead of the upgrade button while a demolition of the building is queued (1.6). */
    demolitionQueued: 'A demolition of this building is in the build queue.',
    /** The demolish side of the Town Hall (1.6, 02-game-rules §4.5). */
    demolish: {
      title: 'Demolish',
      intro:
        'Each demolition takes one level away and joins the build queue. It takes a fifth of the build time and costs nothing; nothing comes back.',
      button: (level: number) => `Demolish to Lv ${level}`,
      /** The button on phones (1.6 UI review): one level down; the screen-reader name stays `buttonLabel`. */
      buttonShort: '−1',
      buttonLabel: (building: string, level: number) => `Demolish ${building} to level ${level}`,
      /** Screen-reader name of the facts of a demolition. */
      factsLabel: 'What the demolition takes and frees',
      time: 'Time',
      frees: (pop: number) => `+${pop} free`,
      freesLong: (pop: number) => `frees ${pop} population`,
      points: (points: number) => `−${points} points`,
      /** A building at the lowest level it can be demolished to. */
      lowest: (level: number) => `Level ${level} is the lowest.`,
      rallyPoint: 'The rally point can’t be demolished.',
      conflict: 'An upgrade of this building is in the build queue.',
      farmTooSmall: 'The smaller farm could not feed your population.',
      confirmTitle: (building: string) => `Demolish ${building}?`,
      confirmLevels: (from: number, to: number) => `Lv ${from} → ${to}`,
      confirmText: (time: string) => ` in ${time}. Demolishing costs nothing, and nothing comes back.`,
      confirmFrees: (pop: number) => ` ${pop} population becomes free.`,
      confirmPoints: (points: number) => ` The village loses ${points} points.`,
      warehouse: 'Resources above the smaller warehouse stay, but nothing is produced until you are below it.',
      locksTitle: 'Locked afterwards until you build it up again:',
      locksBuilding: (building: string) => `${building}: no further upgrades`,
      locksUnit: (unit: string) => `${unit}: no recruiting`,
      keep: 'Keep it',
      confirm: 'Demolish',
      queueing: 'Queueing…',
    },
    needs: (list: string) => `Needs ${list}`,
    requirement: (building: string, level: number) => `${building} ${level}`,
    buildTime: 'Build time',
    /** Screen-reader name of the cost list of a building's next level. */
    costsLabel: 'Cost of the next level',
    /** Screen-reader note after a cost the village can't pay yet. */
    notEnough: 'not enough',
    enoughAt: (time: string) => `Enough resources at ${time}`,
    warehouseTooSmall: 'The warehouse is too small for this.',
    notProduced: 'A resource for this is not produced.',
    farmTooSmall: 'Not enough free population – upgrade the farm.',
    queueFull: 'The build queue is full.',
    /** On the build button when the next order takes a paid slot (M13c). */
    surcharge: (percent: number) => `+${percent}%`,
    surchargeNote: (percent: number) => `The next order takes a paid slot: it costs ${percent}% more.`,
    /** The upgrade preview (M24): hover or focus on a building row on a computer, the info button elsewhere. */
    preview: {
      button: (building: string) => `Preview of the next level: ${building}`,
      /** Before and after the upgrade; a building that isn't there yet starts from nothing. */
      levels: (from: number, to: number) => (from === 0 ? `Not built → Lv ${to}` : `Lv ${from} → Lv ${to}`),
      effectsLabel: 'What changes',
      effect: {
        PRODUCTION_PER_HOUR: 'Production per hour',
        WAREHOUSE_CAPACITY: 'Capacity per resource',
        FARM_CAPACITY: 'Population limit',
        HIDING_CAPACITY: 'Hidden from raiders, per resource',
        MERCHANTS: 'Merchants',
        BUILD_TIME_PERCENT: 'Build time of new orders (no Town Hall = 100%)',
        RECRUIT_TIME_PERCENT: 'Recruit time per unit (Lv 1 = 100%)',
        WALL_DEFENSE_PERCENT: 'Defence bonus',
        WALL_DEFENSE_FLAT: 'Flat defence bonus',
      } satisfies Record<BuildingEffect['kind'], string>,
      /** Screen-reader word for the arrow between the old and the new value. */
      toValue: 'becomes',
      unlocks: (list: string) => `Unlocks: ${list}`,
      /** The Smithy and the like: nothing changes by itself, others need them. */
      noEffect: 'Other buildings and units need this building.',
      ready: 'You can queue this upgrade now.',
    },
  },
  buildQueue: {
    title: 'Build queue',
    titleCount: (count: number, max: number) => `Build queue (${count}/${max})`,
    empty: 'Nothing is being built.',
    readyAt: (time: string) => `ready at ${time}`,
    toTownHall: 'Open the Town Hall',
    moveUp: (order: string) => `Move ${order} up`,
    moveDown: (order: string) => `Move ${order} down`,
    cancel: (order: string) => `Cancel ${order}`,
    cancelTitle: 'Cancel this order?',
    cancelText: (order: string) => `${order} will not be built.`,
    cancelRefund:
      'You get the resources back, as far as the warehouse has room. Later orders that need this one are cancelled too.',
    keep: 'Keep it',
    confirmCancel: 'Cancel order',
    moveBlocked: 'This order cannot come before an order it needs.',
    /** Next to an order in a paid slot (behind the world's queue size, M13c). */
    paidSlot: 'paid slot',
    /** Next to a demolition (1.6). */
    demolition: 'demolition',
    cancelDemolitionText: (order: string) => `${order} will not be demolished.`,
    cancelDemolitionNote:
      'It cost nothing, so nothing comes back. Later demolitions of this building are cancelled too.',
    paidSlotsNote: (size: number) => `Places after ${size} cost extra, and their orders can't be moved.`,
    /** Below the queue: shows the move buttons on the waiting orders (1.3). */
    reorder: 'Change order',
    reorderDone: 'Done',
    /** A finished order, for a moment before its line folds away (1.3). */
    finished: 'Done',
  },
  recruit: {
    /** The Banner Hall's tab: in German with a soft hyphen, so a phone can break it (M32b). */
    bannerHallTab: 'Banner Hall',
    title: 'Recruit',
    buildingsLabel: 'Recruiting buildings',
    /** Screen-reader text of the number on a building tab. */
    ordersInQueue: (count: number) => (count === 1 ? '1 order in the queue' : `${count} orders in the queue`),
    notBuilt: (building: string) => `${building}: not built yet.`,
    toTownHall: 'Build it in the Town Hall',
    unitsTitle: (building: string) => `Units (${building})`,
    atHome: (amount: string) => `${amount} at home`,
    /** After "… at home": all of the village's own units, the ones away included. */
    ofTotal: (total: string) => `(${total} total)`,
    /** Names the cost list of one unit (screen readers). */
    perUnit: 'Cost per unit',
    /** Label of the cost list for the typed count. */
    total: (count: string) => `Total for ${count}:`,
    time: 'Recruit time',
    countLabel: (unit: string) => `Number of units to recruit: ${unit}`,
    recruit: 'Recruit',
    recruiting: 'Recruiting…',
    queueFull: 'The queue of this building is full.',
    /** "Recruit all" (M27): every filled-in field in one go. */
    recruitAll: 'Recruit all',
    /** Label of the cost list of all filled-in fields together. */
    allTotal: (orders: number) => (orders === 1 ? 'Total (1 order):' : `Total (${orders} orders):`),
    /** The field alone fits (its own button works), but not together with the other fields. */
    sharedByOthers: 'Too much together with the other fields.',
    queuePlaces: (free: number) =>
      free === 1
        ? 'Only 1 free place in the queue: recruit one order at a time.'
        : `Only ${free} free places in the queue: fill in fewer fields.`,
    /** Why "Recruit all" is grey while no field has a number (1.3). */
    enterCount: 'Enter a number for a unit first.',
  },
  /** Recruit templates (1.4, 02-game-rules §5.2b): the row above the building tabs and its sheets. */
  recruitTemplates: {
    title: 'Templates',
    count: (count: number) => (count === 1 ? '1 template' : `${count} templates`),
    edit: 'Edit templates',
    create: 'Create a template',
    open: (letter: string, name: string) => `Template ${letter}: ${name}`,
    planHint: 'Recruits what this village is still missing towards the goal, as far as the resources go right now.',
    /** The plan table's first column, for screen readers. */
    unit: 'Unit',
    goal: 'Goal',
    have: 'Have',
    now: 'Now',
    skip: {
      reached: 'reached',
      locked: 'locked',
      queueFull: 'queue full',
      slots: 'no free slot',
    },
    allReached: 'Goal reached: this village is missing nothing.',
    tooPoor: 'Not enough for a single unit right now.',
    loading: 'Counting the units away…',
    total: 'Together:',
    recruit: (count: number) => (count > 1 ? `Recruit (${count})` : 'Recruit'),
    manageTitle: 'Recruit templates',
    manageHint:
      'They apply to all your villages. Tap a template on the recruit page to see what it recruits right now.',
    newTemplate: (letter: string) => `Create template ${letter}`,
    more: (letter: string) => `More for template ${letter}`,
    change: 'Change',
    remove: 'Delete',
    editTitle: (letter: string) => `Template ${letter}`,
    name: 'Name',
    nameHint: 'Short, e.g. Attack or Defence',
    goalLabel: 'Goal army per village',
    fromVillage: "Take this village's army",
    haveAmount: (amount: string) => `have ${amount}`,
    countLabel: (unit: string) => `Goal for ${unit}`,
    save: 'Save',
  },
  /** Village automation (1.6, 02-game-rules §25): the Town Hall's row, its page and the plan sheets. */
  automation: {
    title: 'Automation',
    back: 'Town Hall',
    /** The Town Hall's row. */
    rowTitle: (letter: string, name: string) => `Automation · ${letter} ${name}`,
    rowLine: (line: number, count: number) => `Line ${line}/${count}`,
    rowOpen: 'Open the automation',
    lockedShort: (needed: number) => `From your ${ordinal(needed)} village`,
    locked: (needed: number) =>
      `Unlocks with your ${ordinal(needed)} village: villages then keep building by your plan by themselves, even while you are offline.`,
    lockedCount: (villages: number, needed: number) => `You have ${villages} of ${needed} villages.`,
    noWorld: 'This world has no automation.',
    villageTitle: 'This village',
    switchLabel: 'Automation on',
    /** next to the switch (1.6 UI review) */
    running: 'Running',
    paused: 'Paused',
    choosePlan: 'Choose a plan',
    changePlan: (letter: string, name: string) => `Plan ${letter}: ${name}. Change plan`,
    progress: (line: number, count: number) => `Line ${line} of ${count}`,
    /** What it does now, by state; `next` is "Barracks Lv 4". */
    state: {
      OFF: 'Switched off.',
      PAUSED: 'Paused: you cancelled one of its orders or demolished a level its plan builds. Switch it on to go on.',
      NO_PLAN: 'Its plan was deleted. Choose another one.',
      READY: (next: string) => `Queues ${next} now.`,
      WAITING_RESOURCES: (next: string, time: string) => `Waits for resources: ${next} · enough at ${time}`,
      WAITING_QUEUE: (next: string) => `Waits until a build order is done: ${next} next.`,
      WAITING_UPGRADE: 'Waits for the warehouse or farm level in the queue.',
      BLOCKED: (next: string) => `Stuck at ${next}: a resource for it is not produced, or the farm is full.`,
      DONE: 'Plan done: every line is reached.',
    },
    noPlanYet: 'Choose a plan: the village starts working through it right away.',
    recruiting: (letter: string, name: string) => `Keeps recruit template ${letter} ${name} filled.`,
    recruitingGone: (letter: string) => `Recruit template ${letter} no longer exists: nothing is recruited.`,
    missing: (list: string) => `Missing: ${list}`,
    templateFull: 'The template is full.',
    byHand: (list: string) => `${list}: you recruit these yourself.`,
    planTitle: (letter: string, name: string) => `Plan ${letter} · ${name}`,
    edit: 'Edit',
    plansTitle: 'Build plans',
    plansHint:
      'A plan lists buildings and levels from top to bottom. The village queues them one after the other, at most two orders of its own at a time, and builds a bigger warehouse or farm first when it needs one. All your villages can use the same plan.',
    createPlan: (letter: string) => `Create plan ${letter}`,
    /** The plan picker. */
    pickTitle: (village: string) => `Plan for ${village}`,
    lineCount: (count: number) => (count === 1 ? '1 line' : `${count} lines`),
    newPlan: (letter: string) => `New plan (${letter})`,
    newPlanHint: "Empty, or from this village's levels",
    noPlan: 'No plan',
    noPlanHint: 'This village builds only what you queue.',
    /** The editor. */
    editTitle: (letter: string) => `Plan ${letter}`,
    name: 'Name',
    nameHint: 'Short, e.g. Economy or Offence',
    linesHint: (count: number) => `${count === 1 ? '1 line' : `${count} lines`} · from top to bottom`,
    reorder: 'Change order',
    reorderDone: 'Done',
    empty: 'No lines yet. Add a building, or take the levels of this village.',
    fromVillage: "Take this village's levels",
    addBuilding: '+ Building',
    addRecruit: '+ Start recruiting',
    addTitle: 'Add a line',
    addHint: 'Each tap adds the next level. A missing requirement comes in front of it.',
    addClose: 'Close',
    recruitTitle: 'Start recruiting which template?',
    recruitHint:
      'From this line on the village keeps the template filled (nobles and banner thieves excepted). The lines below it go on being built.',
    noTemplates: 'Make a recruit template on the recruit page first.',
    startRecruiting: 'Start recruiting',
    recruitChip: (letter: string, name: string) => `${letter} ${name}`,
    levelDown: (building: string) => `${building}: one level less`,
    levelUp: (building: string) => `${building}: one level more`,
    moveUp: (line: number) => `Move line ${line} up`,
    moveDown: (line: number) => `Move line ${line} down`,
    remove: (line: number) => `Remove line ${line}`,
    needs: (list: string) => `Needs ${list} in a line above`,
    tooMany: (max: number) => `A plan has at most ${max} lines.`,
    save: 'Save',
    delete: 'Delete plan',
    deleteTitle: (letter: string) => `Delete plan ${letter}?`,
    deleteText: 'Villages working through it stop until you pick another plan.',
    keep: 'Keep it',
    /** Next to an order the automation placed in the build queue. */
    automatedOrder: 'placed by the automation',
  },
  /** The paladin and his slots (M31, 02-game-rules §5.5): the counter under his name in the Banner Hall (1.4). */
  paladin: {
    slots: (used: number, slots: number) =>
      slots === 1 ? `${used} of 1 slot in use` : `${used} of ${slots} slots in use`,
    nextSlot: (villages: number, owned: number) => `Next slot at ${villages} villages (you own ${owned}).`,
    atMaximum: 'This is the most slots the world allows.',
    freeSlots: (free: number) =>
      free <= 0 ? 'Every slot is taken.' : free === 1 ? 'Only 1 slot is free.' : `Only ${free} slots are free.`,
  },
  /** The rising noble price (1.6, 02-game-rules §5.7): the noble's info button in the Academy. */
  noble: {
    button: 'Price of the next noble',
    title: 'Price of nobles',
    next: 'Your next noble',
    after: 'The one after it',
    /** "×1.5" */
    factor: (factor: string) => `×${factor}`,
    /** the chip after the noble's costs in the Academy (1.6 UI review), for screen readers */
    factorChip: (factor: string) => `Price ×${factor}: price of the next noble`,
    /** "3 villages + 3 nobles, 4 free → 2 × 25% = ×1.5" (1.6 UI review: it read like a ranking before) */
    rankLine: (
      villages: number,
      nobles: number,
      free: number,
      rank: number,
      percent: string,
      factor: string,
      capped: boolean,
    ) =>
      `${villages} ${villages === 1 ? 'village' : 'villages'} + ${nobles} ${nobles === 1 ? 'noble' : 'nobles'}, ${free} free → ${
        rank === 0
          ? 'base price'
          : capped
            ? `${rank} × ${percent}%, at most ×${factor}`
            : `${rank} × ${percent}% = ×${factor}`
      }`,
    rule: (free: number, percent: string, max: string) =>
      `With one village your first ${free} nobles cost the base price. Every further village and every noble beyond that makes the next one ${percent}% of the base price dearer, up to ${max} times the base price.`,
    counted: 'Nobles count wherever they are: at home, on the way, in support, in training.',
    cheaper: 'A noble that dies or a village you lose makes the next one cheaper again.',
    atMaximum: 'This is the highest price: it rises no further.',
    flat: 'In this world every noble costs the same.',
    order: 'Several nobles in one order each pay the average of their prices.',
  },
  /** Banners and the Banner Hall (M32a, 02-game-rules §21). */
  banners: {
    title: 'Banner Hall',
    back: 'Village overview',
    /** The name of a type, as the banner is called: "War banner". */
    types: {
      HARVEST: 'Harvest banner',
      BUILDERS: 'Builders banner',
      DRILL: 'Drill banner',
      WAR: 'War banner',
      BULWARK: 'Bulwark banner',
      HASTE: 'Haste banner',
      TREASURY: 'Treasury banner',
      PLUNDER: 'Plunder banner',
      OATH: 'Oath banner',
      // 1.4: the paladin standards
      SPEAR_STANDARD: 'Spear standard',
      SWORD_STANDARD: 'Sword standard',
      AXE_STANDARD: 'Axe standard',
      LANCE_STANDARD: 'Lance standard',
      KNIGHT_STANDARD: 'Knight standard',
      RAM_STANDARD: 'Ram standard',
      SIEGE_STANDARD: 'Siege standard',
    } satisfies Record<BannerType, string>,
    /** The text of a banner picture: "War banner, tier II". */
    label: (name: string, tier: number) => `${name}, tier ${tierName(tier)}`,
    tier: (tier: number) => `Tier ${tierName(tier)}`,
    /** Identical banners of the stash in one row (1.3): "× 3". */
    count: (count: number) => `× ${count}`,
    /** What a banner does at a tier; the percent is already formatted ("10 %"). */
    effects: {
      HARVEST: (percent: string) => `Wood, clay and iron production +${percent}`,
      BUILDERS: (percent: string) => `Build time of new orders −${percent}`,
      DRILL: (percent: string) => `Recruit time of new orders −${percent}`,
      WAR: (percent: string) => `Attack strength of attacks sent from here +${percent}`,
      BULWARK: (percent: string) => `Defence of the village +${percent}`,
      HASTE: (percent: string) => `Travel time of attacks and supports sent from here −${percent}`,
      TREASURY: (percent: string) => `Warehouse and hiding place capacity +${percent}`,
      PLUNDER: (percent: string) => `Carry capacity of units sent from here +${percent}`,
      OATH: (percent: string) => `Loyalty each enemy noble takes off −${percent}`,
      SPEAR_STANDARD: (percent: string) => `Defence of the spearmen it goes with +${percent}`,
      SWORD_STANDARD: (percent: string) => `Defence of the swordsmen it goes with +${percent}`,
      AXE_STANDARD: (percent: string) => `Attack strength of the axemen it goes with +${percent}`,
      LANCE_STANDARD: (percent: string) => `Attack strength of the light cavalry it goes with +${percent}`,
      KNIGHT_STANDARD: (percent: string) => `Attack and defence of the heavy cavalry it goes with +${percent}`,
      RAM_STANDARD: (percent: string) => `Wall damage of the rams it goes with +${percent}`,
      SIEGE_STANDARD: (percent: string) => `Building damage of the catapults it goes with +${percent}`,
    } satisfies Record<BannerType, (percent: string) => string>,
    notBuilt: 'The Banner Hall is not built in this village yet. A banner needs it to fly here.',
    toTownHall: 'Build the Banner Hall in the Town Hall',
    hallLevel: (level: number, max: number) => `Banner Hall, level ${level} of ${max}`,
    hallExplain:
      'The hall decides what a banner is worth here: it works at its own tier, but at most at the tier of the hall. Every banner works in one village at a time.',
    disabled: 'Banners are switched off in this world. Your banners are kept, but they do nothing.',
    slotTitle: 'Banner in this village',
    empty: 'No banner flies in this village.',
    emptyHint: 'Raise one from your stash below.',
    sleeping: 'Asleep: this village has no Banner Hall.',
    cappedAt: (tier: number) => `The hall is too small for this tier: the banner works as tier ${tierName(tier)}.`,
    working: 'Works at full strength.',
    /** Tooltip of the banner on the village page. */
    flyingTitle: (name: string, tier: number, effective: number) =>
      effective === 0
        ? `${name}, tier ${tierName(tier)}: asleep`
        : effective < tier
          ? `${name}, tier ${tierName(tier)}: works as tier ${tierName(effective)}`
          : `${name}, tier ${tierName(tier)}`,
    unequip: 'Take down',
    unequipping: 'Taking down…',
    locked: 'An attack is on its way: banners cannot be changed here until it has arrived.',
    stashTitle: (count: number) => `Stash (${count})`,
    stashEmpty:
      'Your stash is empty. Winning a battle against a player or an NPC lord, attacking or defending, can bring you a banner; against barbarians it happens more rarely.',
    stashExplain:
      'A banner in the stash is safe. A banner that flies in a village works there, but whoever conquers the village takes it, and enemy banner thieves can steal it.',
    /** M32b: the card about the banner thief. */
    thievesTitle: 'Banner thieves',
    thievesExplain:
      'Banner thieves go along on attacks. If the attack wins without conquering the village, the thieves that survive try to steal the banner flying there: the more survive, the better the chance, and a big Banner Hall in that village makes it harder. They come home either way.',
    toRecruit: 'Train banner thieves',
    thievesNeedHall: 'Banner thieves are trained in the Banner Hall.',
    /** 1.4: the paladin moved here from the barracks. */
    paladinHere: 'The paladin is trained here as well.',
    /** The link to the hall's recruiting tab in a world with the paladin (1.4). */
    toRecruitWithPaladin: 'Train the paladin and banner thieves',
    equip: 'Raise here',
    swap: 'Swap in',
    equipping: 'Raising…',
    equipLabel: (name: string) => `Raise ${name} in this village`,
    swapLabel: (name: string) => `Swap in ${name}`,
    needsHall: 'Needs a Banner Hall in this village.',
    villagesTitle: 'Your villages',
    hallShort: (level: number) => (level === 0 ? 'no hall' : `hall ${level}`),
    noBanner: 'no banner',
    open: (name: string) => `Open the Banner Hall of ${name}`,
    /** M32c: fusing three banners of a tier into one of the next. */
    fuseTitle: 'Fuse banners',
    fuseExplain:
      'Three banners of the same tier from your stash become one banner of the next tier. You choose which of the three types it takes. The three are gone; tier III cannot be fused. Banners fuse only with banners, paladin standards only with standards.',
    fuseNone: 'You need three banners of the same tier (I or II) in your stash.',
    fuseTier: (tier: number, count: number) => `Tier ${tierName(tier)} (${count})`,
    fusePick: (count: number, needed: number) => `Choose ${needed} banners of one tier: ${count} of ${needed} chosen.`,
    fuseTooFew: (needed: number) => `Fewer than ${needed}: nothing to fuse.`,
    fuseTypeLegend: 'The new banner becomes',
    fuseButton: (name: string, tier: number) => `Fuse into ${name}, tier ${tierName(tier)}`,
    fusing: 'Fusing…',
    fuseConfirmTitle: 'Fuse banners?',
    fuseConfirmText: (name: string, tier: number) =>
      `The three become one: ${name}, tier ${tierName(tier)}. They are gone for good.`,
    fuseConfirm: 'Fuse',
    fused: (name: string, tier: number) => `Fused: ${name}, tier ${tierName(tier)}, is now in your stash.`,
    selectLabel: (name: string) => `Choose ${name} for fusing`,
    /** 1.4 (02-game-rules §21.18): the paladin standards. */
    fuseStandardTier: (tier: number, count: number) => `Standards, tier ${tierName(tier)} (${count})`,
    standardSlotTitle: 'Paladin standard',
    standardExplain:
      'A standard goes with the paladins of this village: on an attack with a paladin, and on defence with every stack that has a paladin from here. It strengthens only the troops of that army or stack, flies beside the village banner and needs a Banner Hall in this village.',
    paladinLevel: (level: number) =>
      `Your paladin is at level ${level}: standards work up to tier ${tierName(level)}. Raise his level in the Banner Hall.`,
    standardTheft: 'If an attack carrying a standard is beaten, the defender’s surviving banner thieves may take it.',
    standardNoPaladins: 'This world has no paladin: standards stay in the stash and do nothing.',
    standardEmpty: 'No standard in this village.',
    standardEmptyHint: 'Equip one from your stash below.',
    standardCappedAt: (tier: number) => `Your paladin’s level caps it: the standard works as tier ${tierName(tier)}.`,
    standardLocked:
      'An attack is on its way here, or one with a paladin from this village is under way: the standard cannot be changed until it is over.',
    stashBanners: 'Village banners',
    stashStandards: 'Paladin standards',
    noStandard: 'no standard',
  },
  /** Dismissing units (02-game-rules §5.2a). */
  dismiss: {
    button: 'Dismiss',
    buttonLabel: (unit: string) => `Dismiss ${unit}`,
    title: (unit: string) => `Dismiss ${unit}`,
    atHome: (amount: string) => `${amount} at home`,
    explain:
      'The units leave your army at once, so they no longer defend and cannot be sent. They are then dismissed one by one. Nothing is refunded; every dismissed unit frees its population.',
    perUnit: (time: string) => `Time per unit: ${time}`,
    countLabel: (unit: string) => `Number of units to dismiss: ${unit}`,
    total: (count: string, time: string) => `${count} units take ${time} in all.`,
    confirm: 'Dismiss units',
    keep: 'Keep them',
    queueFull: 'The dismiss queue of this building is full.',
    queueTitle: 'Dismissing',
    queueTitleCount: (count: number, max: number) => `Dismissing (${count}/${max})`,
    order: (count: string, unit: string) => `${count} × ${unit}`,
    cancel: (order: string) => `Cancel dismissing ${order}`,
    cancelTitle: 'Stop dismissing?',
    cancelText: (order: string) => `${order} are not dismissed any more.`,
    cancelNote: 'Units that are already gone stay gone; the rest come back into your army at once.',
    locked: 'An attack is on its way: dismissing cannot be stopped until it has arrived.',
  },
  recruitQueue: {
    title: 'Recruitment queue',
    titleCount: (count: number, max: number) => `Recruitment queue (${count}/${max})`,
    empty: 'Nobody is in training here.',
    /** "12 × Spearman"; the count is already formatted. */
    order: (count: string, unit: string) => `${count} × ${unit}`,
    done: (done: string, count: string) => `${done}/${count} done`,
    /** Followed by a countdown: "next in 0:02:13". */
    nextIn: 'next in',
    cancel: (order: string) => `Cancel ${order}`,
    cancelText: (order: string) => `The training of ${order} stops.`,
    cancelRefund:
      'Units that are done stay in the village. You get the resources back for the rest, as far as the warehouse has room.',
  },
  /** The units card on the village overview. */
  training: {
    title: 'In training',
    empty: 'Nobody is in training.',
    /** Orders queued behind the running ones (all buildings together). */
    waiting: (count: number) => (count === 1 ? '+1 more order waiting' : `+${count} more orders waiting`),
    toRecruit: 'Open recruitment',
  },
  /** The count field with − / + and "max" (UnitCountInput). */
  unitCount: {
    less: 'One less',
    more: 'One more',
    /** Also the button's accessible name, so voice control finds it by what it says. */
    max: (amount: string) => `max ${amount}`,
  },
  /** The map screen (05-frontend §6). */
  map: {
    title: 'Map',
    /** Name of the canvas for screen readers and keyboard users. */
    canvasLabel:
      'Map. Drag or use the arrow keys to move; pinch, scroll or press + and − to zoom. Tap a village, or press Enter for the village in the middle.',
    goToLabel: 'Go to coordinates',
    goTo: 'Go',
    goToHint: 'For example 48|52 or 48.52',
    invalidCoords: 'Enter coordinates like 48|52.',
    offMap: (max: number) => `That field is not on the map (0 to ${max}).`,
    centre: 'Centre on my village',
    zoomIn: 'Zoom in',
    zoomOut: 'Zoom out',
    loading: 'Loading the map…',
    loadError: 'Part of the map could not be loaded.',
    legend: 'Colours on the map',
    // the side column's folded legend (1.3)
    legendFold: 'Legend',
    legendKinds: {
      own: 'Your villages',
      player: 'Players',
      npc: 'NPC lords',
      barbarian: 'Barbarians',
    } satisfies Record<VillageKind, string>,
    barbarianVillage: 'Barbarian village',
    owner: 'Owner',
    noOwner: 'nobody (barbarians)',
    /** The village panel of a barbarian village. */
    barbarianHint:
      'Barbarians never attack, build or train troops, but their mines slowly grow on their own, so they are worth raiding again and again.',
    /** Behind the owner's name. */
    youTag: 'you',
    npcTag: 'NPC lord',
    /** v1.3: a banner keep (02-game-rules §22), a strong barbarian village that counts for victory. */
    keep: 'Banner keep',
    keepValue: 'counts for victory',
    points: 'Points',
    loyalty: 'Loyalty',
    /** Loyalty of a village that is not yours and that you have not attacked (M21). */
    loyaltyUnknown: 'unknown',
    loyaltyUnknownHint: 'You see the loyalty of villages you have attacked. Attack this one to find out.',
    /** Behind a loyalty value of a village you attacked: when you saw it (it has grown back since). */
    loyaltySeen: (when: string) => `seen ${when}`,
    /** v1.3: the banner chance against this village's owner (or this barbarian village) is used. */
    bannerChance: 'Banner chance',
    bannerChanceUsed: (when: string) => `used · back at ${when}`,
    bannerChanceLegend: 'Your banner chance used',
    /** The player's own note on a village (1.4): panel, sheet, map legend. */
    note: {
      write: 'Write a note',
      edit: (note: string) => `Your note: ${note}. Change it`,
      title: (village: string) => `Note · ${village}`,
      label: 'Your note',
      placeholder: 'e.g. wall 3, spears only, good to farm',
      onlyYou: 'Only you can see this note.',
      count: (length: string, max: string) => `${length} / ${max}`,
      delete: 'Delete',
      legend: 'Your note',
    },
    distance: 'Distance',
    fields: (amount: string) => `${amount} fields`,
    currentVillage: 'Your current village',
    /** Read out to screen readers while the map is moved with the keyboard. */
    middle: (coords: string) => `Middle of the map: ${coords}`,
    middleVillage: (coords: string, name: string) => `Middle of the map: ${coords}, ${name}`,
    /** The panel's actions whose screens come in later milestones. */
    comingSoon: (actions: string) => `Coming soon: ${actions}`,
    actions: {
      open: 'Open village',
      attack: 'Attack',
      support: 'Support',
      farm: 'Add to farm list',
      sendResources: 'Send resources',
      profile: 'Player profile',
    } satisfies Record<MapActionKey, string>,
    /** The overview map (M11b): a broad area around the village, the detailed map's part as a rectangle. */
    overviewTitle: 'Overview map',
    overviewLabel:
      'Overview map. Click or tap a spot to move the map there; drag the rectangle to move the map, drag elsewhere or use the arrow keys to move the overview. Enter moves the map to the middle of the overview.',
    /** Phones: the toolbar button that switches between the detailed map and the overview. */
    overviewToggle: 'Overview map',
    layer: {
      label: 'Map colours',
      owner: 'Owners',
      guild: 'Guilds',
      legend: 'Guilds on this map',
      noGuilds: 'No guild has villages in this area.',
      moreGuilds: (count: number) => `and ${count} more`,
    },
    overviewMove: {
      left: 'Move the overview left',
      up: 'Move the overview up',
      down: 'Move the overview down',
      right: 'Move the overview right',
    },
    overviewHome: 'Overview back to my village',
    /** The movement markers (M26): the player's own attacks and supports, and what comes to his villages. */
    movements: {
      toggle: 'Show troop movements',
      legend: {
        attack: 'Attacks',
        noble: 'Your attacks with nobles',
        support: 'Supports',
        return: 'Your troops coming home',
      } satisfies Record<MarkerKind, string>,
      /** The village panel. */
      onTheWay: 'On the way',
      attacks: (count: number) => (count === 1 ? '1 attack' : `${count} attacks`),
      withNobles: (count: number) => `(${count} with nobles)`,
      supports: (count: number) => (count === 1 ? '1 support' : `${count} supports`),
      next: (when: string) => `next arrives ${when}`,
      /** The panel's row for the player's own troops walking home to the village (M36). */
      comingHome: 'Coming home',
      returns: (count: number) => (count === 1 ? '1 return' : `${count} returns`),
      nextHome: (when: string) => `first back ${when}`,
    },
    /** The movement lines (v1.3): a switch of their own, and the bubble of a tapped line. */
    lines: {
      toggle: 'Show movement lines',
      legend: 'Lines: solid behind the troops, dotted ahead',
      legendHostile: 'Attack on you: dashed',
      layers: 'Map layers',
      markersRow: 'Troops at villages',
      markersHint: 'A badge on each village your troops are heading to',
      linesRow: 'Movement lines',
      linesHint: 'Every movement as a line from start to target',
      legendTitle: 'Legend',
      attackOn: (target: string) => `Attack on ${target}`,
      nobleAttackOn: (target: string) => `Attack with nobles on ${target}`,
      supportFor: (target: string) => `Support for ${target}`,
      returnTo: (home: string) => `Coming home to ${home}`,
      attackFrom: (source: string) => `Attack from ${source}`,
      supportFrom: (source: string) => `Support from ${source}`,
      fieldsLeft: (fields: string) => `${fields} fields to go`,
      positionHidden: 'Where this attack is right now stays hidden.',
      arrives: 'Arrives',
      way: 'Way',
      troops: 'Troops',
      hidden: 'hidden',
      toRally: 'Rally point',
      showTarget: 'Show target',
      showAttacker: 'Show attacker',
      showStart: 'Show start',
    },
    /** Desktop side column while no village is selected. */
    pickHint: 'Click a village on the map to see it here.',
    panelColumnLabel: 'Selected village',
  },
  /** The rally point (05-frontend §3): sending troops, movements, troops in and from the village. */
  rally: {
    title: 'Rally Point',
    sendTitle: 'Send troops',
    /** The switch on phones and tablets (1.3): one part of the page at a time. */
    partsLabel: 'Show',
    partSend: 'Send',
    /** Followed by the number of movements, if any. */
    partMoves: 'On the way',
    partTroops: 'Troops',
    target: 'Target',
    targetHint: 'Coordinates, e.g. 48|52 or 48.52',
    targetLoading: 'Looking for the village…',
    noVillageThere: 'There is no village on this field.',
    thisVillage: 'This is the village the troops would leave from.',
    countLabel: (unit: string) => `Number of units to send: ${unit}`,
    /** The chip of a unit row: every unit of that kind at home. */
    all: (amount: string) => `all ${amount}`,
    allTroops: 'All troops',
    clear: 'Clear',
    noTroops: 'No troops at home.',
    attack: 'Attack',
    support: 'Support',
    previewTitle: { ATTACK: 'Send an attack?', SUPPORT: 'Send support?' } satisfies Record<CommandKind, string>,
    checking: 'Checking…',
    travelTime: 'Travel time',
    arrival: 'Arrival',
    catapultTarget: 'Catapults aim at',
    endsProtection: 'Attacking ends your own beginner protection.',
    /** The night bonus (1.4): the attack would arrive at night at an owned village. */
    nightBonus: (factor: string) => `It arrives during the night bonus: the village defends ×${factor}.`,
    /** Morale (1.6) with today's points: "48%" */
    morale: (percent: string) =>
      `Morale ${percent}: the owner has far fewer points than you, so your attack hits with ${percent} of its strength.`,
    /** The moon tag on an attack that arrives at night (1.4). */
    nightTag: (factor: string) => `Night bonus ×${factor}`,
    paladinLeads: 'The paladin leads this support: all units walk at his speed.',
    back: 'Back',
    confirm: { ATTACK: 'Send attack', SUPPORT: 'Send support' } satisfies Record<CommandKind, string>,
    sending: 'Sending…',
    /** 1.6: the switch on the confirm sheet, and the bell on an outgoing attack. */
    notifyReturn: 'Notify me when they’re back',
    notifyReturnFor: (attack: string) => `Notify me when they’re back (${attack})`,
    /** Attack train (M37): several attacks at one target with one confirm. */
    addToTrain: 'Add to train',
    attackTrain: (count: number) => `Attack (${count})`,
    trainTitle: 'Attack train',
    trainCount: (count: number, max: number) => `${count} of ${max} attacks`,
    trainAttack: (position: number) => `Attack ${position}`,
    trainRemove: (position: number) => `Remove attack ${position} from the train`,
    trainHint:
      'Attack sends these, then the troops picked below as the last attack. They land one after another in this order.',
    trainFull: 'The train is full: Attack sends these attacks.',
    trainPace:
      'The whole train walks at the pace of its slowest unit, and the attacks land one after another in this order.',
    trainPreviewTitle: (count: number) => `Send ${count} attacks?`,
    confirmTrain: (count: number) => `Send ${count} attacks`,
    sentTrain: (count: number, target: string, time: string) =>
      `${count} attacks on ${target} sent – they arrive at ${time}.`,
    sent: {
      ATTACK: (target: string, time: string) => `Attack on ${target} sent – arrives at ${time}.`,
      SUPPORT: (target: string, time: string) => `Support for ${target} sent – arrives at ${time}.`,
    } satisfies Record<CommandKind, (target: string, time: string) => string>,
    outgoingTitle: 'Outgoing',
    noOutgoing: 'No commands on the way.',
    incomingTitle: 'Incoming',
    noIncoming: 'Nothing is on its way here.',
    /** Followed by the village: "Attack on Hilltop (48|52)". */
    outgoing: {
      ATTACK: 'Attack on',
      SUPPORT: 'Support for',
      RETURN: 'Return to',
    } satisfies Record<MovementKind, string>,
    incoming: {
      ATTACK: 'Attack from',
      SUPPORT: 'Support from',
      RETURN: 'Returning from',
    } satisfies Record<MovementKind, string>,
    arrivesAt: (time: string) => `arrives at ${time}`,
    /** Once above the incoming list: other players' attacks don't show their units or start (1.3). */
    attacksHidden: 'Troops and start of other players’ attacks stay hidden.',
    /** In place of the hidden units: the attacker's village on the map. */
    onMap: 'On the map',
    /** Followed by the time left to cancel: "Cancel 0:04:12". */
    cancel: 'Cancel',
    cancelTitle: 'Cancel this command?',
    cancelReturn: (time: string) => `The troops turn around and are back home at ${time}.`,
    /** Followed by a countdown. */
    cancelLeft: 'It can be cancelled for another',
    confirmCancel: 'Cancel command',
    troopsTitle: 'Troops',
    atHome: 'At home',
    /** The troop overview (M33): the village's own units at home, away and in all, plus support here. */
    overviewTitle: 'Overview',
    overviewCaption: 'All troops of this village, at home and away, and the support standing here',
    overviewUnit: 'Unit',
    overviewOutside: 'Away',
    overviewTotal: 'Total',
    overviewSupport: 'Support',
    overviewOutsideHint: 'Away: in other villages or on the way (attacks, supports, returns).',
    overviewSupportHint: 'Support: troops of other villages standing here; they are not in the total.',
    noTroopsAnywhere: 'This village has no troops, neither here nor elsewhere.',
    supportsHere: 'Support in this village',
    noSupportsHere: 'No troops of other villages are here.',
    elsewhere: 'In other villages',
    noElsewhere: 'None of this village’s troops are elsewhere.',
    sendBack: 'Send back',
    withdraw: 'Withdraw',
    sendBackTitle: 'Send troops back',
    withdrawTitle: 'Withdraw troops',
    sendBackText: (village: string) => `These troops leave this village and walk home to ${village}.`,
    withdrawText: (village: string) => `These troops leave ${village} and walk home.`,
    returnCountLabel: (unit: string) => `Number of units: ${unit}`,
  },
  /** The attack simulator at the rally point (M23, 02-game-rules §20). */
  simulator: {
    title: 'Attack simulator',
    intro:
      'Try an attack on paper: type numbers in, take them from this village or from a report. Nothing is sent and nothing changes.',
    back: 'Rally Point',
    attackerTitle: 'Attacker',
    defenderTitle: 'Defender',
    attackerCount: (unit: string) => `Attacking: ${unit}`,
    defenderCount: (unit: string) => `Defending: ${unit}`,
    /** Fills the attacker with the units at home in this village. */
    myUnits: 'My units here',
    /** Fills the defender with this village: its troops, the support standing in it and its wall. */
    thisVillage: 'This village',
    clear: 'Clear',
    /** on phones, after a side was filled in (1.6 UI review) */
    showAllUnits: (more: number) => `Show all units (${more} more)`,
    reportTitle: 'Report',
    levelLabel: (unit: string) => `Level of ${unit}`,
    /** Hall training (1.4): the paladin's and the thieves' levels in a world without unit levels. */
    hallLevelsHint:
      'The paladin and banner thieves have levels from Hall training: choose theirs. “My units here”, “This village” and reports bring your own levels.',
    levelsHint:
      'Unit levels are on in this world: choose the level of each unit. “My units here”, “This village” and reports bring the levels of the real units.',
    reportLabel: 'Take numbers from a report',
    reportPlaceholder: 'Choose a report…',
    reportsLoading: 'Loading reports…',
    noReports: 'You have no battle reports yet.',
    reportLoading: 'Loading the report…',
    /** One entry of the report list: "Scouting · Hilltop → Marsh · Tue 21:14". */
    reportOption: (kind: string, from: string, to: string, when: string) => `${kind} · ${from} → ${to} · ${when}`,
    /** Under the report list after one was taken. */
    reportTaken: 'Numbers taken from the report as they were before that battle.',
    reportTakenSpy:
      'Numbers taken from the report. A scouting report has no army of yours, so the attacker stays as it is.',
    reportTakenPartly: 'Nobody came back from that attack, so the report did not tell the defender’s numbers.',
    wall: 'Wall level',
    loyalty: 'Loyalty of the village',
    loyaltyHint: 'Only matters if nobles survive.',
    catapultTarget: 'Catapults aim at',
    catapultLevel: (building: string) => `${building} level`,
    simulate: 'Simulate',
    simulating: 'Simulating…',
    needUnits: 'Type in at least one attacking unit.',
    resultsTitle: 'Result',
    resultsHint:
      'The same battle three times: with the worst luck, with none and with the best luck this world allows. Real battles are the same calculation.',
    luckTitle: {
      worst: 'Worst luck',
      none: 'No luck',
      best: 'Best luck',
    },
    verdicts: {
      ATTACKER_WON: 'The attacker wins.',
      DEFENDER_WON: 'The defenders hold.',
      SCOUTS_ONLY: 'Only scouts attack: there is no battle.',
    },
    attackStrength: 'Attack',
    defenseStrength: 'Defence',
    wallInFight: 'Wall in the fight',
    scoutsSent: 'Scouts sent',
    scoutsDefending: 'Scouts in the village',
    scoutsLost: 'Scouts lost',
    scoutsSucceeded: 'Scouting succeeds',
    scoutsFailed: 'Scouting fails',
    lootCapacity: 'Carrying capacity',
    loyaltyOutlook: 'Loyalty',
    /** "2 nobles: 100 → 30 to 60" (every noble takes 20 to 35). */
    loyaltyRange: (nobles: number, before: string, highest: string, lowest: string) =>
      `${nobles} noble${nobles === 1 ? '' : 's'}: ${before} → ${lowest === highest ? lowest : `${lowest} to ${highest}`}`,
    conquest: {
      NO: 'The village stays.',
      POSSIBLE: 'The village may fall, depending on the nobles’ hits.',
      YES: 'The village falls.',
    },
    attackerTable: 'Attacker units',
    defenderTable: 'Defender units',
    yes: 'yes',
    no: 'no',
    /** The button on a battle report. */
    simulateReport: 'Simulate this attack',
    /** M32c: banners in the simulator. */
    warBanner: 'War banner of the attacking village',
    bulwarkBanner: 'Bulwark banner in the village',
    plunderBanner: 'Plunder banner of the attacking village',
    oathBanner: 'Oath banner in the village',
    bannerHall: 'Banner Hall level',
    bannerHallHint: 'Caps the Bulwark and the Oath and makes theft harder.',
    noBanner: 'none',
    /** "Tier II (+6 %)"; the percent comes with its sign */
    bannerTierOption: (tier: number, percent: string) => `Tier ${tierName(tier)} (${percent})`,
    bulwarkCapped: (tier: number) =>
      tier === 0 ? 'Without a Banner Hall the banner sleeps.' : `The hall lets it work as tier ${tierName(tier)}.`,
    warBonus: 'War banner',
    bulwarkBonus: 'Bulwark banner',
    plunderBonus: 'Plunder banner',
    oathBonus: 'Oath banner',
    thievesSurvive: 'Banner thieves surviving',
    /** "8 of 10" */
    ofSent: (survived: string, sent: string) => `${survived} of ${sent}`,
    theftChance: 'Chance to steal the banner',
    theftHint: 'If a banner flies there. A battle that conquers the village takes the banner with it anyway.',
    /** 1.4 (02-game-rules §21.18): the paladin standards. */
    attackerStandard: 'Paladin standard of the attack',
    defenderStandard: 'Paladin standard of the defenders',
    standardTier: 'Its tier',
    standardHint:
      'A standard works only with a paladin on that side, at most at his level; the defenders’ needs a Banner Hall.',
    /** The result: "Axe standard: +5 % (works as tier I)" */
    standardWorks: (percent: string, tier: number) => `${percent} (works as tier ${tierName(tier)})`,
    standardAsleep: 'asleep',
    /** 1.4 (02-game-rules §8.1): the switch in the defenders' card, "×2" */
    night: (factor: string) => `The attack arrives at night (defence ×${factor})`,
    /** "23:00–07:00" */
    nightHint: (hours: string) =>
      `The world’s night bonus: ${hours} German time. It doesn’t count in barbarian villages.`,
    nightResult: 'Night bonus',
    /** 1.6 (02-game-rules §8.2): the attacker's morale in percent */
    morale: 'Attacker’s morale (%)',
    moraleHint: (floor: string) =>
      `100 unless the defender has less than about a quarter of the attacker’s points; this world’s lowest is ${floor}.`,
    moraleResult: 'Morale',
    defenderThievesSurvive: 'Defenders’ banner thieves surviving',
    standardTheftChance: 'Chance they take the standard',
  },
  /** Reports (05-frontend §3): the list and one report. */
  /** The market (05-frontend §3): send resources, offers, own offers, merchants on their way. */
  market: {
    title: 'Market',
    merchants: 'Merchants',
    /** "3 of 5 free" (merchants), also "2 of 5" for the merchants a send needs. */
    ofFree: (amount: string, total: string) => `${amount} of ${total}`,
    noMarket: 'This village has no market yet, so it has no merchants.',
    toTownHall: 'Build one in the Town Hall',
    tabs: {
      label: 'Market sections',
      send: 'Send',
      offers: 'Offers',
      mine: 'Own',
      exchange: 'Exchange',
      merchants: 'Merchants',
    },
    sendTitle: 'Send resources',
    ownVillages: 'Own villages',
    ownVillagesTitle: 'Send to an own village',
    amountLabel: (resource: string) => `Amount to send: ${resource}`,
    total: 'Total',
    merchantsNeeded: 'Merchants needed (free)',
    travelTime: 'Travel time',
    arrival: 'Arrival',
    send: 'Send',
    sending: 'Sending…',
    sent: (target: string, when: string) => `Merchants on their way to ${target} – they arrive at ${when}.`,
    offersTitle: 'Offers of other players',
    sortLabel: 'Sort offers',
    sortNear: 'Nearest',
    sortBest: 'Best deal',
    noOffers: 'Nobody offers anything right now.',
    /** An offer can be accepted this many times: "× 3". */
    times: (amount: string) => `× ${amount}`,
    for: ' for ',
    accept: 'Accept',
    accepting: 'Accepting…',
    acceptTitle: 'Accept the offer',
    countLabel: 'How many times',
    maxUnits: (amount: string) => `max ${amount}`,
    youPay: 'You pay',
    youGet: 'You get',
    goodsArrive: 'The goods arrive',
    cannotAccept: 'You cannot pay for even one unit with your resources and free merchants.',
    accepted: (when: string) => `Accepted – the goods arrive at ${when}.`,
    createTitle: 'Create an offer',
    iGive: 'I give',
    iWant: 'I want',
    times1: 'How many times',
    offerAmountLabel: 'Amount you give per unit',
    wantAmountLabel: 'Amount you want per unit',
    ratioHint: (min: string, max: string) => `You can ask between ${min} and ${max} for this amount.`,
    merchantsReserved: 'Merchants reserved (free)',
    create: 'Create offer',
    creating: 'Creating…',
    created: 'Offer created – the goods and the merchants are reserved for it.',
    ownTitle: 'My offers',
    noOwnOffers: 'This village has no open offers.',
    timesLeft: (amount: string) => `${amount} left`,
    cancel: 'Cancel',
    cancelHint: 'Cancelling gives back what is left, as far as the warehouse has room.',
    outgoingTitle: 'Merchants sent',
    noOutgoing: 'No merchants are away.',
    incomingTitle: 'Merchants coming here',
    noIncoming: 'No merchants are on their way here.',
    merchantsTo: 'Merchants to',
    merchantsFrom: 'Merchants from',
    goods: 'Goods',
    delivered: 'Delivered',
    homeIn: 'home in',
    merchantCount: (amount: string, one: boolean) => (one ? `${amount} merchant` : `${amount} merchants`),
    /** v1.3: trading with the market itself, at a rate the world sets. */
    exchangeTitle: 'Trade with the market',
    exchangeText: (ratio: string) =>
      `The market gives 1 of a resource for ${ratio} of another, at once and without merchants.`,
    iGet: 'I get',
    exchangeAmountLabel: 'Amount you get',
    exchangeSame: 'Choose two different resources.',
    /** "Iron left today: 3,200 of 5,000". */
    exchangeLeft: (resource: string) => `${resource} left today`,
    exchangeResets: 'New amounts at',
    exchange: 'Trade',
    exchanging: 'Trading…',
    exchanged: 'Traded – the resources are in the warehouse.',
    exchangeHint: 'The daily amount of each resource grows with the market level and counts what you get.',
  },
  reports: {
    title: 'Reports',
    /** Desktop (1.3): next to the list until a report is opened, and the column's name for screen readers. */
    pickReport: 'Pick a report on the left to read it here.',
    openReport: 'Open report',
    /** The filter chips above the list (1.3), each with its count. */
    filtersLabel: 'Show reports',
    filters: {
      all: 'All',
      ATTACKS: 'Attacks',
      DEFENSE: 'Defence',
      SCOUTING: 'Scouting',
      SUPPORT: 'Support',
      TRADE: 'Trade',
    },
    emptyFilter: 'No reports of this kind.',
    /** The day headings of the list (1.3); older days show their date. */
    today: 'Today',
    yesterday: 'Yesterday',
    /** The third line of a battle in the list (1.3). */
    lineLosses: 'Losses:',
    lineNoLosses: 'no losses',
    /** The attackers carried all they could: there was more to take. */
    fullHaul: 'full haul',
    /** Before the resources the attackers took from the reader's village. */
    lineTaken: 'Taken:',
    /** Under the list's title: "3 unread · 42 reports". */
    unreadCount: (count: number) => `${count} unread`,
    total: (count: number) => (count === 1 ? '1 report' : `${count} reports`),
    /** Screen-reader text of the number on the navigation's Reports entry. */
    unreadBadge: (count: number) => (count === 1 ? '1 unread report' : `${count} unread reports`),
    /** Screen-reader note on an unread line of the list. */
    unread: 'unread',
    /** For screen readers on a line whose attack had nobles (1.4.1; the icon has a gold ring). */
    withNobles: 'with nobles',
    markAllRead: 'Mark all as read',
    /** M38: deletes every report of the list after one confirm. */
    deleteAll: 'Delete all',
    /** 1.2.3: deletes the own attack and scout reports on barbarian villages, except new banners. */
    deleteBarbarian: 'Delete barbarian reports',
    /** 1.3: the list's select mode (it replaced the delete button on every line). */
    select: 'Select',
    selectDone: 'Done',
    selectedCount: (count: number) => `${count} selected`,
    selectAll: 'Select all',
    selectNone: 'Select none',
    /** M38: on a battle report, the other side's village for the current village's farm list. */
    addToFarm: (village: string) => `Add ${village} to the farm list`,
    onFarmList: (village: string) => `${village} is on the farm list`,
    /** M40: sending a copy of a report to other players. */
    send: 'Send',
    sendReport: (title: string) => `Send report: ${title}`,
    sendTitle: 'Send report',
    sendText: 'Each player gets a copy in their reports that says you sent it.',
    sendNames: 'Players',
    sendNamesHint: 'Names separated by commas, at most 10.',
    /** Quick picks above the field: the players last sent a report to, then those of the inbox. */
    sendRecent: 'Recently contacted',
    sendTooMany: (max: number) => `At most ${max} players at once.`,
    /** One button that sends to every other member of the player's guild, so no name is needed. */
    sendGuild: 'Send to guild',
    sendGuildText: (tag: string, others: number) =>
      `Your guild [${tag}]: ${others} other ${others === 1 ? 'member' : 'members'}`,
    sentTo: (names: string) => `Sent to ${names}.`,
    /** After a guild send that reached nobody (all left out: blocked, or too many unread). */
    sentToNobody:
      'Sent to nobody: you blocked the other members of your guild, or they have too many unread reports from you.',
    /** An error that names the player the send stopped at (nothing was sent). */
    sendErrors: {
      PLAYER_NOT_FOUND: (name: string) => `There is no player called ${name}. Nothing was sent.`,
      OWN_PLAYER: (name: string) => `${name} is you: you can’t send a report to yourself. Nothing was sent.`,
      PLAYER_BLOCKED: (name: string) => `You blocked ${name}. Unblock them to send them reports. Nothing was sent.`,
      INBOX_FULL: (name: string) =>
        `${name} has too many unread reports from you. Wait until they read some. Nothing was sent.`,
    },
    /** A copy someone sent (M40): above the report, and "from …" in the list. */
    sharedBy: (name: string) => `Sent by ${name}`,
    sharedView: (owner: string) => `This is ${owner}’s report: “you” and “your” in it mean ${owner}.`,
    sharedFrom: (name: string) => `from ${name}`,
    deleteAllTitle: 'Delete all reports?',
    deleteAllText: (total: number, unread: number) =>
      `${total === 1 ? 'Your report' : `All ${total} reports`}${unread > 0 && total > 1 ? ` (${unread} of them unread)` : ''} will be deleted. This can’t be undone. Reports that arrive from now on stay.`,
    deleteBarbarianTitle: 'Delete barbarian reports?',
    deleteBarbarianText: (count: number) =>
      `${count === 1 ? 'Your report on an attack or scouting of a barbarian village' : `Your ${count} reports on attacks and scouting of barbarian villages`} will be deleted. Reports with a new banner or with nobles stay, as do reports other players sent you and reports that arrive from now on. This can’t be undone.`,
    empty: 'No reports yet. They appear here when your troops fight or a support arrives.',
    emptyPage: 'There are no reports on this page any more.',
    unreadableText: 'This report can’t be shown any more. You can delete it.',
    toFirstPage: 'Go to the newest reports',
    pagesLabel: 'Report pages',
    newer: 'Newer',
    older: 'Older',
    page: (page: number, pages: number) => `Page ${page} of ${pages}`,
    delete: 'Delete',
    deleteReport: (title: string) => `Delete report: ${title}`,
    /** The delete button after the first tap; a second tap deletes. */
    reallyDelete: 'Really delete?',
    back: 'All reports',
    kinds: {
      ATTACK: 'Attack',
      DEFENSE: 'Defence',
      SCOUT: 'Scouting',
      SUPPORT_ARRIVED: 'Support arrived',
      SUPPORT_RETURNED: 'Support returned',
      CONQUEST: 'Village conquered',
      VILLAGE_LOST: 'Village lost',
      TRADE_ARRIVED: 'Merchants arrived',
    } satisfies Record<ReportKind, string>,
    results: { won: 'Won', lost: 'Lost' },
    /** Next to the result in the list: the reader gained a banner there (found, stolen, conquered). */
    bannerGained: (count: number): string => (count === 1 ? 'New banner' : 'New banners'),
    /** Village names are already in the texts; barbarian villages are "Barbarian village". */
    titles: {
      battle: (from: string, to: string) => `${from} attacks ${to}`,
      scout: (from: string, to: string) => `${from} scouts ${to}`,
      supportArrived: (from: string, to: string) => `Support from ${from} arrived in ${to}`,
      supportSentBack: (to: string) => `${to} sent your support home`,
      supportWithdrawn: (from: string, to: string) => `${from} withdrew its support from ${to}`,
      conquest: (from: string, to: string) => `${from} conquered ${to}`,
      tradeArrived: (from: string, to: string) => `Merchants from ${from} arrived in ${to}`,
      /** A stored report the server can't read any more (it can still be deleted). */
      unreadable: 'Report that can’t be shown',
    },
    /** The line under a battle's title, from the reader's side (game/reports.ts battleVerdict). */
    verdicts: {
      attackWon: 'Victory – the defenders were beaten.',
      attackLost: 'Defeat – your troops were beaten.',
      defenseWon: 'Victory – the attack was beaten off.',
      defenseLost: 'Defeat – the attackers won.',
      scoutsThrough: 'Your scouts got through.',
      scoutsCaught: 'Your scouts were caught. None of them came back.',
      enemyScoutsThrough: 'Enemy scouts got through. They saw the resources, buildings and troops.',
      enemyScoutsCaught: 'The enemy scouts were caught.',
      thievesAlone: 'No battle: banner thieves can’t fight, so on their own they turn back.',
      enemyThievesAlone: 'Enemy banner thieves came alone and turned back without a battle.',
    },
    attacker: 'Attacker',
    defender: 'Defender',
    unit: 'Unit',
    sent: 'Sent',
    present: 'There',
    lost: 'Lost',
    survived: 'Survived',
    noDefenders: 'No troops defended the village.',
    levelsScouted: 'Your upgraded scouts saw the unit levels: the highest level of each unit is shown.',
    nobodyCameBack: 'None of your troops came back, so you learned nothing about the defenders.',
    /** 1.4: "×2" */
    nightBonus: (factor: string) => `Night bonus: the attack arrived at night, the village defended ×${factor}.`,
    /** 1.6: the attacker's morale, below 100% only, in the send sheet's words (the reader attacked) */
    moraleAttack: (percent: string, owner: string | null) =>
      `Morale ${percent}: ${owner ?? 'the owner'} has far fewer points than you, so your attack hit with only ${percent} of its strength.`,
    /** the same in the defenders' copy */
    moraleDefence: (percent: string, owner: string | null, attacker: string | null) =>
      `Morale ${percent}: ${owner ?? 'the owner'} has far fewer points than ${attacker ?? 'the attacker'}, so the attack hit with only ${percent} of its strength.`,
    luck: 'Luck',
    catapultTarget: 'Catapults aimed at',
    wall: 'Wall',
    level: (level: number) => `Lv ${level}`,
    levelChange: (before: number, after: number) => `Lv ${before} → ${after}`,
    byVillage: 'Troops by village',
    /** Before the units a village lost: "Lost: …". */
    stackLosses: 'Lost:',
    noLosses: 'No losses',
    loot: 'Loot',
    plundered: 'Plundered',
    nothingTaken: 'Nothing was taken.',
    scouting: 'Scouting',
    scoutedResources: 'Resources (before the loot)',
    scoutedBuildings: 'Buildings',
    scoutedBanner: 'Banner',
    scoutedStandard: 'Paladin standard in the village',
    /** Balancing round 2: the owner's own units in support elsewhere or on the way; only when at least 3 in 4 scouts survived. */
    scoutedAway: 'Away from the village',
    scoutedAwayNone: 'None of the owner’s troops are away from the village.',
    /** The scouted troops are the defender's table; this says so. */
    scoutedTroops: 'The troops they saw are in the defender’s table.',
    scoutsFailed: 'Your scouts were caught and saw nothing.',
    loyalty: 'Loyalty',
    loyaltyChange: (before: number, after: number) => `${before} → ${after}`,
    /** Under the verdict of the battle that took the village. */
    conqueredInBattle: 'The village was conquered!',
    lostInBattle: 'The village was lost to the attacker.',
    /** The CONQUEST / VILLAGE_LOST report. */
    conquestWon: 'The village is yours now.',
    conquestLost: 'Your village was conquered. Its troops outside it and its build and recruit orders are lost.',
    conquestVillage: 'Village',
    conquestFrom: 'Nobles from',
    newName: 'Its name now',
    toVillage: 'Open the village',
    supportUnits: 'Units',
    supportFrom: 'From',
    supportTo: 'To',
    tradeGoods: 'Delivered',
    tradeLostToWarehouse: 'What did not fit into the warehouse was lost:',
    tradeNothingLost: 'Everything fit into the warehouse.',
    /** M32b: the banner block of a battle and the banner of a conquest (02-game-rules §21). */
    bannersTitle: 'Banners',
    warBonus: 'War banner of the attacker',
    bulwarkBonus: 'Bulwark banner of the defender',
    bonus: (percent: string) => `+${percent}`,
    bannerFound: 'You found a banner',
    thieves: 'Banner thieves',
    thievesCount: (sent: string, survived: string) => `${sent} sent, ${survived} survived`,
    theftChance: 'Chance to steal',
    /** What the thieves did; `banner` is "War banner, tier II" (only STOLEN and FAILED use it). */
    theft: {
      STOLEN: (banner: string) => `Your thieves stole the ${banner}! It is in your stash now.`,
      FAILED: (banner: string) => `Your thieves tried, but the ${banner} stayed where it was.`,
      NO_BANNER: () => 'No banner flew in the village.',
      CONQUERED: () => 'The village was conquered: its banner goes with the conquest.',
      NO_SURVIVORS: () => 'None of your thieves survived.',
      BATTLE_LOST: () => 'The attack was beaten off: your thieves had no chance.',
    } satisfies Record<TheftResult, (banner: string) => string>,
    bannerStolen: (banner: string) => `Enemy banner thieves stole your ${banner}.`,
    /** 1.4 (02-game-rules §21.18): the paladin standards in a battle; `standard` is "Axe standard, tier II". */
    attackStandard: 'Standard of the attack',
    defenderStandard: 'Standard of the defenders',
    standardState: (standard: string, effective: number, tier: number) =>
      effective === 0
        ? `${standard}: asleep`
        : effective < tier
          ? `${standard}: works as tier ${tierName(effective)}`
          : standard,
    standardFound: 'You found a paladin standard',
    standardSurvivors: (survived: string) => `${survived} survived`,
    standardTheftChance: 'Chance to take the standard',
    standardTaken: (standard: string) =>
      `Your banner thieves took the attacker’s ${standard}! It is in your stash now.`,
    standardKept: (standard: string) => `Your banner thieves could not take the attacker’s ${standard}.`,
    standardLost: (standard: string) => `The defenders’ banner thieves took your ${standard}.`,
    conquestStandard: 'Paladin standard',
    conquestBanner: 'Banner',
    conquestBannerToStash: 'It is in your stash now.',
    conquestBannerAsleep: 'It stays in the village, asleep while banners are switched off.',
    lostBannerTaken: 'The conqueror took it.',
    lostBannerStays: 'It stays in the village with its new owner.',
  },
  /** Live notifications (M9b): toasts, the red counter of incoming attacks, the connection. */
  realtime: {
    yourVillage: 'your village',
    incomingAttack: (target: string, from: string, player: string | null, arrival: string) =>
      player === null
        ? `Attack on ${target} from ${from} – arrives ${arrival}`
        : `${player} attacks ${target} from ${from} – arrives ${arrival}`,
    newReport: (kind: string) => `New report: ${kind}`,
    /** M40: a copy of a report another player sent. */
    reportShared: (player: string, kind: string) => `${player} sent you a report: ${kind}`,
    buildComplete: (village: string, building: string, level: number) =>
      `${village}: ${building} reached level ${level}`,
    /** A demolition is done (1.6). */
    demolished: (village: string, building: string, level: number) =>
      `${village}: ${building} demolished to level ${level}`,
    /** Screen-reader name of the red counter in the top bar. */
    incomingBadge: (count: number) =>
      count === 1 ? '1 attack on the way to your villages' : `${count} attacks on the way to your villages`,
    notifications: 'Notifications',
    dismiss: 'Dismiss',
    worldOpened: 'The world has opened. Your village is ready!',
    worldEnded: (winner: string | null) =>
      winner === null ? 'The world has ended.' : `The world has ended: ${winner} won!`,
    /** v1.3: the victory clock started or passed on (`when` is "Sat 17 Oct, 01:49"), or stopped. */
    victoryClock: (side: string, when: string) =>
      `${side} meets the victory condition: they win on ${when} unless someone stops them`,
    victoryClockYours: (when: string) => `Your guild meets the victory condition: you win on ${when} if you hold on`,
    victoryClockYou: (when: string) => `You meet the victory condition: you win on ${when} if you hold on`,
    victoryClockStopped: (side: string) =>
      `${side} no longer meets the victory condition. The victory clock has stopped.`,
    victoryClockStoppedYours: 'Your guild no longer meets the victory condition. The victory clock has stopped.',
    victoryClockStoppedYou: 'You no longer meet the victory condition. The victory clock has stopped.',
    guildInvited: (guild: string) => `${guild} invited you to join their guild`,
    messageReceived: (player: string) => `New message from ${player}`,
    achievementEarned: (name: string, level: string | null) =>
      level === null ? `Achievement earned: ${name}` : `Achievement earned: ${name} (${level})`,
    /** M32b; `banner` is "War banner, tier II". */
    bannerFound: (banner: string, village: string) => `Banner found at ${village}: ${banner}`,
    bannerLost: (banner: string, village: string) => `Banner stolen from ${village}: ${banner}`,
    bannerStolen: (banner: string, village: string) => `Your thieves stole a banner at ${village}: ${banner}`,
    /** 1.6: attacks the player asked about are home; `loot` is "1,240" (resources), null for none. */
    troopsBack: (village: string, from: string, loot: string | null) =>
      loot === null
        ? `Your troops from ${from} are back in ${village}.`
        : `Your troops from ${from} are back in ${village} with ${loot} resources.`,
    nobodyBack: (from: string) => `Nobody came back from ${from}.`,
    /** A train (`from` is its target) or a farm list run (`from` null). */
    attacksBack: (count: number, village: string, from: string | null, loot: string | null, lost: number) => {
      const attacks = count === 1 ? '1 attack' : `${count} attacks`;
      if (lost === count) {
        return from === null
          ? `Farm run from ${village}: nobody came back from ${count === 1 ? 'the attack' : `the ${attacks}`}.`
          : `Nobody came back from the ${attacks} on ${from}.`;
      }
      const head =
        from === null ? `Farm run back in ${village}: ${attacks}` : `${attacks} on ${from} back in ${village}`;
      return `${head}${loot === null ? '' : `, ${loot} resources`}${lost === 0 ? '' : `, ${lost} lost`}.`;
    },
    /** 1.6: `resources` is "wood, clay". */
    storageFull: (village: string, resources: string) => `Warehouse full in ${village}: ${resources}.`,
  },
  /** The farm helper (M18): targets with the usual troops, one tap sends one attack. */
  farm: {
    title: 'Farm list',
    explainer: 'Your raid targets with the usual troops. One tap sends one attack; nothing is ever sent by itself.',
    listTitle: (village: string) => `Targets of ${village}`,
    empty: 'No targets yet. Add a village with the form, or use “Add to farm list” on the map.',
    count: (count: number, max: number) => `${count} of ${max} targets`,
    addTitle: 'Add a target',
    add: 'Add to list',
    countLabel: (unit: string) => `Units to send: ${unit}`,
    trip: (distance: string, duration: string) => `${distance} fields · ${duration} one way`,
    lastSent: (when: string) => `Last sent ${when}`,
    protectedUntil: (when: string) => `Under beginner protection until ${when}`,
    /** Your daily banner chance against this target is used (02 §21.7); `when` it comes back. */
    bannerRollUsed: (when: string) => `Banner from ${when}`,
    bannerRollHint:
      '“Banner from …”: a won battle against this target has had its chance at a banner today. Against a player that counts for all their villages. The chance comes back at the time shown; plundering works as always.',
    notEnough: 'Not enough troops at home for this.',
    sent: (arrival: string) => `Attack sent – arrives ${arrival}`,
    send: 'Send',
    sendTo: (target: string) => `Send the usual troops to ${target}`,
    /** The armed Send of a player's village while the sender is protected: the second tap sends. */
    sendAnyway: 'Send anyway?',
    /** The night bonus (1.4) on the map's farm line, for the templates that would arrive in it: "A, C", "2" */
    nightBonusWith: (letters: string, factor: string) =>
      `With ${letters} the attack arrives during the night bonus: the village defends ×${factor}.`,
    edit: 'Edit',
    editTitle: (target: string) => `Troops for ${target}`,
    save: 'Save',
    remove: 'Remove',
    /** 1.6, the farm list overhaul: the compact row's short facts (the screen reader hears the labels). */
    travelTime: 'Travel time',
    lastSentLabel: 'Last sent',
    lootLabel: 'Loot',
    bannerRollUsedShort: 'Banner chance used today',
    nightShort: 'Night bonus ',
    moraleShort: 'Morale',
    protectedShort: 'Under beginner protection',
    /** Before a raid landed: what the units can carry, at most. */
    haulUpTo: (amount: string) => `up to ${amount}`,
    raidLost: 'lost',
    /** The opened row (1.6). */
    onMap: 'On the map',
    capacityLine: (amount: string) => `The units carry up to ${amount} resources home.`,
    /** The last attack from this village that landed: `loot` undefined when it was lost. */
    lastRaidLine: (when: string, loot: string | undefined, full: boolean) =>
      loot === undefined
        ? `Last attack ${when}: lost.`
        : full
          ? `Last attack ${when}: ${loot} looted, all they could carry – the village had more.`
          : `Last attack ${when}: ${loot} looted.`,
    rowsHint: 'Tap a target for its troops, details and Edit.',
    /** What a run would do now, on the first tap of "Send all" or "Send banner chances" (1.6). */
    runPreview: (attacks: number, haul: string) =>
      attacks === 0
        ? 'No attack would go out now.'
        : `${attacks === 1 ? '1 attack' : `${attacks} attacks`}, loot up to ${haul}`,
    /** "Send all" (M39): the whole list from the top; a target the troops don't cover is skipped (1.3). */
    sendAll: 'Send all',
    sendAllArmed: 'Really send all?',
    sendAllHint: 'Top to bottom; a target without enough troops at home is skipped.',
    /** In place of "Send banner chances (0)" (1.3). */
    noBannerChances: 'No banner chance open',
    /** The (i) button that opens the banner chance rules under the hint. */
    bannerRulesLabel: 'How banner chances work',
    /** Like "send all", but only the targets with a banner chance (`bannerChance`, 1.2.3). */
    sendBannerChances: (count: number) => `Send banner chances (${count})`,
    sendBannerChancesArmed: 'Really send banner chances?',
    sendBannerChancesHint:
      '“Send banner chances” does the same, but only for the targets without “Banner from …” whose template can win a banner (not scouts alone, and at least 30 population against barbarian villages) and that have no attack of yours on the way yet. A player’s villages share one chance, so only the first of them the troops cover is sent.',
    sendAllLeavesPlayersOut: 'While your beginner protection lasts, “Send all” leaves players’ villages out.',
    /** 1.6: a switch under the buttons; one notification when the whole run is home. */
    notifyRun: 'Notify me when this run is back',
    sendAllSent: (count: number) =>
      count === 0 ? 'No attack sent' : count === 1 ? '1 attack sent' : `${count} attacks sent`,
    sendAllSkipped: (count: number) => `${count} skipped.`,
    sendAllShort: (count: number) => `${count} not sent: not enough troops at home.`,
    noteSent: 'Sent.',
    noteSkipped: {
      NOT_ENOUGH_UNITS: 'Not sent: not enough troops at home.',
      PROTECTED_TARGET: 'Skipped: under beginner protection when the troops arrive.',
      OWN_VILLAGE: 'Skipped: this is your village now.',
      ENDS_OWN_PROTECTION: 'Skipped: the attack would end your own beginner protection.',
    },
    /** Templates (1.4): A to E per player, shared by all villages; an entry uses one or its own units. */
    parts: 'Farm list parts',
    partTargets: 'Targets',
    partTemplates: 'Templates',
    templatesTitle: 'Templates',
    templatesHint: 'For all your villages. Change one and every target using it sends the new troops.',
    templateLabel: (letter: string) => `Template ${letter}`,
    templateNew: (letter: string) => `Create template ${letter}`,
    templateEditTitle: (letter: string) => `Template ${letter}`,
    templateDelete: 'Delete',
    templateUsed: (count: number) =>
      count === 0
        ? 'not used in this village'
        : count === 1
          ? 'used by 1 target here'
          : `used by ${count} targets here`,
    templateMore: (letter: string) => `More about template ${letter}`,
    troops: 'Troops',
    ownUnits: 'Own troops',
    ownUnitsHint: "Only for this target; template changes don't touch it.",
    pickTemplate: (letter: string) => `Use template ${letter}`,
    farmWith: 'Farm with',
    sendTemplate: (letter: string) => `Send template ${letter} now`,
    templatesShort: 'Greyed out: not enough troops at home.',
    noTemplates: 'No templates yet.',
    createTemplates: 'Create templates',
    onList: 'On the farm list',
    addSheetTitle: (target: string) => `Add ${target} to the farm list`,
    listOf: (village: string) => `Farm list of ${village}`,
    fromTemplate: 'Template',
  },
  /** Private messages and the block list (M17). */
  messages: {
    title: 'Messages',
    /** Desktop (1.3): next to the inbox until a conversation is opened, and the column's name for screen readers. */
    pickConversation: 'Pick a conversation on the left, or open a player’s profile to write to them.',
    openConversation: 'Open conversation',
    explainer: 'Write to another player. Only the two of you can read it.',
    /** Screen-reader text of the unread count on the navigation. */
    unreadBadge: (count: number) => (count === 1 ? '1 unread message' : `${count} unread messages`),
    inboxEmpty: 'No messages yet. Open a player’s profile to write to them.',
    you: 'You',
    unreadFrom: (count: number) => (count === 1 ? '1 new' : `${count} new`),
    back: 'Messages',
    withPlayer: (name: string) => `Messages with ${name}`,
    profile: 'Profile',
    list: 'Conversation',
    empty: 'No messages yet. Say hello!',
    loadOlder: 'Load older messages',
    label: 'Message',
    placeholder: 'Write a message…',
    send: 'Send',
    charsLeft: (count: number) => (count === 1 ? '1 character left' : `${count} characters left`),
    blockedTitle: 'Blocked players',
    blockedNone:
      'You have not blocked anyone. Blocked players can still write to you, but you will not see their messages.',
    unblock: 'Unblock',
    block: 'Block',
    blockedNote: 'You blocked this player. Their messages are hidden.',
    blockTitle: (name: string) => `Block ${name}?`,
    blockText:
      'You will not see their messages or be told about new ones, and they will not know. You can unblock them any time.',
    blockConfirm: 'Block',
    write: 'Write a message',
  },
  chat: {
    hide: 'Hide message',
    reallyHide: 'Really hide?',
    title: 'Chat',
    channels: 'Chat channel',
    world: 'World',
    guild: 'Guild',
    open: 'Open the chat',
    minimize: 'Minimise the chat',
    /** Screen-reader text of the chat's unread count. */
    unreadBadge: (count: number) => (count === 1 ? '1 unread chat message' : `${count} unread chat messages`),
    messages: 'Chat messages',
    empty: 'No messages yet. Say hello!',
    loadOlder: 'Load older messages',
    start: 'This is the start of the chat.',
    label: 'Message',
    placeholder: 'Write a message…',
    send: 'Send',
    charsLeft: (count: number) => (count === 1 ? '1 character left' : `${count} characters left`),
    offline: 'Not connected – reconnecting…',
    newMessages: 'New messages',
    /** The author of a broadcast by the host (M14d). */
    host: 'Host',
    broadcastToast: (text: string) => `Host: ${text}`,
  },
  /** Guilds (M15a): founding, invitations, members, the leader's tools. */
  guild: {
    title: 'Guild',
    explainer:
      'A guild is a group of players. Its tag shows in brackets before their names in the ranking and on the map. Found one, or wait for a leader to invite you.',
    disabled: 'The host has not switched guilds on in this world.',
    /** pre-game only, with guild mates starting together (1.6 UI review) */
    startTogether: 'Whoever is in a guild when the world opens starts next to it.',
    notInGuild: 'You are not in a guild.',
    back: 'Guilds',
    allGuilds: 'All guilds',
    noGuilds: 'No guild has been founded yet.',
    memberCount: (count: number) => (count === 1 ? '1 member' : `${count} members`),
    membersOf: (count: number, max: number) => `${count} of ${max} members`,
    points: (points: string) => `${points} points`,
    openOwn: 'Open your guild',
    create: {
      title: 'Found a guild',
      name: 'Name',
      nameHint: "3 to 30 characters: letters, digits, single spaces and . - _ '",
      tag: 'Tag',
      tagHint: '2 to 5 letters or digits (no accents), shown in brackets',
      submit: 'Found guild',
      problems: {
        tooShort: 'Too short.',
        tooLong: 'Too long.',
        badCharacters: 'Contains characters that are not allowed.',
      },
    },
    invitations: {
      title: 'Invitations',
      none: 'Nobody has invited you.',
      by: (name: string) => `invited by ${name}`,
      accept: 'Join',
      decline: 'Decline',
    },
    roles: { LEADER: 'Leader', MEMBER: 'Member' } as Record<'LEADER' | 'MEMBER', string>,
    description: 'About the guild',
    noDescription: 'No description yet.',
    members: 'Members',
    memberLine: (villages: number, points: string) =>
      `${villages === 1 ? '1 village' : `${villages} villages`} · ${points}`,
    leader: {
      editDescription: 'Edit description',
      descriptionLabel: 'Description',
      descriptionHint: (left: number) => `${left} characters left`,
      inviteTitle: 'Invite a player',
      inviteName: 'Player name',
      invite: 'Invite',
      pendingTitle: 'Open invitations',
      pendingNone: 'No open invitations.',
      pendingBy: (name: string) => `invited by ${name}`,
      withdraw: 'Withdraw',
      kick: 'Remove',
      kickTitle: 'Remove this member?',
      kickText: (name: string) => `${name} leaves the guild. They can be invited again.`,
      handOver: 'Make leader',
      /** 1.3: the "…" of a member's line, with "Make leader" and "Remove". */
      more: (name: string) => `Actions for ${name}`,
      handOverTitle: 'Hand over the lead?',
      handOverText: (name: string) => `${name} becomes the leader. You stay a member.`,
      disband: 'Disband the guild',
      disbandTitle: 'Disband the guild?',
      disbandText: (name: string) =>
        `${name} ends for everybody: all members are out and open invitations are gone. This cannot be undone.`,
    },
    leave: 'Leave the guild',
    leaveTitle: 'Leave the guild?',
    leaveText: (name: string) => `You leave ${name}. A leader can invite you again.`,
    leaveLastText: (name: string) => `You are the last member: ${name} ends when you leave.`,
    leaderMustHandOver: 'You lead this guild. Hand the lead to a member before you leave, or disband it.',
  },
  /** The host's page (04-api, "Admin"). */
  ranking: {
    title: 'Ranking',
    count: (total: number) => (total === 1 ? '1 player' : `${total} players`),
    rank: 'Rank',
    player: 'Player',
    points: 'Points',
    villages: 'Villages',
    villageCount: (count: number) => (count === 1 ? '1 village' : `${count} villages`),
    empty: 'Nobody is on the list yet.',
    pagesLabel: 'Ranking pages',
    previous: 'Previous',
    next: 'Next',
    page: (page: number, pages: number) => `Page ${page} of ${pages}`,
    /** The tabs (1.5, 02-game-rules §24): what the list is sorted by. */
    tabsLabel: 'Sort the ranking by',
    tabs: {
      POINTS: 'Points',
      ATTACKER: 'Attacker',
      DEFENDER: 'Defender',
      PLUNDERER: 'Plunderer',
    } satisfies Record<RankingKind, string>,
    tabHint: {
      POINTS: 'Everyone by the points of their villages.',
      ATTACKER: 'Defenders killed in their attacks, counted by population.',
      DEFENDER: 'Attackers killed where they defended, counted by population.',
      PLUNDERER: 'Resources taken in attacks.',
    } satisfies Record<RankingKind, string>,
    worldStatistics: 'World statistics',
  },
  /** Statistics (1.5, 02-game-rules §24): the profile's card and charts, and the world page. */
  statistics: {
    title: 'Statistics',
    history: 'Over time',
    since: (date: string) => `Counted since ${date}.`,
    populationNote: 'Kills and losses are counted by population: a unit counts as much as the farm space it needs.',
    attacks: 'Attacks',
    defences: 'Defences',
    wonLost: (won: string, lost: string) => `${won} won · ${lost} lost`,
    killedAttacking: 'Killed attacking',
    killedDefending: 'Killed defending',
    unitsLost: 'Own units lost',
    loot: 'Loot',
    takenLost: (taken: string, lost: string) => `${taken} taken · ${lost} lost`,
    villages: 'Villages',
    conqueredLost: (conquered: string, lost: string) => `${conquered} conquered · ${lost} lost`,
    unitsRecruited: 'Units recruited',
    buildingLevels: 'Building levels finished',
    chart: {
      POINTS: 'Points',
      VILLAGES: 'Villages',
    } satisfies Record<'POINTS' | 'VILLAGES', string>,
    chartLabel: 'Show over time',
    day: 'Day',
    oneDay: 'The first day: the line grows from tomorrow on.',
    worldTitle: 'World statistics',
    worldIntro: 'How the world has grown, day by day. Each day shows the state at its end; today shows now.',
    leaders: 'Leading players',
    leadersHint: 'Points of today’s top five over time.',
    world: 'The world',
    worldChart: {
      VILLAGES: 'Villages',
      BARBARIANS: 'Barbarian villages',
      PLAYERS: 'Players',
      POINTS: 'Points',
    } satisfies Record<'VILLAGES' | 'BARBARIANS' | 'PLAYERS' | 'POINTS', string>,
    worldFacts: (players: string, villages: string, barbarians: string) =>
      `${players} players · ${villages} villages · ${barbarians} barbarian villages`,
  },
  /** How the world is won (v1.3, 02-game-rules §22): the card above the ranking. */
  victory: {
    // v1.3: the card leads with who holds what; the player's own side is "your guild" or "you"
    titleRunning: 'Victory condition',
    titleEnded: 'Result',
    yourGuild: 'Your guild',
    keepsState: (who: string, keeps: number, count: number) => `${who} holds ${keeps} of ${count} keeps`,
    keepsStateYou: (keeps: number, count: number) => `You hold ${keeps} of ${count} keeps`,
    shareState: (who: string, share: string) => `${who} holds ${share} of the villages`,
    shareStateYou: (share: string) => `You hold ${share} of the villages`,
    noLeaderYet: 'Nobody leads yet',
    closestKeeps: (side: string, keeps: number, needed: number) =>
      `Closest: ${side} with ${keeps} of the ${needed} keeps needed.`,
    closestShare: (side: string, share: string, needed: number) => `Closest: ${side} with ${share} of ${needed} %.`,
    levelKeeps: (sides: number, keeps: number, needed: number) =>
      `Level at the top: ${sides} sides with ${keeps} of the ${needed} keeps needed each.`,
    levelShare: (sides: number, share: string, needed: number) =>
      `Level at the top: ${sides} sides with ${share} of ${needed} % each.`,
    ruleShortKeeps: (needed: number, count: number, hours: number) =>
      `Whoever holds ${needed} of the ${count} banner keeps for ${hours} hours in a row wins.`,
    ruleShortDomination: (percent: number, hours: number) =>
      `Whoever holds ${percent} % of all villages for ${hours} hours in a row wins.`,
    rules: 'Rules',
    pips: (keeps: number, count: number, needed: number) => `${keeps} of ${count} keeps, ${needed} needed`,
    guildTag: 'your guild',
    youWon: 'You won!',
    guildWon: 'Your guild won!',
    sideWon: (side: string) => `${side} won.`,
    yourPlaceGuild: (place: number, total: number) => `Your guild: place ${place} of ${total}`,
    yourPlace: (place: number, total: number) => `You: place ${place} of ${total}`,
    keepRowNone: 'held by nobody yet',
    toCondition: 'Victory condition',
    // how the winner won, under the headline that names it
    wonBy: {
      DOMINATION: 'Won by holding the map.',
      KEEPS: 'Won by holding the banner keeps.',
      END_DATE: 'Won with the most points at the end.',
    } satisfies Record<EndReason, string>,
    title: 'Victory',
    ruleDomination: (percent: number, hours: number) =>
      `A side wins once it holds ${percent}% of all villages on the map, barbarian villages included, for ${hours} hours in a row.`,
    ruleKeeps: (needed: number, count: number, hours: number) =>
      count === 1
        ? `One banner keep stands in the centre of the map. A side wins once it holds it for ${hours} hours in a row.`
        : `${count} banner keeps stand around the centre of the map. A side wins once it holds ${needed} of them for ${hours} hours in a row.`,
    ruleNone: 'The host ends this world.',
    sidesGuilds: 'A guild counts as one side with all its members; players without a guild count alone.',
    sidesPlayers: 'Every player counts alone.',
    endsAt: (when: string) => `The world ends ${when} at the latest. Then the side with the most points wins.`,
    leader: (side: string) => `${side} meets the condition.`,
    winsIn: 'Wins in',
    winsAt: (when: string) => `on ${when}, unless someone stops them`,
    winsAtYours: (when: string) => `on ${when}, if you hold on`,
    winsAtYou: (when: string) => `on ${when}, if you hold on`,
    standings: 'Leading sides',
    finalStandings: 'Final standings',
    share: (share: string, villages: number) => `${share} · ${villages === 1 ? '1 village' : `${villages} villages`}`,
    keeps: (keeps: number, count: number) => `${keeps} of ${count} keeps`,
    points: (points: string) => `${points} points`,
    leaderTag: 'leads',
    winnerTag: 'winner',
    ended: (when: string) => `The world ended ${when}.`,
    noWinner: 'Nobody won.',
    frozen: 'Nothing moves any more. Reports, messages and the chat stay open.',
  },
  profile: {
    back: 'Ranking',
    rank: (rank: number) => `Rank ${rank}`,
    points: (points: string) => `${points} points`,
    villages: 'Villages',
    noVillages: 'This player has no villages.',
    npcHint: 'An NPC lord plays by the same rules as everyone else.',
    guild: 'Guild',
    career: 'Career on every realm',
    onMap: 'On the map',
    invite: 'Invite to guild',
    invited: 'Invited to your guild. You can take the invitation back on the guild page.',
    inviteFull: 'Your guild is full, so you cannot invite anyone right now.',
    admin: {
      delete: 'Delete player',
      title: 'Delete this player?',
      text: (name: string) =>
        `${name}’s villages turn into barbarian villages, their troops and orders are gone, and the account can no longer log in. This cannot be undone.`,
      confirm: 'Delete player',
    },
  },
  /** Achievements (M16, 02-game-rules §19): honours without any effect on the game. */
  achievements: {
    title: 'Achievements',
    intro:
      'Honours for what you do in the world. They change nothing in the game. Everybody sees the ones you have earned on your profile; your progress is only yours.',
    earnedCount: (earned: number, total: number) => `${earned} of ${total} earned`,
    profileTitle: 'Achievements',
    profileEmpty: 'Nothing earned yet.',
    viewAll: 'All achievements and your progress',
    earnedOn: (when: string) => `Earned ${when}`,
    locked: 'Not earned yet',
    levelNames: ['Bronze', 'Silver', 'Gold'],
    /** Screen-reader name of a badge. */
    badgeLabel: (name: string, level: string | null) => (level === null ? name : `${name}, ${level}`),
    progress: (value: string, next: string) => `${value} of ${next}`,
    progressDone: (value: string) => `${value} – highest level reached`,
    nextLevel: (level: string) => `Next: ${level}`,
    categories: {
      BUILDING: 'Building',
      ARMY: 'Army',
      COMBAT: 'Combat',
      DEFENCE: 'Defence',
      CONQUEST: 'Conquest',
      TRADE: 'Trade',
      SOCIAL: 'Social',
      TIME: 'Time',
      BANNER: 'Banners',
    },
    items: {
      PIONEER: { name: 'Pioneer', description: 'You registered before the world opened.' },
      FOREMAN: { name: 'Foreman', description: 'Upgrade a Town Hall to level 10.' },
      SCHOLAR: { name: 'Scholar', description: 'Complete an Academy.' },
      SIEGECRAFT: { name: 'Siegecraft', description: 'Finish your first ram or catapult.' },
      NOBLE_BLOOD: { name: 'Noble Blood', description: 'Finish your first noble.' },
      FIRST_BLOOD: { name: 'First Blood', description: 'Win an attack with at least 10 units.' },
      SHIELD: { name: 'Shield', description: 'Beat an attack of at least 10 units on one of your villages.' },
      BREACH: { name: 'Breach', description: 'Lower an enemy wall with your rams.' },
      CONQUEROR: { name: 'Conqueror', description: 'Conquer your first village.' },
      TOPPLER: { name: 'Toppler', description: 'Conquer a village from another player.' },
      LORD_BREAKER: { name: 'Lord Breaker', description: 'Conquer a village from an NPC lord.' },
      FALLEN: { name: 'Fallen', description: 'Lose a village to a conquest.' },
      PHOENIX: { name: 'Phoenix', description: 'Start again after losing every village.' },
      FIRST_DEAL: {
        name: 'First Deal',
        description: 'Trade on the market: have your offer accepted, or accept an offer.',
      },
      FIRST_WORDS: { name: 'First Words', description: 'Write a message in the world chat.' },
      GUILD_FOUNDER: { name: 'Guild Founder', description: 'Found a guild.' },
      GUILD_MEMBER: { name: 'Guild Member', description: 'Join a guild.' },
      STANDARD_BEARER: { name: 'Standard Bearer', description: 'Find your first banner in battle.' },
      COLOURS_RAISED: { name: 'Colours Raised', description: 'Raise a banner where a Banner Hall lets it work.' },
      COLOURS_LOST: {
        name: 'Colours Lost',
        description: 'Lose a banner to banner thieves or with a conquered village.',
      },
      FULL_SET: { name: 'Full Set', description: 'Own each of the nine banners at some point.' },
      GROWTH: { name: 'Growth', description: 'Reach player points.' },
      LANDLORD: { name: 'Landlord', description: 'Own several villages at the same time.' },
      STANDING_ARMY: { name: 'Standing Army', description: 'Train units. Scouts and nobles do not count.' },
      VICTOR: { name: 'Victor', description: 'Win attacks. Against the same village, one win per day counts.' },
      BULWARK: {
        name: 'Bulwark',
        description: 'Beat attacks on your villages. From the same village, one per day counts.',
      },
      PLUNDERER: { name: 'Plunderer', description: 'Loot resources in the attacks that count as wins.' },
      MERCHANT_PRINCE: {
        name: 'Merchant Prince',
        description: 'Give resources away through accepted market offers, or pay them when you trade with the market.',
      },
      VETERAN: { name: 'Veteran', description: 'Days since you registered.' },
      BANNER_COLLECTOR: { name: 'Banner Collector', description: 'Own banners at the same time, stash and flying.' },
      /** The achievement, not the unit of the same name. */
      BANNER_THIEF: {
        name: 'Banner Thief',
        description:
          'Take banners from other villages with thieves or by conquest. Each banner counts once, your own never.',
      },
      PYRRHIC_VICTORY: {
        name: 'Pyrrhic Victory',
        description: 'Win an attack of at least 100 units and lose at least three quarters of them.',
      },
      AVENGER: {
        name: 'Avenger',
        description:
          'Win an attack with at least 10 units on a village of the player who beat you within the last day.',
      },
      DEMOLISHER: {
        name: 'Demolisher',
        description: 'Take at least 5 levels off a building with your catapults in one attack.',
      },
      LUCKY_STRIKE: { name: 'Lucky Strike', description: 'Win an attack that you would have lost without luck.' },
      SPY_CATCHER: { name: 'Spy Catcher', description: 'Every scout of an attack on your village dies.' },
      LAST_STAND: {
        name: 'Last Stand',
        description: 'Beat an attack of at least 10 units that brought more units than stood in your village.',
      },
      BROTHERS_IN_ARMS: {
        name: 'Brothers in Arms',
        description: "Your support stands in another player's village when it beats an attack of at least 10 units.",
      },
      SLAYER: { name: 'Slayer', description: 'Kill enemy units in your attacks.' },
      WARDEN: { name: 'Warden', description: 'Kill attacking units in your villages.' },
    },
  },
  /** /privacy: what the world stores, for guests and players alike. The host's name comes from GET /api/world/operator. */
  privacy: {
    link: 'Privacy',
    title: 'Privacy',
    intro:
      'What this world stores about you, and why. Bannerfall is open-source software: every world is run by its own host, who is responsible for the data on it.',
    operatorTitle: 'Who runs this world',
    operatorText: 'For anything about your data, write to the host of this world:',
    accountTitle: 'Your account',
    account:
      'Your player name and your password, which is stored only as a hash: it can’t be turned back into your password, not even by the host. Also when you registered and last logged in, your role, and a ban with its reason. No email address, no real name, no payment details.',
    accountServerTitle: 'Logging in through an account server',
    accountServer: (server: string) =>
      `This world also lets you log in with an account at ${server}, a separate service with its own privacy page. If you do, this world gets from it only your account’s id there and your name, and keeps no password of yours: your login runs through ${server}. If that account is banned or deleted there, ${server} tells this world, which ends your logins here.`,
    accountServerCareer: (server: string) =>
      `For your career page there, this world also tells ${server} every few minutes how you are doing here: your name, points, villages, rank, achievements, a few battle totals and, once the world has ended, its result for you. ${server} shows it to the players logged in there.`,
    gameTitle: 'Your game',
    game: 'Villages, troops, buildings, reports, farm list, notes on villages, achievements, statistics and guild: everything the game needs to run. Your name, villages, points, guild and statistics (battles, kills, loot, conquests, recruited units, building levels, and your points and villages per day) are visible to the other players of this world (map, ranking, profile). Your notes on villages are shown to nobody else, but like messages they are stored unencrypted on the server.',
    messagesTitle: 'Messages and chat',
    messages:
      'Private messages, the guild chat and the world chat are stored on the server, so they are still there next time. They are not encrypted there: the host can read them and can hide chat messages that break the rules.',
    addressTitle: 'Your IP address',
    address:
      'For a few minutes the server uses your address to limit logins, registrations and password changes (against password guessing and mass sign-ups). It stays in memory only and never goes into the database. Neither the game nor the web server it comes with keeps an access log or writes your address into a log (only wrong attempts at the host’s admin key are noted with their address).',
    logsTitle: 'Logs',
    logs: 'The server log notes events such as failed logins and locked accounts with the player name. The log files are deleted after 14 days; the server’s console output is limited in size and starts afresh with every update. Requests the game’s own page never sends (signs of cheating) are noted per account for 30 days. What the host does (bans, password resets, deleted players) stays in the host’s log.',
    cookiesTitle: 'Cookies and browser storage',
    cookies:
      'Two cookies, both needed to play: one keeps you logged in (up to 30 days), the other protects against forged requests. Your browser also remembers settings such as language, theme and map view. No tracking, no ads: fonts and pictures come from this server, and the only outside service is the push service of notifications you turn on yourself.',
    purposeTitle: 'Why and for how long',
    purpose:
      'The data is needed to play (your account and game) and to protect the world against abuse (address limits and logs); in the terms of the GDPR, Art. 6(1)(b) and (f). It is kept as long as the world runs; the backups of the database are kept for 14 days unless the host keeps them longer.',
    pushTitle: 'Notifications',
    push: 'If you turn on notifications for a device, this world stores the address your browser got for that device at its push service (run by the browser’s maker, such as Google, Mozilla, Apple or Microsoft), two keys to encrypt the notifications, and the game’s language on that device. Each notification goes to that push service encrypted, so the service can’t read it; it only learns that something was sent, and when. The address is deleted when you turn notifications off or log out on that device, when your password changes, and when the push service says the device is gone.',
    rightsTitle: 'Your rights',
    rights:
      'You may ask the host what is stored about you, and have it corrected or deleted. In the game, deleting means the host deletes your player: you can no longer log in and your villages become barbarian villages. Your account entry, your name and what you wrote stay, because old reports, messages and chat lines point to them; only your notes on villages are deleted. You may also complain to a data protection authority.',
    backToLogin: 'Log in',
    backToGame: 'Back to the game',
  },
  about: {
    link: 'About and credits',
    title: 'About Bannerfall',
    intro:
      'Bannerfall is a browser strategy game made for a small group of friends: build villages, train troops, attack and support each other, conquer villages, trade and chat. Computer-controlled NPC lords and barbarian villages fill the world when only a few people play.',
    /** 1.6: the thanks to the first test players, signed by the developer. */
    testersTitle: 'Thank you',
    testers:
      'A big thank you to the test players Anjum, David and Kai: you played from the very first world on, found the bugs and made Bannerfall better with your ideas.',
    testersSignature: 'Lots of love, Remph :)',
    originalTitle: 'Original work',
    original: 'Bannerfall is not affiliated with any existing game. Its name, numbers and texts are its own.',
    graphicsTitle: 'Graphics',
    /** 1.6: the medieval icons come from game-icons.net (CC BY 3.0 asks for the artists and a link to the licence). */
    graphics:
      'The medieval icons are from game-icons.net, drawn by Lorc, Delapouite, Skoll, HeavenlyDog, Cathelineau and DarkZaitzev, under the Creative Commons Attribution 3.0 licence. The classic icons, the village picture, the map and the banner symbol were drawn for Bannerfall.',
    graphicsSource: 'game-icons.net',
    graphicsLicence: 'Licence CC BY 3.0',
    fontsTitle: 'Fonts',
    fonts:
      'Uncial Antiqua, Alegreya SC and Alegreya Sans, all under the SIL Open Font License 1.1, served from this server.',
    softwareTitle: 'Software',
    software:
      'Built with React, TanStack Query and Vite in the browser, and Spring Boot, Hibernate, Flyway and PostgreSQL on the server, all open source.',
    back: 'Back to the game',
  },
  admin: {
    link: 'Admin',
    title: 'Admin',
    tabs: {
      label: 'Admin sections',
      status: 'Status',
      players: 'Players',
      world: 'World',
      events: 'Events',
      lords: 'NPC lords',
      suspicious: 'Suspicious',
      cheats: 'Cheats',
      audit: 'Audit log',
    },
    status: {
      title: 'Server',
      version: (version: string) => `Version ${version}`,
      uptime: (duration: string) => `running for ${duration}`,
      database: 'Database',
      databaseOk: 'ok',
      databaseDown: 'not answering',
      cheatsOn: 'CHEATS ON',
      cheatsOff: 'Cheats off',
      humans: 'Players',
      villages: 'Villages',
      barbarians: 'Barbarian villages',
      lords: 'NPC lords',
      events: 'Event queue',
      pending: (count: number) => `${count} pending`,
      nextDue: (when: string) => `next ${when}`,
      lag: (duration: string) => `behind by ${duration}`,
      noLag: 'keeping up',
      failed: (count: number) => (count === 1 ? '1 failed' : `${count} failed`),
      refresh: 'Refresh',
    },
    broadcast: {
      title: 'Message to everybody',
      hint: 'Appears in everybody’s chat and as a toast, e.g. “Restart in 5 minutes”.',
      label: 'Message',
      send: 'Send',
      sent: 'Sent.',
    },
    barbarian: {
      title: 'Spawn a barbarian village',
      hint: 'On exactly this field; it must be free and on the map.',
      x: 'x',
      y: 'y',
      spawn: 'Spawn',
      spawned: (x: number, y: number) => `A barbarian village stands on ${x}|${y} now.`,
    },
    /** v1.3 (02-game-rules §22): how the world is won. */
    victory: {
      hint: 'How this world is won. A side is a guild with all its members while guilds are on, otherwise a single player; NPC lords can win too. The server checks every 5 minutes, and the hold time starts again whenever another side leads or the leader drops below the goal. An end date (next card) works in every mode.',
      mode: 'How the world is won',
      modes: {
        DOMINATION: 'Domination',
        KEEPS: 'Banner keeps',
        NONE: 'Host ends it',
      } satisfies Record<VictoryMode, string>,
      modeHints: {
        DOMINATION:
          'A side has to hold a share of all villages on the map, barbarian villages included, for the hold time.',
        KEEPS:
          'Strong barbarian villages stand in a ring around the centre of the map (wall 10, 2,100 defenders). A side has to hold the share of them for the hold time. They appear within 5 minutes of saving; raising the number adds more, lowering it removes none.',
        NONE: 'Nobody wins by conquest: the world ends at the end date or when you end it.',
      } satisfies Record<VictoryMode, string>,
      dominationPercent: 'Share of all villages (%)',
      victoryHoldHours: 'Hold for (hours)',
      keepCount: 'Banner keeps on the map',
      keepPercent: 'Share of the keeps (%)',
      keepsNeeded: (needed: number, count: number) => `A side has to hold ${needed} of ${count} keeps.`,
    },
    /** v1.3: the end date, ending the world now, and taking an end back. */
    worldEnd: {
      title: 'End of the world',
      hint: 'An end date ends the world at that moment, whatever the mode: the side with the most points wins. An ended world is frozen: nothing moves any more, but everything stays readable and the chat stays open.',
      noDate: 'No end date is set.',
      status: (when: string) => `The world ends ${when}.`,
      at: 'End date',
      clear: 'Remove end date',
      endNow: 'End now',
      confirmNow:
        'End the world now? The side with the most points wins and nothing moves any more. You can reopen it afterwards.',
      confirmYes: 'Yes, end it',
      ended: (when: string) => `The world ended ${when}. It is frozen.`,
      reopen: 'Reopen the world',
      confirmReopen:
        'Reopen the world? The result is deleted and the world runs on: whatever fell due while it was frozen happens at once.',
      confirmReopenYes: 'Yes, reopen it',
    },
    barbarianWalls: {
      hint: 'Barbarian villages that build walls: they start without one, and from the start day on every growth round adds one level up to the cap. Rams can break a wall down; it grows back only with the last switch. A wall that is already above the cap (a deleted player’s village) is left alone. Off keeps the old behaviour: a random wall of 0–2 at the start, never more.',
      barbarianWallsRebuild: 'Barbarians rebuild walls',
    },
    players: {
      title: 'Players',
      hint: 'Human players; deleting a player is on his profile. A role change counts at once.',
      empty: 'No players yet.',
      adminTag: 'admin',
      deletedTag: 'deleted',
      points: (points: string) => `${points} points`,
      villages: (count: number) => (count === 1 ? '1 village' : `${count} villages`),
      lastLogin: (when: string) => `last login ${when}`,
      neverLoggedIn: 'no login recorded',
      makeAdmin: 'Make admin',
      makePlayer: 'Remove admin',
      resetPassword: 'Reset password',
      profile: 'Profile',
      roleTitle: 'Change the role?',
      roleText: (name: string, admin: boolean) =>
        admin
          ? `${name} becomes an admin: the admin page, world settings, deleting players – everything you can do here.`
          : `${name} is a normal player again and loses the admin page.`,
      roleConfirm: 'Change role',
      ban: 'Ban',
      unban: 'Unban',
      bannedUntil: (when: string, reason: string) => `Banned until ${when}: ${reason}`,
      bannedForGood: (reason: string) => `Banned for good: ${reason}`,
      banTitle: (name: string) => `Ban ${name}?`,
      banHint:
        'He cannot log in and his open sessions end. A timed ban leaves his villages alone; for good turns them barbarian.',
      banLength: 'How long',
      banLengths: { '1': '1 day', '7': '1 week', '30': '1 month', forever: 'For good' },
      banReason: 'Reason (he sees it at login)',
      banConfirm: 'Ban',
    },
    suspicious: {
      title: 'Suspicious requests',
      hint: 'Requests the game never sends: other players’ ids, invalid numbers, request floods. Last 30 days.',
      empty: 'Nobody has been suspicious.',
      banned: 'banned',
      total: (count: number) => (count === 1 ? '1 event' : `${count} events`),
      recent: 'Latest events',
      kinds: {
        FOREIGN_ID: 'foreign id',
        INVALID_INPUT: 'invalid input',
        MALFORMED_BODY: 'broken body',
        RATE_LIMITED: 'rate limit',
        CSRF_FAILED: 'CSRF',
        STOMP_REFUSED: 'refused frame',
      },
      checkTitle: 'Invariant check',
      checkHint: 'Looks for impossible states (negative amounts, orphaned rows). Read only; it runs every night too.',
      checkClean: 'Nothing wrong.',
      problemCount: (count: number) => (count === 1 ? '1 row' : `${count} rows`),
      runCheck: 'Check now',
    },
    reset: {
      title: (name: string) => `New password for ${name}`,
      hint: 'Leave the field empty and the server makes one up. Tell the player yourself – there is no e-mail.',
      label: 'New password (optional)',
      confirm: 'Set the password',
      generated: 'The new password (shown only now):',
      done: 'The password is changed.',
    },
    cheats: {
      title: 'Cheats',
      off: 'The cheats are off on this server (BANNERFALL_CHEATS_ENABLED).',
      hint: 'They work on any village, skip all rules and are written to the audit log.',
      player: 'Player',
      village: 'Village',
      choose: 'Choose…',
      villageId: 'Village id',
      resources: 'Resources (each)',
      give: 'Give',
      unit: 'Unit',
      count: 'Count',
      addUnits: 'Add units',
      finishOrders: 'Finish all orders',
      finishMovements: 'Let all movements arrive',
      done: 'Done.',
    },
    audit: {
      title: 'Audit log',
      hint: 'Every admin action, newest first. “host-cli” is the command line on the server.',
      empty: 'No admin actions yet.',
      target: (type: string, id: number | null) => (id === null ? type : `${type} ${id}`),
    },
    world: {
      fixed: (mapSize: number, spread: string, barbarians: number, lords: number) =>
        `Fixed once the world is created: map ${mapSize} × ${mapSize}, spread ${spread}, ${barbarians} barbarian villages at the start, ${lords} NPC lords at the start.`,
      speed: 'World speed',
      unitSpeed: 'Unit speed',
      startResources: 'Start resources (each)',
      luckRange: 'Luck range (0–1)',
      beginnerProtectionHours: 'Beginner protection (hours)',
      nobleMaxDistance: 'Noble range (fields)',
      loyaltyRegenPerHour: 'Loyalty regeneration per hour',
      buildQueueSize: 'Build queue at the normal price',
      buildQueueMax: 'Build queue with paid slots',
      recruitQueueSize: 'Recruit orders per building',
      cancelWindowSeconds: 'Cancel window (seconds)',
      barbariansPerNewPlayer: 'Barbarian villages per new player',
      tradeMaxRatio: 'Widest market ratio',
      npcThinkMinutes: 'NPC round interval (minutes, before speed)',
      archersHint:
        'They attack as their own class, met by every unit’s defence against archers. NPC lords use them too.',
      archersOff: 'Archers that exist stay and keep fighting; nobody can recruit or research new ones.',
      npcBannersHint:
        'Lords find, fly and fuse banners and standards like players, train their paladin in the Banner Hall and take banner thieves on their revenge attacks. Off: what lords hold sleeps but can still be stolen or conquered.',
      npcShipmentsHint:
        'A lord village whose warehouse is nearly full after building and recruiting sends what would overflow by merchant to the lord’s villages that are still being built. Never at night, never to a village under attack.',
      nightBonusHint:
        'Counts for attacks that arrive in these hours, in player and NPC lord villages, not in barbarian villages. Start and end must differ. Attacks already on their way get the bonus that holds when they arrive.',
      moraleHint:
        'The attack counts with 30% + 3 × the defender’s points / the attacker’s points, at most 100% and at least the lowest morale. Only the attack changes, never the defence. Players and NPC lords alike; barbarian villages and scouts are not affected.',
      villageNameLanguageHint:
        "New villages are called “Ben's village” or “Dorf von Ben”. Saving another language renames every village that still has its default name; names players gave stay.",
      guildStartTogetherHint:
        'At the opening, players who are in a guild by then get villages a few fields apart; a player who lost his last village may start again next to his guild.',
      npcGuildsHint:
        'Lords from 850 points team up with a lord within 15 fields (more on a wider map): they never attack each other, help defend each other and avenge each other. You can rearrange them in the NPC lords tab.',
      npcGuildsOff: 'Saving this ends every lord guild and clears your placements.',
      npcLearningHint:
        'Lords shift up to 35 % of their troops towards what counters the attacks on them and towards the mix of human attacks that won against villages of players or lords, and build their wall up after rams and catapults. Their economy stays the same.',
      mapSize: 'Map size (fields per side)',
      /** 1.6: how far apart the sides start. */
      mapSpread: 'Map spread',
      mapSpreadHint:
        'Players, NPC lords and barbarians spread out this much; the lords look for targets as far. Picking one also sets the map size and the noble range, which you can still change.',
      barbarianInitialCount: 'Barbarian villages at the start',
      npcLordCount: 'NPC lords at the start',
      pregameHint:
        'The world has not opened: map spread, map size, barbarians and NPC lords can still be changed here. They are created when the world opens.',
      setupLink: 'Walk through the important settings in the guided setup',
    },
    /** The World tab in the setup wizard's five sections with one save bar (1.6). */
    worldTab: {
      sectionsLabel: 'World settings',
      sections: {
        basics: 'Basics',
        pace: 'Pace',
        map: 'Map and opponents',
        rules: 'Rules',
        victory: 'Victory and end',
      } satisfies Record<WorldSection, string>,
      /** On phones, where the five share one row. */
      short: {
        basics: 'Basics',
        pace: 'Pace',
        map: 'Map',
        rules: 'Rules',
        victory: 'Victory',
      } satisfies Record<WorldSection, string>,
      intros: {
        basics: 'What the world is called, how players get in and what villages are named.',
        pace: 'How fast the world runs. Running orders keep their times; a new speed counts for production from each village’s last update.',
        map: 'The map, the barbarians and the NPC lords.',
        rules: 'Which parts of the game are switched on. Changes apply once you save.',
        victory: 'How the world is won and when it ends.',
      } satisfies Record<WorldSection, string>,
      groups: {
        world: 'World',
        pace: 'Pace',
        map: 'Map',
        barbarians: 'Barbarians',
        lords: 'NPC lords',
        guilds: 'Guilds',
        combat: 'Combat',
        bannersNobles: 'Banners and nobles',
        economy: 'Economy',
        victory: 'Victory condition',
        end: 'End of the world',
      },
      /** The names the save bar uses for its changes; switch rows show them as their label. */
      rows: {
        name: 'World name',
        inviteCode: 'Invite code',
        villageNameLanguage: 'Village names',
        speed: 'World speed',
        unitSpeed: 'Unit speed',
        beginnerProtectionHours: 'Beginner protection',
        startResources: 'Start resources',
        buildQueue: 'Build queue',
        recruitQueueSize: 'Recruit orders',
        cancelWindowSeconds: 'Cancel window',
        mapSpread: 'Map spread',
        mapSize: 'Map size',
        nobleMaxDistance: 'Noble range',
        barbarianInitialCount: 'Barbarians at the start',
        barbariansPerNewPlayer: 'Barbarians per new player',
        barbarianGrowth: 'Barbarian villages grow',
        barbarianWalls: 'Barbarian walls',
        npcLordCount: 'Lords at the start',
        npcGuilds: 'Lords form guilds',
        npcLearning: 'Lords learn from players',
        npcBanners: 'Lords use banners',
        npcShipments: 'Lords ship their surplus',
        npcThinkMinutes: 'NPC round',
        guilds: 'Guilds',
        guildStartTogether: 'Guild mates start next to each other',
        morale: 'Morale',
        nightBonus: 'Night bonus',
        unitLevels: 'Unit levels',
        archers: 'Archers',
        paladin: 'Paladin',
        luckRange: 'Luck range',
        banners: 'Banners',
        standardTheft: 'Standard theft',
        noblePrice: 'Rising noble price',
        loyaltyRegenPerHour: 'Loyalty',
        market: 'Market exchange',
        tradeMaxRatio: 'Largest trade ratio',
        automation: 'Village automation',
        victory: 'Victory condition',
        endsAt: 'End date',
      } satisfies Record<WorldRow, string>,
      npcLearningHint: 'They shift their troops towards what works against the attacks on them and what wins.',
      npcShipmentsHint: 'What would overflow goes by merchant to their villages that are still growing.',
      automationHint: 'Villages from this number on can follow a building and recruiting plan on their own.',
      paladinHint: 'One hero per player, one more slot for every few villages.',
      /** Shorter labels for the numbers under a switch. */
      fields: {
        guildMaxMembers: 'Most members per guild',
        npcGuildMaxMembers: 'Most lords per lord guild',
        moraleFloor: 'Lowest morale (%)',
        nightBonusStartHour: 'Night from (hour, German time)',
        nightBonusEndHour: 'Night until (hour)',
        nightBonusFactor: 'Defence ×',
        paladinMaxSlots: 'Most slots',
        paladinVillagesPerSlot: 'Villages per further slot',
        bannerDropFactor: 'Banner drops ×',
        noblePriceStep: 'Added per rank',
        marketExchangeRatio: 'Rate (1 for x)',
        automationVillages: 'From village no.',
        barbarianWallStartDays: 'Start after (days since the opening)',
        barbarianWallMaxLevel: 'Highest wall level',
      },
      fieldHints: {
        moraleFloor: '30 to 100',
        nightBonusFactor: '1.1 to 3',
        bannerDropFactor: '1 = normal, 0 = none',
        noblePriceStep: 'at most 1',
        marketExchangeRatio: '1 to 10',
      },
      more: 'More settings',
      lordsLink: 'Difficulty and the single lords: NPC lords tab',
      endsAtHint: 'Leave it empty and the world keeps running.',
      endNowHint: '“End now” asks first and works at once, not through the save bar.',
      changes: (count: number) => (count === 1 ? '1 change' : `${count} changes`),
      where: (section: string, rows: string) => `${section}: ${rows}`,
      unsaved: '(unsaved changes)',
      discard: 'Discard',
      invalid: (rows: string) => `Check: ${rows}`,
      saved: 'Saved.',
      failed: (error: string) => `Not saved: ${error}`,
      endFailed: (error: string) => `Saved, except the end date: ${error}`,
      settingsFailed: (error: string) => `Only the end date was saved, the rest not: ${error}`,
    },
    opening: {
      title: 'Opening time',
      hint: 'Until then players can register, chat and read the settings; nobody has a village. When the time comes the map is created and every registered player gets a village.',
      status: (when: string) => `The world opens ${when}.`,
      noTime: 'No opening time is set: the world waits.',
      open: 'The world is open.',
      registered: (count: number) => (count === 1 ? '1 player registered' : `${count} players registered`),
      at: 'Opens at (your time zone)',
      set: 'Set the time',
      clear: 'Take the time back',
      openNow: 'Open the world now',
      confirmNow: 'Open the world now? Every registered player gets a village at once.',
      confirmYes: 'Yes, open it',
      cancel: 'Cancel',
      saved: 'Saved.',
      pastHint: 'A time in the past opens the world right away.',
    },
    events: {
      title: 'Failed events',
      empty: 'No event has failed.',
      hint: 'An event that failed 3 times is given up. Retrying gives it 3 new attempts.',
      retry: 'Retry',
      failedAt: (when: string) => `failed ${when}`,
      attempts: (count: number) => `${count} attempts`,
      about: (id: number | null) => (id === null ? 'no subject' : `subject ${id}`),
    },
    difficulty: {
      title: 'NPC lords: difficulty',
      names: { EASY: 'Easy', NORMAL: 'Normal', HARD: 'Hard', CUSTOM: 'Custom' } as Record<
        'EASY' | 'NORMAL' | 'HARD' | 'CUSTOM',
        string
      >,
      hint: 'Takes effect at each lord’s next round.',
      custom: {
        title: 'Custom difficulty',
        hint: 'Starts as a copy of Normal. Times are Berlin time.',
        productionFactor: 'Production factor',
        boostBelow: 'Boost below (× the humans’ average points, 0 = no boost)',
        boostFactor: 'Boost factor',
        leadMargin: 'Lead margin (× the strongest human’s points)',
        attacksPerPlayerPerDay: 'Attacks per player and day, one lord',
        attacksPerHumanPerDay: 'Attacks per human and day, all lords',
        buildQuietFrom: 'No new build orders from',
        buildQuietTo: 'No new build orders until',
        farmLootShare: 'Farm loot per day (× own daily production, 0 = no limit)',
        quietHours: 'No arrivals at humans 23:00–07:00',
        spareLastVillage: 'Spare a human’s last village',
        farmRest: 'No farming at night (in the time without new build orders)',
        saved: 'Saved.',
      },
    },
    lords: {
      title: 'NPC lords',
      spawn: 'Spawn a lord:',
      empty: 'There are no NPC lords.',
      pregame: 'The lords are created when the world opens; their number is set in the world settings.',
      personalities: { BALANCED: 'Balanced', OFFENSIVE: 'Offensive', DEFENSIVE: 'Defensive' } as Record<
        'BALANCED' | 'OFFENSIVE' | 'DEFENSIVE',
        string
      >,
      points: (points: string) => `${points} points`,
      villages: (count: number) => (count === 1 ? '1 village' : `${count} villages`),
      factor: (factor: string) => `production × ${factor}`,
      braked: 'braked',
      boosted: 'boosted',
      outOfTheGame: 'Out of the game',
      nextRound: (when: string) => `next round ${when}`,
      lastRound: (when: string) => `last ${when}`,
      /** Lord guilds (09-npc-lords §13.7). */
      guilds: 'Lord guilds',
      guildsEmpty: 'No lord guilds yet. Lords form them from 850 points with a neighbour within 15 fields.',
      guildsOff: 'Lord guilds are off (world settings).',
      guildLine: (members: number, max: number, points: string) => `${members}/${max} lords · ${points} points`,
      guild: (name: string, tag: string) => `Guild ${name} [${tag}]`,
      noGuild: 'No guild',
      placed: 'placed by the admin',
      more: (name: string) => `Guild for ${name}`,
      moveTo: (name: string, tag: string) => `Move to ${name} [${tag}]`,
      takeOut: 'Take out of the guild',
      found: 'Found a new lord guild',
      letChoose: 'Let it choose',
      /** Lord banners (09-npc-lords §14.8). */
      paladinLevel: (level: number) => `Paladin level ${level}`,
      stash: (items: string) => `stash: ${items}`,
      stashEmpty: 'stash empty',
      times: (name: string, count: number) => (count === 1 ? name : `${name} ×${count}`),
      asleep: 'asleep',
      worksAt: (tier: string) => `works at ${tier}`,
      bannersOff: 'Lord banners are off (world settings): what lords hold sleeps.',
      /** Learning lords (09-npc-lords §15.5). */
      /** 1.6 UI review: the troop shifts first ("Learned: Light cavalry +4 %, Archer −4 %") */
      learned: (changes: string) => `Learned: ${changes}`,
      learnedNoChange: 'Learned: no noticeable change to the troops yet',
      learnedInfo: 'What the lord has learned',
      learnedAttacks: (percent: number) =>
        `From attacks on it: ${percent}\u00a0%. This much of its troops it turns towards what attacks it (spearmen against cavalry, swordsmen against infantry, scouts against scouts).`,
      learnedWins: (percent: number) =>
        `From players' wins: ${percent}\u00a0%. This much it mixes its attack troops like the armies players have won with lately.`,
      learnedRest: 'The rest follows its personality’s template. Without new attacks and wins, what it learned fades.',
      wallGoal: (village: string, level: number, until: string) => `${village}: wall to level ${level} until ${until}`,
    },
  },
  dev: {
    title: 'Dev cheats',
    hint: 'Only in development (pnpm dev, server profile dev).',
    addResources: (amount: string) => `+${amount} of each resource`,
    addUnits: (amount: string) => `+${amount} of each unit`,
    finishOrders: 'Finish all orders now',
    finishMovements: 'Let all movements and merchants arrive now',
    /** On the map, for an empty field that was jumped to. */
    spawnBarbarian: (coords: string) => `Barbarian village on ${coords}`,
  },
  /** Keyed by the `code` of API errors (server: ErrorCode.java) plus the client-side codes. */
  errors: {
    VALIDATION: 'Please check your input.',
    UNAUTHENTICATED: 'Please log in.',
    FORBIDDEN: 'You are not allowed to do that.',
    CSRF_TOKEN_INVALID: 'Your session has expired. Please reload the page.',
    INTERNAL_ERROR: 'Something went wrong on the server.',
    NOT_FOUND: 'This does not exist (anymore).',
    BAD_REQUEST: 'The server could not handle this request. Please reload the page.',
    INVALID_INVITE_CODE: 'The invite code is wrong.',
    USERNAME_TAKEN: 'This username is already taken.',
    BAD_CREDENTIALS: 'Wrong username or password.',
    TOO_MANY_LOGIN_ATTEMPTS: 'Too many failed attempts. Please wait a minute.',
    ACCOUNT_NOT_FOUND: 'This account no longer exists.',
    WORLD_FULL: 'The world is full. There is no free spot for a new village.',
    WORLD_NOT_OPEN: 'The world has not opened yet.',
    WORLD_ALREADY_OPEN: 'The world is open already; this can only be changed before it opens.',
    SETUP_CODE_INVALID:
      'This setup link is wrong, used already or older than 30 minutes. Run `bannerfall-admin setup` again for a new one.',
    WORLD_ENDED: 'The world has ended. Nothing can change any more.',
    WORLD_NOT_ENDED: 'The world has not ended.',
    SERVER_STARTING: 'The server is starting. Try again in a moment.',
    GUILDS_DISABLED: 'The host has not switched guilds on.',
    GUILD_NOT_FOUND: 'This guild does not exist (anymore).',
    ALREADY_IN_GUILD: 'Already in a guild: you, or the player you invited.',
    NOT_IN_GUILD: 'You are not in a guild.',
    NOT_GUILD_LEADER: 'Only the leader of the guild may do that.',
    GUILD_NAME_TAKEN: 'Another guild has this name already.',
    GUILD_TAG_TAKEN: 'Another guild has this tag already.',
    GUILD_FULL: 'The guild is full.',
    LEADER_MUST_TRANSFER: 'Hand the lead to a member before you leave.',
    INVITATION_NOT_FOUND: 'This invitation no longer exists. It may have been withdrawn.',
    ALREADY_INVITED: 'This player has been invited already.',
    GUILD_MEMBER_NOT_FOUND: 'This player is not a member of your guild.',
    INVITATION_RATE_LIMIT: 'This player was invited a moment ago. Try again in a minute.',
    FARM_TARGET_NOT_FOUND: 'This target is not on the list any more.',
    ALREADY_IN_FARM_LIST: 'This village is on the list already.',
    FARM_LIST_FULL: 'The farm list is full. Remove a target first.',
    FARM_TEMPLATE_NOT_FOUND: "That template doesn't exist (any more).",
    OWN_PLAYER: 'You cannot do that to yourself.',
    MESSAGE_RATE_LIMIT: 'You are writing too fast. Wait a second.',
    INBOX_FULL: 'This player has too many unread messages from you. Wait for an answer.',
    PLAYER_BLOCKED: 'You blocked this player. Unblock them to write to them.',
    BLOCK_LIST_FULL: 'Your block list is full.',
    VILLAGE_NOT_FOUND: 'This village does not exist or is not yours.',
    NOT_ENOUGH_RESOURCES: 'Not enough resources.',
    NOT_ENOUGH_POPULATION: 'Not enough free population. Upgrade the farm first.',
    REQUIREMENTS_NOT_MET: 'The requirements are not met yet.',
    MAX_LEVEL_REACHED: 'This building is already at its maximum level.',
    MIN_LEVEL_REACHED: 'This building can’t be demolished any further.',
    BUILD_ORDER_CONFLICT: 'A building can’t be upgraded and demolished in the same build queue.',
    UNIT_LEVELS_DISABLED: 'Unit levels are switched off in this world.',
    PALADIN_SLOTS_FULL: 'All your paladin slots are taken. More villages open more slots.',
    BANNERS_DISABLED: 'Banners are switched off in this world.',
    BANNER_NOT_FOUND: 'This banner does not exist or is not yours.',
    BANNER_NOT_IN_STASH: 'This banner already flies in a village. Take it down first.',
    BANNER_HALL_REQUIRED: 'This village needs a Banner Hall to fly a banner.',
    BANNER_LOCKED:
      'An attack is on its way to this village, or for a standard one with a paladin from it is under way: this cannot be changed until it is over.',
    NO_BANNER_EQUIPPED: 'No banner flies in this village.',
    BANNER_TIERS_DIFFER: 'Only banners of the same tier can be fused.',
    BANNER_TIER_MAX: 'Tier III is the highest: these banners cannot be fused.',
    BANNER_FUSE_TYPE: 'The new banner takes the type of one of the three.',
    BANNER_FAMILIES_DIFFER: 'Banners fuse only with banners, paladin standards only with standards.',
    PALADIN_DISABLED: 'There are no paladins in this world.',
    ARCHERS_DISABLED: 'There are no archers in this world.',
    RESEARCH_BUSY: 'This smithy, or this unit, is already being researched. Wait until it is done.',
    RESEARCH_ORDER_NOT_FOUND: 'This research no longer exists. It may be done already.',
    QUEUE_FULL: 'The queue is full.',
    BUILD_ORDER_NOT_FOUND: 'This build order no longer exists. It may have finished already.',
    ORDER_NOT_MOVABLE: 'Orders that have started cannot be moved, and nothing can go in front of them.',
    RECRUIT_ORDER_NOT_FOUND: 'This recruitment order no longer exists. Its units may be done already.',
    RECRUIT_TEMPLATE_NOT_FOUND: "This template doesn't exist (any more).",
    BUILD_PLAN_NOT_FOUND: "This build plan doesn't exist (any more).",
    AUTOMATION_LOCKED: 'The automation is not unlocked for you yet.',
    DISMISS_ORDER_NOT_FOUND: 'This dismiss order no longer exists. Its units may be gone already.',
    DISMISS_LOCKED: 'An attack is on its way, so dismissing cannot be stopped now. Try again once it has arrived.',
    TARGET_NOT_FOUND: 'There is no village on this field.',
    MOVEMENT_NOT_FOUND: 'These troops are no longer on the way. They may have arrived already.',
    STACK_NOT_FOUND: 'These troops are no longer there.',
    NOT_ENOUGH_UNITS: 'Not enough units in the village.',
    EMPTY_COMMAND: 'Choose at least one unit.',
    OWN_VILLAGE: 'You cannot attack your own village.',
    SAME_VILLAGE: 'The target is the village the troops would leave from.',
    PROTECTED_TARGET: 'This player is still under beginner protection when the troops arrive.',
    NOBLE_TOO_FAR: 'Nobles cannot travel that far.',
    NOBLE_ONLY_IN_ATTACK: 'Nobles can only go along on attacks.',
    CANCEL_WINDOW_OVER: 'Too late – this command can no longer be cancelled.',
    REPORT_NOT_FOUND: 'This report does not exist (anymore). It may have been deleted.',
    REPORT_UNREADABLE: 'This report can’t be shown any more, so it can’t be sent either.',
    CHAT_RATE_LIMIT: 'You are writing too fast. Wait a second.',
    FIELD_NOT_FREE: 'There is already a village on that field, or it is off the map.',
    RESPAWN_NOT_ALLOWED: 'You still have a village, so you cannot start again.',
    NOT_ENOUGH_MERCHANTS: 'Not enough free merchants.',
    INVALID_RATIO: 'The exchange rate is too lopsided for this world.',
    SAME_RESOURCE: 'An offer must ask for a different resource than it gives.',
    EXCHANGE_OFF: 'Trading with the market is off in this world.',
    EXCHANGE_LIMIT: 'The market does not give that much of this resource today.',
    WAREHOUSE_FULL: 'The warehouse has no room for that much.',
    EMPTY_TRANSPORT: 'Choose at least one resource to send.',
    OWN_OFFER: 'You cannot accept your own offers.',
    OFFER_EXHAUSTED: 'The offer does not have that many left.',
    OFFER_NOT_FOUND: 'This offer no longer exists. It may have been taken or cancelled.',
    PLAYER_NOT_FOUND: 'This player does not exist (anymore).',
    ACHIEVEMENT_NOT_FOUND: 'This achievement does not exist.',
    CANNOT_DELETE_SELF: 'You cannot delete your own player.',
    MESSAGE_NOT_FOUND: 'This chat message does not exist.',
    EVENT_NOT_FOUND: 'This event does not exist (anymore).',
    EVENT_NOT_FAILED: 'This event has not failed (anymore).',
    LAST_ADMIN: 'The last admin cannot stop being one. Make someone else an admin first.',
    TOO_MANY_ADMIN_ATTEMPTS: 'Too many wrong host tokens. Wait a minute.',
    WRONG_PASSWORD: 'The current password is wrong.',
    LOCAL_LOGIN_OFF: 'On this world you log in through the account server.',
    ACCOUNT_LINKED: 'This account logs in through the account server. Change the password there.',
    EXTERNAL_LOGIN_MISSING: 'Your login has expired. Please log in again.',
    EXTERNAL_NAME_INVALID:
      'Your name at the account server does not fit the name rules of this world (3–24 letters, digits, _ and -).',
    LINK_NOT_ALLOWED: 'Linking is not possible right now.',
    PUSH_UNAVAILABLE: 'This world doesn’t send notifications.',
    ACCOUNT_BANNED: 'This account is banned.',
    TOO_MANY_REQUESTS: 'Too many requests. Please slow down.',
    CANNOT_BAN_ADMIN: 'Admins cannot be banned. Remove the admin role first.',
    BODY_TOO_LARGE: 'The request is too large.',
    NETWORK: 'The server is not reachable. Check your connection.',
    UNKNOWN: 'Something went wrong.',
  },
};

/** The shape every language must have. `typeof en` turns the object above into a type. */
export type Dictionary = typeof en;

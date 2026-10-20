import type { ReactNode, SVGProps } from 'react';

/**
 * Hand-made icons (05-frontend §8). Line icons use `currentColor`, so they take the text colour of
 * their parent; the three resource icons have fixed colours. Each entry: viewBox size + drawing.
 */
const stroke = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
} as const;

const icons = {
  // resources (16 × 16)
  wood: [
    16,
    <>
      <rect x="2" y="5" width="12" height="6" rx="3" fill="#9a6a3a" />
      <circle cx="13" cy="8" r="2.4" fill="#d9b07a" />
    </>,
  ],
  clay: [
    16,
    <>
      <rect x="1.5" y="4" width="6" height="3.5" fill="#c0603a" />
      <rect x="8.5" y="4" width="6" height="3.5" fill="#c0603a" />
      <rect x="5" y="8.5" width="6" height="3.5" fill="#a84e2c" />
    </>,
  ],
  iron: [
    16,
    <>
      <path d="M3 11 L5 5 H11 L13 11 Z" fill="#8a96a3" />
      <path d="M5 5 H11 L10 7 H6 Z" fill="#c7d0d9" />
    </>,
  ],
  population: [
    16,
    <>
      <circle cx="8" cy="5" r="2.6" fill="currentColor" />
      <path d="M3 14 C3 9.5 13 9.5 13 14 Z" fill="currentColor" />
    </>,
  ],
  // buildings (16 × 16)
  HEADQUARTERS: [
    16,
    <>
      <path d="M2 7 L8 2 L14 7 V14 H2 Z" {...stroke} />
      <rect x="6.5" y="9" width="3" height="5" fill="currentColor" />
    </>,
  ],
  BARRACKS: [16, <path d="M8 2 L13 4 V8 C13 11 10.5 13 8 14 C5.5 13 3 11 3 8 V4 Z" {...stroke} />],
  STABLE: [16, <path d="M4.5 13 V8 A3.5 3.5 0 0 1 11.5 8 V13" {...stroke} strokeWidth={2.2} />],
  WORKSHOP: [
    16,
    <>
      <path d="M3 13 L9 7" {...stroke} strokeWidth={1.8} />
      <path d="M7.5 3.5 L12.5 8.5 L14 7 L9 2 Z" fill="currentColor" />
    </>,
  ],
  ACADEMY: [
    16,
    <path
      d="M2.5 3.5 C4.5 2.5 6.5 2.5 8 4 C9.5 2.5 11.5 2.5 13.5 3.5 V13 C11.5 12 9.5 12 8 13.5 C6.5 12 4.5 12 2.5 13 Z M8 4 V13.5"
      {...stroke}
      strokeWidth={1.4}
    />,
  ],
  SMITHY: [
    16,
    <path d="M2 5 H11 C11 7 12.5 8 14 8 V9 H10 L11 11 H12 V13 H4 V11 H5 L6 9 C4 9 2 7.5 2 5 Z" fill="currentColor" />,
  ],
  RALLY_POINT: [16, <path d="M4 14 V2 M4 3 H12 L10 5.5 L12 8 H4" {...stroke} />],
  // a pair of scales (1.4; a coin with a line read as the info sign)
  MARKET: [
    16,
    <>
      <path d="M8 2.5 V13.5 M5 13.5 H11 M3 4.5 H13" {...stroke} strokeWidth={1.4} />
      <path d="M3 4.5 L1.3 9 H4.7 Z M13 4.5 L11.3 9 H14.7 Z" {...stroke} strokeWidth={1.2} />
    </>,
  ],
  WOODCUTTER: [
    16,
    <>
      <path d="M3 14 L11 4" {...stroke} />
      <path d="M9 2 C13 2 14 5 14 7 L10 5 Z" fill="currentColor" />
    </>,
  ],
  CLAY_PIT: [16, <path d="M2 4 H14 V12 H2 Z M2 8 H14 M6 4 V8 M10 8 V12" {...stroke} strokeWidth={1.4} />],
  IRON_MINE: [
    16,
    <>
      <path d="M8 5 V14" {...stroke} />
      <path d="M2 6 C5 2 11 2 14 6" {...stroke} />
    </>,
  ],
  FARM: [
    16,
    <path
      d="M8 14 V5 M8 8 C5 8 4 6 4 4 C6 4 8 5 8 8 M8 10 C11 10 12 8 12 6 C10 6 8 7 8 10"
      {...stroke}
      strokeWidth={1.5}
    />,
  ],
  WAREHOUSE: [16, <path d="M2.5 4 H13.5 V13 H2.5 Z M2.5 7 H13.5" {...stroke} />],
  HIDING_PLACE: [
    16,
    <>
      <circle cx="5" cy="8" r="2.5" {...stroke} />
      <path d="M7.5 8 H14 M12 8 V10.5 M10 8 V10" {...stroke} />
    </>,
  ],
  WALL: [16, <path d="M2 14 V5 H4.5 V7 H7 V5 H9 V7 H11.5 V5 H14 V14 Z" {...stroke} strokeWidth={1.5} />],
  // a banner on a crossbar
  BANNER_HALL: [
    16,
    <>
      <path d="M2.5 3 H13.5" {...stroke} strokeWidth="1.5" />
      <path d="M4.5 3.5 V13 L8 11 L11.5 13 V3.5" {...stroke} strokeWidth="1.4" />
    </>,
  ],
  // units (16 × 16)
  SPEARMAN: [
    16,
    <>
      <path d="M8 6.5 V14.5" {...stroke} />
      <path d="M8 1.2 C9.8 3.2 9.8 5.2 8 7 C6.2 5.2 6.2 3.2 8 1.2 Z" fill="currentColor" />
    </>,
  ],
  SWORDSMAN: [
    16,
    <>
      <path d="M8 1.8 V10.5 M5 10.5 H11 M8 10.5 V13.2" {...stroke} />
      <circle cx="8" cy="14.2" r="1" fill="currentColor" />
    </>,
  ],
  AXEMAN: [
    16,
    <>
      <path d="M8 2.5 V14" {...stroke} />
      <path
        d="M8 4 C5.5 3 3 4.5 3 7.5 C5 6.8 6.5 7 8 8 Z M8 4 C10.5 3 13 4.5 13 7.5 C11 6.8 9.5 7 8 8 Z"
        fill="currentColor"
      />
    </>,
  ],
  SCOUT: [
    16,
    <>
      <path d="M1.5 8 C4 3.8 12 3.8 14.5 8 C12 12.2 4 12.2 1.5 8 Z" {...stroke} strokeWidth={1.4} />
      <circle cx="8" cy="8" r="2" fill="currentColor" />
    </>,
  ],
  LIGHT_CAVALRY: [
    16,
    <>
      <path
        d="M11.5 14 C12.2 9 11.5 5 9.5 2 L8.5 3.6 C6.5 4.2 4.5 6.5 2.5 8.6 L3.6 10.6 C5 10.6 6 10 7 9.6 L7 14"
        {...stroke}
        strokeWidth={1.4}
      />
      <circle cx="7.6" cy="6" r="0.8" fill="currentColor" />
    </>,
  ],
  HEAVY_CAVALRY: [
    16,
    <path
      d="M11.5 14 C12.2 9 11.5 5 9.5 2 L8.5 3.6 C6.5 4.2 4.5 6.5 2.5 8.6 L3.6 10.6 C5 10.6 6 10 7 9.6 L7 14 Z"
      fill="currentColor"
    />,
  ],
  RAM: [
    16,
    <>
      <path d="M1.5 6.5 H12 L14.5 8 L12 9.5 H1.5 Z" {...stroke} strokeWidth={1.4} />
      <circle cx="4.5" cy="12" r="1.6" {...stroke} strokeWidth={1.4} />
      <circle cx="10" cy="12" r="1.6" {...stroke} strokeWidth={1.4} />
    </>,
  ],
  CATAPULT: [
    16,
    <>
      <path d="M1.5 14 H14.5 M4 14 L8 9.5 L12 14 M5 11.5 L11.5 4.5" {...stroke} strokeWidth={1.4} />
      <circle cx="12.4" cy="3.6" r="1.7" fill="currentColor" />
    </>,
  ],
  NOBLE: [
    16,
    <>
      <path d="M2.5 12 L2 5 L5.5 8 L8 3.5 L10.5 8 L14 5 L13.5 12 Z" {...stroke} strokeWidth={1.4} />
      <path d="M2.5 14 H13.5" {...stroke} strokeWidth={1.4} />
    </>,
  ],
  PALADIN: [
    16,
    <>
      <path
        d="M8 1.8 L13 3.6 V7.6 C13 10.8 10.8 12.8 8 14.2 C5.2 12.8 3 10.8 3 7.6 V3.6 Z"
        {...stroke}
        strokeWidth={1.4}
      />
      <path d="M8 4.5 V10.5 M5.5 7 H10.5" {...stroke} strokeWidth={1.4} />
    </>,
  ],
  // M32b: a banner carried off at a run (a slanted pole with its flag, speed lines behind)
  BANNER_THIEF: [
    16,
    <>
      <path d="M5 14.5 L11.5 1.8" {...stroke} strokeWidth={1.4} />
      <path d="M11 2.8 L15 4.6 L10 7.6 Z" fill="currentColor" />
      <path d="M1 7 H4.5 M1.5 10 H4 M2 13 H3.5" {...stroke} strokeWidth={1.2} />
    </>,
  ],
  shield: [16, <path d="M8 2 L13 4 V8 C13 11 10.5 13 8 14 C5.5 13 3 11 3 8 V4 Z" {...stroke} />],
  pencil: [16, <path d="M3 13 L3.5 10.5 L10.5 3.5 L12.5 5.5 L5.5 12.5 Z" {...stroke} strokeWidth={1.5} />],
  clock: [
    16,
    <>
      <circle cx="8" cy="8" r="5.5" {...stroke} strokeWidth={1.5} />
      <path d="M8 5 V8 L10 9.5" {...stroke} strokeWidth={1.5} />
    </>,
  ],
  // navigation (24 × 24)
  village: [24, <path d="M3 11 L12 4 L21 11 V20 H3 Z" {...stroke} strokeWidth={2} />],
  map: [
    24,
    <path d="M3 6 L9 4 L15 6 L21 4 V18 L15 20 L9 18 L3 20 Z M9 4 V18 M15 6 V20" {...stroke} strokeWidth={1.8} />,
  ],
  troops: [24, <path d="M5 19 L19 5 M19 19 L5 5 M4 16 L8 20 M16 20 L20 16" {...stroke} strokeWidth={2} />],
  /** Troops walking home (a returning movement). */
  returning: [24, <path d="M19 19 V12 A5 5 0 0 0 14 7 H5 M9 3 L5 7 L9 11" {...stroke} strokeWidth={2} />],
  chat: [24, <path d="M4 5 H20 V16 H11 L6 20 V16 H4 Z" {...stroke} strokeWidth={2} />],
  /** A letter: private messages. */
  /** A medal on a ribbon: the achievements. */
  medal: [
    24,
    <>
      <circle cx="12" cy="14" r="5.5" {...stroke} strokeWidth={1.8} />
      <path d="M8.5 3 L10.5 9.5 M15.5 3 L13.5 9.5 M9 3 H15" {...stroke} strokeWidth={1.8} />
    </>,
  ],
  mail: [24, <path d="M3 6 H21 V18 H3 Z M3 7 L12 14 L21 7" {...stroke} strokeWidth={1.8} />],
  /** A banner on a pole: a village changed owner (conquest reports). */
  banner: [24, <path d="M6 21 V3 M6 4 H19 V13 H6 M10 13 V17 L12.5 15 L15 17 V13" {...stroke} strokeWidth={1.8} />],
  /** A guild: three people standing together. */
  guild: [
    24,
    <>
      <circle cx="12" cy="8" r="2.8" {...stroke} strokeWidth={1.8} />
      <circle cx="5.5" cy="10" r="2.2" {...stroke} strokeWidth={1.8} />
      <circle cx="18.5" cy="10" r="2.2" {...stroke} strokeWidth={1.8} />
      <path
        d="M7 20 C7 14.5 17 14.5 17 20 Z M1.5 19 C1.5 15.5 5 14.2 7 15.2 M22.5 19 C22.5 15.5 19 14.2 17 15.2"
        {...stroke}
        strokeWidth={1.8}
      />
    </>,
  ],
  /** One person, drawn like the guild's: the player's own profile (1.5.1). */
  profile: [
    24,
    <>
      <circle cx="12" cy="8" r="3.5" {...stroke} strokeWidth={1.8} />
      <path d="M5 20 C5 13.5 19 13.5 19 20 Z" {...stroke} strokeWidth={1.8} />
    </>,
  ],
  reports: [24, <path d="M6 3 H15 L19 7 V21 H6 Z M9 10 H16 M9 14 H16 M9 18 H13" {...stroke} strokeWidth={1.8} />],
  more: [
    24,
    <>
      <circle cx="5" cy="12" r="2" fill="currentColor" />
      <circle cx="12" cy="12" r="2" fill="currentColor" />
      <circle cx="19" cy="12" r="2" fill="currentColor" />
    </>,
  ],
  /** A cogwheel (1.4: the spokes alone read as a sun, like a light/dark switch). */
  settings: [
    24,
    <>
      <path
        d="M18.9 9.9 L21.4 10 L21.4 14 L18.9 14.1 L18.4 15.4 L20.1 17.2 L17.2 20.1 L15.4 18.4 L14.1 18.9 L14 21.4 L10 21.4 L9.9 18.9 L8.6 18.4 L6.8 20.1 L3.9 17.2 L5.6 15.4 L5.1 14.1 L2.6 14 L2.6 10 L5.1 9.9 L5.6 8.6 L3.9 6.8 L6.8 3.9 L8.6 5.6 L9.9 5.1 L10 2.6 L14 2.6 L14.1 5.1 L15.4 5.6 L17.2 3.9 L20.1 6.8 L18.4 8.6 Z"
        {...stroke}
        strokeWidth={1.6}
      />
      <circle cx="12" cy="12" r="3" {...stroke} strokeWidth={1.8} />
    </>,
  ],
  /** A podium: the ranking (1.4; it used the rally point's flag). */
  ranking: [
    24,
    <path
      d="M2 20 H22 M3 20 V14 H8 V20 M8 20 V8 H16 V20 M16 20 V12 H21 V20 M12 3.5 V5.5"
      {...stroke}
      strokeWidth={1.8}
    />,
  ],
  /** "Centre on my village" on the map. */
  locate: [
    24,
    <>
      <circle cx="12" cy="12" r="6.5" {...stroke} strokeWidth={1.8} />
      <circle cx="12" cy="12" r="2" fill="currentColor" />
      <path d="M12 2 V5 M12 19 V22 M2 12 H5 M19 12 H22" {...stroke} strokeWidth={1.8} />
    </>,
  ],
  /** The movement lines on the map (v1.3): solid behind the troops, dotted ahead. */
  route: [
    24,
    <>
      <path d="M3.5 20.5 L9.5 14.5" {...stroke} strokeWidth={2.2} />
      <circle cx="12" cy="12" r="2.8" fill="currentColor" />
      <path d="M15.5 8.5 L15.51 8.49 M18 6 L18.01 5.99 M20.5 3.5 L20.51 3.49" {...stroke} strokeWidth={2.4} />
    </>,
  ],
  /** A banner keep (1.3): a small fort with a flag. */
  keep: [
    24,
    <>
      <path d="M4.5 21 V11 H7.5 V13 H10 V11 H14 V13 H16.5 V11 H19.5 V21 Z" {...stroke} strokeWidth={1.8} />
      <path d="M10.3 21 V17.6 A1.7 1.7 0 0 1 13.7 17.6 V21" {...stroke} strokeWidth={1.8} />
      <path d="M12 11 V2.8 M12 3.2 L17.5 5 L12 6.8" {...stroke} strokeWidth={1.8} />
    </>,
  ],
  /** The map's layers (1.3): two sheets stacked on a third. */
  layers: [
    24,
    <>
      <path d="M12 3.5 L21 8.5 L12 13.5 L3 8.5 Z" {...stroke} strokeWidth={1.8} />
      <path d="M3 12.5 L12 17.5 L21 12.5 M3 16.5 L12 21.5 L21 16.5" {...stroke} strokeWidth={1.8} />
    </>,
  ],
  /** The overview map (M11b): the map with the visible part as a small rectangle. */
  overview: [
    24,
    <>
      <rect x="3.5" y="3.5" width="17" height="17" rx="2" {...stroke} strokeWidth={1.8} />
      <rect x="10" y="8" width="7" height="6" fill="currentColor" />
      <path d="M6.5 16.5 H8.5 M6.5 7 H7.5" {...stroke} strokeWidth={1.8} />
    </>,
  ],
  info: [24, <path d="M12 11 V17 M12 7 V7.5 M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12" {...stroke} strokeWidth={2} />],
  lock: [
    24,
    <>
      <rect x="5" y="11" width="14" height="9" rx="2" {...stroke} strokeWidth={2} />
      <path d="M8 11 V8 A4 4 0 0 1 16 8 V11" {...stroke} strokeWidth={2} />
    </>,
  ],
  chevronLeft: [24, <path d="M15 6 L9 12 L15 18" {...stroke} strokeWidth={2} />],
  chevronRight: [24, <path d="M9 6 L15 12 L9 18" {...stroke} strokeWidth={2} />],
  chevronUp: [24, <path d="M6 15 L12 9 L18 15" {...stroke} strokeWidth={2} />],
  chevronDown: [24, <path d="M6 9 L12 15 L18 9" {...stroke} strokeWidth={2} />],
  close: [24, <path d="M6 6 L18 18 M18 6 L6 18" {...stroke} strokeWidth={2} />],
  /** A finished order (1.3). */
  check: [24, <path d="M5 12.5 L10 17.5 L19 7" {...stroke} strokeWidth={2.4} />],
  plus: [24, <path d="M12 5 V19 M5 12 H19" {...stroke} strokeWidth={2} />],
  minus: [24, <path d="M5 12 H19" {...stroke} strokeWidth={2} />],
  /** Sending a report to other players (M40): a paper plane. */
  send: [24, <path d="M3.5 11 L20.5 3.5 L13 20.5 L11 13 Z M11 13 L20.5 3.5" {...stroke} strokeWidth={1.8} />],
  /** The night bonus (1.4): a crescent. */
  moon: [16, <path d="M10.5 2.5 A5.75 5.75 0 1 0 13.5 11.5 A4.5 4.5 0 0 1 10.5 2.5 Z" {...stroke} strokeWidth={1.4} />],
  /** Push notifications (1.5): a bell. */
  bell: [
    24,
    <>
      <path d="M6 16.5 V11 A6 6 0 0 1 18 11 V16.5 L19.5 18.5 H4.5 Z" {...stroke} strokeWidth={1.8} />
      <path d="M10 21 H14" {...stroke} strokeWidth={1.8} />
    </>,
  ],
  /** Deleting a report. */
  trash: [
    24,
    <path
      d="M4 7 H20 M9 7 V4 H15 V7 M6.5 7 L7.5 20 H16.5 L17.5 7 M10 11 V16 M14 11 V16"
      {...stroke}
      strokeWidth={1.8}
    />,
  ],
} satisfies Record<string, [number, ReactNode]>;

/** 'wood' | 'clay' | … | 'close' – every key of the object above. */
export type IconName = keyof typeof icons;

type IconProps = SVGProps<SVGSVGElement> & { name: IconName };

/** Decorative by default (aria-hidden): put the meaning into text next to it or into an aria-label. */
export function Icon({ name, ...svgProps }: IconProps) {
  const [size, drawing] = icons[name];
  return (
    <svg viewBox={`0 0 ${size} ${size}`} width="1em" height="1em" aria-hidden="true" focusable="false" {...svgProps}>
      {drawing}
    </svg>
  );
}

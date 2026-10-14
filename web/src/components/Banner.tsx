import type { ReactNode } from 'react';
import type { BannerType } from '../game/gameData';
import { useT } from '../i18n';

/**
 * The look of a banner (02-game-rules §21.10): a hanging standard on a crossbar. The tier reads
 * without colour (straight edge and one pip / swallow-tail, tassels and two pips / gold finial,
 * long tassels and three pips), the type through an emblem on a field colour. One component draws
 * all 27, so dark mode needs no second set; a thin ink outline carries the shapes.
 */

const INK = '#2b2118';

/** Field and emblem colour per type: green, brown, blue, crimson, slate, teal, amber, plum, ivory. */
const looks: Record<BannerType, { field: string; emblem: string }> = {
  HARVEST: { field: '#3f7a4a', emblem: '#ffffff' },
  BUILDERS: { field: '#85592f', emblem: '#ffffff' },
  DRILL: { field: '#3c6aa8', emblem: '#ffffff' },
  WAR: { field: '#a8372a', emblem: '#ffffff' },
  BULWARK: { field: '#5d6b7a', emblem: '#ffffff' },
  HASTE: { field: '#2a8584', emblem: '#ffffff' },
  TREASURY: { field: '#b9852a', emblem: '#ffffff' },
  PLUNDER: { field: '#7a4a7a', emblem: '#ffffff' },
  // ivory is too light for a white emblem
  OATH: { field: '#efe6cf', emblem: '#5a4320' },
};

/** The trim colour of a tier: bronze, silver, gold (the achievement tokens). */
const trims = ['var(--badge-bronze, #a8683a)', 'var(--badge-silver, #8f9aa5)', 'var(--badge-gold, #c9951c)'] as const;

/** Nine emblems on a 24 × 24 grid, drawn in the emblem colour; `field` is for cut-outs. */
const emblems: Record<BannerType, (emblem: string, field: string) => ReactNode> = {
  // an ear of wheat
  HARVEST: (e) => (
    <>
      <path d="M12 22 V9" stroke={e} strokeWidth="1.8" strokeLinecap="round" />
      <path d="M12 8.5 C10 6.5 10 4.5 12 2.5 C14 4.5 14 6.5 12 8.5 Z" fill={e} />
      <path
        d="M12 13 C9 13 7 11 7 8.5 C10 8.5 12 10 12 13 Z M12 13 C15 13 17 11 17 8.5 C14 8.5 12 10 12 13 Z"
        fill={e}
      />
      <path
        d="M12 18 C9 18 7 16 7 13.5 C10 13.5 12 15 12 18 Z M12 18 C15 18 17 16 17 13.5 C14 13.5 12 15 12 18 Z"
        fill={e}
      />
    </>
  ),
  // a hammer
  BUILDERS: (e) => (
    <>
      <path d="M6 20.5 L14.5 12" stroke={e} strokeWidth="2.8" strokeLinecap="round" />
      <path d="M8.5 8 L13 3.5 L20.5 11 L16 15.5 Z" fill={e} />
    </>
  ),
  // two crossed spears
  DRILL: (e) => (
    <>
      <path d="M5 20.5 L19 6 M19 20.5 L5 6" stroke={e} strokeWidth="1.8" strokeLinecap="round" />
      <path d="M19.8 3 L21.5 8.2 L16.3 6.5 Z M4.2 3 L7.7 6.5 L2.5 8.2 Z" fill={e} />
    </>
  ),
  // an axe (the map keeps crossed swords for attacks)
  WAR: (e) => (
    <>
      <path d="M7.5 21.5 L15 4" stroke={e} strokeWidth="2.2" strokeLinecap="round" />
      <path d="M13.5 4.5 C19 3.5 21.5 8 20.5 13 C17 12.5 14.5 11 13 8.5 Z" fill={e} />
    </>
  ),
  // a tower with a door
  BULWARK: (e) => (
    <path
      fillRule="evenodd"
      fill={e}
      d="M5.5 21.5 V9 H8 V6 H10.5 V9 H13.5 V6 H16 V9 H18.5 V21.5 Z M10 21.5 V16 A2 2 0 0 1 14 16 V21.5 Z"
    />
  ),
  // an arrow with speed lines
  HASTE: (e) => (
    <>
      <path d="M9 12 H20 M16 7.5 L20.5 12 L16 16.5" stroke={e} strokeWidth="2" strokeLinecap="round" fill="none" />
      <path d="M2.5 7.5 H11 M2.5 16.5 H11 M2.5 12 H6" stroke={e} strokeWidth="1.6" strokeLinecap="round" />
    </>
  ),
  // a coin
  TREASURY: (e) => (
    <>
      <circle cx="12" cy="12" r="8.5" stroke={e} strokeWidth="2" fill="none" />
      <circle cx="12" cy="12" r="4" fill={e} />
    </>
  ),
  // a sack
  PLUNDER: (e, f) => (
    <>
      <path
        d="M8.5 3 H15.5 L14 7 C18.5 9 19.5 13.5 18.5 17 C17.5 20.5 15 21.5 12 21.5 C9 21.5 6.5 20.5 5.5 17 C4.5 13.5 5.5 9 10 7 Z"
        fill={e}
      />
      <path d="M9.5 7.6 H14.5" stroke={f} strokeWidth="1.4" strokeLinecap="round" />
    </>
  ),
  // a crown
  OATH: (e) => (
    <>
      <path d="M3.5 18 L3 7.5 L8.2 11.5 L12 4.5 L15.8 11.5 L21 7.5 L20.5 18 Z" fill={e} />
      <path d="M4 21 H20" stroke={e} strokeWidth="2" strokeLinecap="round" />
    </>
  ),
};

interface BannerProps {
  type: BannerType;
  /** 1–3 (anything else is drawn as the nearest). */
  tier: number;
  /** Width in px: 20 in lists and reports, 56 on the Banner Hall page, 96 in the stash. The height follows. */
  size?: number;
  /** Greyed out: the banner is dormant (the village has no Banner Hall). */
  dormant?: boolean;
}

/** `<Banner type="WAR" tier={2} />`: an image labelled "War banner, tier II". */
export function Banner({ type, tier, size = 56, dormant = false }: BannerProps) {
  const t = useT();
  const level = Math.min(3, Math.max(1, Math.round(tier)));
  const { field, emblem } = looks[type];
  const trim = trims[level - 1];
  // the cloth hangs from y 10 down to 58; from tier II on its lower edge is a swallow-tail
  const cloth = level === 1 ? 'M8 10 H40 V58 H8 Z' : 'M8 10 H40 V60 L33 54.5 L24 61 L15 54.5 L8 60 Z';
  const pips = Array.from({ length: level }, (_, index) => 24 + (index - (level - 1) / 2) * 6.5);
  return (
    <svg
      viewBox="0 0 48 72"
      width={size}
      height={(size * 72) / 48}
      role="img"
      aria-label={t.banners.label(t.banners.types[type], level)}
      style={dormant ? { opacity: 0.45, filter: 'grayscale(0.7)' } : undefined}
      focusable="false"
    >
      {level === 3 && <circle cx="24" cy="3.2" r="2.6" fill={trim} stroke={INK} strokeWidth="0.8" />}
      <path d="M24 6 V10" stroke={INK} strokeWidth="2" />
      {level >= 2 && (
        <g stroke={trim} strokeWidth="2" strokeLinecap="round">
          <path d={`M9 60 v${level === 3 ? 8 : 5}`} />
          <path d={`M39 60 v${level === 3 ? 8 : 5}`} />
        </g>
      )}
      <path d={cloth} fill={field} stroke={trim} strokeWidth="3" strokeLinejoin="round" />
      <path d={cloth} fill="none" stroke={INK} strokeWidth="0.9" strokeLinejoin="round" />
      <g transform="translate(12 14)">{emblems[type](emblem, field)}</g>
      {pips.map((x) => (
        <circle key={x} cx={x} cy="46" r="2" fill={trim} stroke={INK} strokeWidth="0.6" />
      ))}
      <path d="M3 10 H45" stroke={INK} strokeWidth="3.4" strokeLinecap="round" />
      <path d="M3 10 H45" stroke="#8a6a3c" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

import type { ReactNode } from 'react';
import { MAX_LEVEL, isAchievementCategory, type AchievementCategory, type Badge } from '../game/achievements';
import styles from './AchievementBadge.module.css';

const glyphStroke = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
} as const;

/** One drawing per category (24 × 24): the badge says what kind of deed, its rim says how far. */
const glyphs: Record<AchievementCategory, ReactNode> = {
  BUILDING: <path d="M4 11 L12 4 L20 11 V20 H4 Z M10 20 V14 H14 V20" {...glyphStroke} />,
  ARMY: <path d="M12 3 L19 6 V12 C19 16.5 15.5 19.5 12 21 C8.5 19.5 5 16.5 5 12 V6 Z" {...glyphStroke} />,
  COMBAT: <path d="M5 5 L19 19 M19 5 L5 19 M4 8 L8 4 M16 20 L20 16 M20 8 L16 4 M8 20 L4 16" {...glyphStroke} />,
  // a stretch of wall with a closed gate (1.4)
  DEFENCE: <path d="M4 20 V8 H7 V5 H10 V8 H14 V5 H17 V8 H20 V20 Z M10 20 V15 H14 V20" {...glyphStroke} />,
  CONQUEST: <path d="M7 21 V4 M7 5 H18 L15 9 L18 13 H7" {...glyphStroke} />,
  // a standard hanging from its crossbar (M32c)
  BANNER: <path d="M4 4 H20 M7 4 V18 L12 15 L17 18 V4 M12 8 V11" {...glyphStroke} />,
  TRADE: (
    <>
      <circle cx="12" cy="12" r="8" {...glyphStroke} />
      <path d="M9.5 9.5 H14.5 M9.5 14.5 H14.5 M12 7.5 V16.5" {...glyphStroke} />
    </>
  ),
  SOCIAL: <path d="M4 5 H20 V16 H11 L6 20 V16 H4 Z" {...glyphStroke} />,
  TIME: (
    <>
      <circle cx="12" cy="12" r="8" {...glyphStroke} />
      <path d="M12 7 V12 L15 14" {...glyphStroke} />
    </>
  ),
};

const levelTones = ['bronze', 'silver', 'gold'] as const;

interface AchievementBadgeProps {
  badge: Badge;
  /** Side length in px. */
  size?: number;
}

/**
 * The round badge of an achievement (05-frontend §8). Not earned: dashed grey rim. Earned: a
 * bronze, silver or gold rim for the level of a levelled achievement, a green one for a one-time
 * achievement. Purely decorative (`aria-hidden`): the text next to it carries name and level.
 */
export function AchievementBadge({ badge, size = 48 }: AchievementBadgeProps) {
  const category: AchievementCategory = isAchievementCategory(badge.category) ? badge.category : 'TIME';
  const level = Math.min(badge.level, MAX_LEVEL);
  const tone = level === 0 ? 'locked' : badge.leveled ? (levelTones[level - 1] ?? 'gold') : 'once';
  return (
    <svg
      viewBox="0 0 48 48"
      width={size}
      height={size}
      className={[styles.badge, styles[tone]].join(' ')}
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="24" cy="24" r="21.5" className={styles.rim} />
      <circle cx="24" cy="24" r="16.5" className={styles.face} />
      <g transform="translate(12 12)" className={styles.glyph}>
        {glyphs[category]}
      </g>
    </svg>
  );
}

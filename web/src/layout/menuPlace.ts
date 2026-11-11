import type { CSSProperties } from 'react';

/** The gap between the button and the menu. */
const GAP_PX = 8;

/**
 * A FloatingMenu's place under its button, the height capped to the window: its right edge on the
 * button's (the gear, at the right) or its left edge on the button's (the village switch, at the left).
 */
export function menuPlaceUnder(button: HTMLElement, align: 'left' | 'right'): CSSProperties {
  const rect = button.getBoundingClientRect();
  const top = rect.bottom + GAP_PX;
  const maxHeight = `calc(100dvh - ${top}px - var(--space-3))`;
  return align === 'right'
    ? { top, right: Math.max(document.documentElement.clientWidth - rect.right, GAP_PX), maxHeight }
    : { top, left: Math.max(rect.left, GAP_PX), maxHeight };
}

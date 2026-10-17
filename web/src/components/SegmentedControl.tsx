import { useLayoutEffect, useRef, useState } from 'react';
import styles from './SegmentedControl.module.css';

export interface SegmentedOption<T extends string> {
  value: T;
  label: string;
  /** Language of the label, if it differs from the page (e.g. "Deutsch" on an English page). */
  lang?: string;
}

interface SegmentedControlProps<T extends string> {
  /** Names the group for screen readers. */
  label: string;
  options: readonly SegmentedOption<T>[];
  value: T;
  onChange: (value: T) => void;
  /**
   * Many or long options (the market's five tabs): on phones they share the width with less
   * padding, and the row scrolls sideways if they still don't fit.
   */
  scroll?: boolean;
  /** Slimmer, for a card's header (the village page's picture | list switch); still 44 px tall to tap. */
  compact?: boolean;
}

interface Pill {
  left: number;
  width: number;
}

function samePill(a: Pill | null, b: Pill | null): boolean {
  return a === b || (a !== null && b !== null && a.left === b.left && a.width === b.width);
}

/**
 * A row of buttons where exactly one is selected (theme, language …). Generic: `T` is the type of
 * the values, e.g. 'system' | 'light' | 'dark', so onChange gets exactly that type back.
 *
 * The selected option's background is one pill behind the buttons that slides to the new choice
 * (1.3); it follows the button when the row changes size. Reduced motion: it jumps.
 */
export function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
  scroll = false,
  compact = false,
}: SegmentedControlProps<T>) {
  const group = useRef<HTMLDivElement>(null);
  const [pill, setPill] = useState<Pill | null>(null);
  // the labels decide the buttons' widths (another language, other options)
  const labels = options.map((option) => option.label).join('\n');

  useLayoutEffect(() => {
    const element = group.current;
    if (element === null) {
      return;
    }
    const measure = () => {
      const pressed = element.querySelector<HTMLButtonElement>('[aria-pressed="true"]');
      const next =
        pressed === null || pressed.offsetWidth === 0 ? null : { left: pressed.offsetLeft, width: pressed.offsetWidth };
      setPill((current) => (samePill(current, next) ? current : next));
    };
    measure();
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [value, labels]);

  const classes = [styles.group, scroll && styles.scroll, compact && styles.compact, pill !== null && styles.sliding]
    .filter(Boolean)
    .join(' ');
  return (
    <div ref={group} className={classes} role="group" aria-label={label}>
      {pill !== null && (
        <span className={styles.pill} style={{ left: pill.left, width: pill.width }} aria-hidden="true" />
      )}
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={styles.option}
          lang={option.lang}
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

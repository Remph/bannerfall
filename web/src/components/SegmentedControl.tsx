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
}

/**
 * A row of buttons where exactly one is selected (theme, language …). Generic: `T` is the type of
 * the values, e.g. 'system' | 'light' | 'dark', so onChange gets exactly that type back.
 */
export function SegmentedControl<T extends string>({ label, options, value, onChange }: SegmentedControlProps<T>) {
  return (
    <div className={styles.group} role="group" aria-label={label}>
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

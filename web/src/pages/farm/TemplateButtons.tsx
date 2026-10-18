import { UnitSummary } from '../../components/UnitSummary';
import type { FarmTemplate, TemplateLetter } from '../../game/farmTemplates';
import { fitsHome } from '../../game/farm';
import type { UnitMap } from '../../game/troops';
import styles from './FarmTemplates.module.css';

/**
 * The template's letter in a small square; `null` = the entry's own units (a dash). `label` is read
 * out instead ("Template A") where the badge is the only sign of it.
 */
export function TemplateLetterBadge({ letter, label }: { letter: TemplateLetter | null; label?: string }) {
  return (
    <>
      <span className={letter === null ? `${styles.letter} ${styles.own}` : styles.letter} aria-hidden="true">
        {letter ?? '–'}
      </span>
      {label !== undefined && <span className="sr-only">{label}</span>}
    </>
  );
}

interface TemplateButtonsProps {
  templates: FarmTemplate[];
  /** Units at home: a template they don't cover is greyed out (when `needsHome`). */
  home: UnitMap;
  /** Send buttons need the units at home; picking a template for a list entry doesn't. */
  needsHome?: boolean;
  /** The picked one (a choice, not an action). */
  selected?: TemplateLetter | null;
  /** The one waiting for its second tap. */
  armed?: TemplateLetter;
  armedLabel?: string;
  disabled?: boolean;
  label: (letter: TemplateLetter) => string;
  onPick: (template: FarmTemplate) => void;
}

/** One button per template: its letter and its units. */
export function TemplateButtons({
  templates,
  home,
  needsHome = false,
  selected,
  armed,
  armedLabel,
  disabled = false,
  label,
  onPick,
}: TemplateButtonsProps) {
  return (
    <ul className={styles.buttons}>
      {templates.map((template) => {
        const isArmed = armed === template.letter;
        return (
          <li key={template.letter}>
            <button
              type="button"
              className={isArmed ? `${styles.button} ${styles.armed}` : styles.button}
              aria-pressed={selected === undefined ? undefined : selected === template.letter}
              aria-label={isArmed ? armedLabel : label(template.letter)}
              disabled={disabled || (needsHome && !fitsHome(template.units, home))}
              onClick={() => onPick(template)}
            >
              <TemplateLetterBadge letter={template.letter} />
              {isArmed ? armedLabel : <UnitSummary units={template.units} className={styles.units} />}
            </button>
          </li>
        );
      })}
    </ul>
  );
}

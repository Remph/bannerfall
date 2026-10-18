import type { FarmTemplate, TemplateLetter } from '../../game/farmTemplates';
import type { UnitMap } from '../../game/troops';
import { useT } from '../../i18n';
import styles from './FarmTemplates.module.css';
import { TemplateButtons, TemplateLetterBadge } from './TemplateButtons';
import { TemplatePicker } from './TemplatePicker';

interface TemplateChoiceProps {
  templates: FarmTemplate[];
  /** The picked template, or null for the entry's own units. */
  value: TemplateLetter | null;
  onChange: (letter: TemplateLetter | null) => void;
  /** The own units, while `value` is null. */
  counts: UnitMap;
  onCounts: (type: keyof UnitMap, count: number) => void;
  home: UnitMap;
}

/** What a farm list entry sends: one of the templates, or its own units (the unit rows open then). */
export function TemplateChoice({ templates, value, onChange, counts, onCounts, home }: TemplateChoiceProps) {
  const t = useT();
  return (
    <div className={styles.choice}>
      <p className={styles.choiceLabel}>{t.farm.troops}</p>
      <div className={styles.buttons}>
        <TemplateButtons
          templates={templates}
          home={home}
          selected={value}
          label={t.farm.pickTemplate}
          onPick={(template) => onChange(template.letter)}
        />
        <button type="button" className={styles.button} aria-pressed={value === null} onClick={() => onChange(null)}>
          <TemplateLetterBadge letter={null} />
          {t.farm.ownUnits}
        </button>
      </div>
      {value === null && (
        <>
          <p className={styles.choiceHint}>{t.farm.ownUnitsHint}</p>
          <TemplatePicker counts={counts} home={home} onChange={onCounts} />
        </>
      )}
    </div>
  );
}

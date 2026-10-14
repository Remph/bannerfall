import { Icon } from '../../components/Icon';
import { UnitCountInput } from '../../components/UnitCountInput';
import type { UnitType } from '../../game/gameData';
import type { UnitMap } from '../../game/troops';
import { useT } from '../../i18n';
import styles from './RallyPage.module.css';

interface UnitPickerProps {
  /** The rows, in unit order. */
  types: readonly UnitType[];
  /** How many of each there are: the "all" chip and the most that can be typed. */
  available: UnitMap;
  counts: UnitMap;
  onChange: (type: UnitType, count: number) => void;
  /** Screen-reader name of a count field, e.g. "Number of units to send: Spearman". */
  label: (unit: string) => string;
}

/**
 * How many units of each type go (send form, withdraw/send back): one row per type with − / +, the
 * count and an "all 120" chip. On phones the field moves below the name.
 */
export function UnitPicker({ types, available, counts, onChange, label }: UnitPickerProps) {
  const t = useT();
  return (
    <ul className={styles.pickList}>
      {types.map((type) => {
        const max = available[type] ?? 0;
        return (
          <li key={type} className={styles.pickRow}>
            <span className={styles.pickName}>
              <span className={styles.unitIcon}>
                <Icon name={type} />
              </span>
              {t.units[type]}
            </span>
            <span className={styles.pickInput}>
              <UnitCountInput
                value={counts[type] ?? 0}
                onChange={(count) => onChange(type, count)}
                max={max}
                // nothing to type beyond what is there
                limit={max}
                label={label(t.units[type])}
                maxLabel={t.rally.all}
              />
            </span>
          </li>
        );
      })}
    </ul>
  );
}

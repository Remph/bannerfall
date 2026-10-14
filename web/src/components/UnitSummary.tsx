import { formatNumber } from '../game/format';
import { presentUnits, type UnitMap } from '../game/troops';
import { levelOf, type UnitLevelMap } from '../game/unitLevels';
import { useI18n } from '../i18n';
import { Icon } from './Icon';
import styles from './UnitSummary.module.css';

/**
 * Units in one compact line: icon and count per unit type (only the types that are there, in unit
 * order). The unit names are read out to screen readers and shown as tooltip with a mouse.
 */
export function UnitSummary({
  units,
  className,
  levels = {},
}: {
  units: UnitMap;
  className?: string;
  /** The research levels above 1 (M30): shown small after the count. */
  levels?: UnitLevelMap;
}) {
  const { t, language } = useI18n();
  return (
    <ul className={[styles.units, className].filter(Boolean).join(' ')}>
      {presentUnits(units).map(([type, count]) => (
        <li key={type} className={styles.unit} title={t.units[type]}>
          <span className={styles.icon}>
            <Icon name={type} />
          </span>
          <span className="sr-only">{t.units[type]}: </span>
          {formatNumber(count, language)}
          {levelOf(levels, type) > 1 && <span className={styles.level}>{t.smithy.level(levelOf(levels, type))}</span>}
        </li>
      ))}
    </ul>
  );
}

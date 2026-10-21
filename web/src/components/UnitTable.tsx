import { formatNumber } from '../game/format';
import type { UnitRow } from '../game/reports';
import { levelOf, type UnitLevelMap } from '../game/unitLevels';
import { useI18n } from '../i18n';
import { Icon } from './Icon';
import styles from './UnitTable.module.css';

interface UnitTableProps {
  rows: readonly UnitRow[];
  /** Names the table for screen readers, e.g. "Attacker". */
  caption: string;
  /** Header of the first number column: "Sent" for the attacker, "There" for the defender. */
  countLabel: string;
  /** False: only the count column (the defenders against scouts only, who never lose units). */
  showLosses?: boolean;
  /** The research levels above 1 of these units (M30), shown next to the unit name. */
  levels?: UnitLevelMap;
}

/**
 * Units of one side of a battle: one row per unit type, then how many there were, died and are
 * left. Unit types run down, not across, so it fits a phone; on a very narrow one the "survived"
 * column goes (a container query), and anything still too wide scrolls inside its own box
 * (05-frontend §8).
 */
export function UnitTable({ rows, caption, countLabel, showLosses = true, levels = {} }: UnitTableProps) {
  const { t, language } = useI18n();
  return (
    <div className={styles.scroll}>
      <table className={styles.table}>
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            <th scope="col">{t.reports.unit}</th>
            <th scope="col">{countLabel}</th>
            {showLosses && (
              <>
                <th scope="col">{t.reports.lost}</th>
                <th scope="col" className={styles.left}>
                  {t.reports.survived}
                </th>
              </>
            )}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.type}>
              <th scope="row">
                <span className={styles.unit}>
                  <span className={styles.icon}>
                    <Icon name={row.type} />
                  </span>
                  {/* name and badge flow as text beside the icon: a long German name wraps there, and the
                      badge follows its last word (a flex-wrap row broke the icon and the badge off) */}
                  <span className={styles.name}>
                    {t.units[row.type]}
                    {levelOf(levels, row.type) > 1 && (
                      <>
                        {' '}
                        <span className={styles.level}>{t.smithy.level(levelOf(levels, row.type))}</span>
                      </>
                    )}
                  </span>
                </span>
              </th>
              <td>{formatNumber(row.count, language)}</td>
              {showLosses && (
                <>
                  <td className={row.lost > 0 ? styles.lost : styles.none}>{formatNumber(row.lost, language)}</td>
                  <td className={row.left > 0 ? styles.left : `${styles.left} ${styles.none}`}>
                    {formatNumber(row.left, language)}
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

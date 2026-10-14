import { Icon } from '../../components/Icon';
import { formatNumber } from '../../game/format';
import { troopOverview } from '../../game/troops';
import type { Troops } from '../../api/queries/troops';
import { useI18n } from '../../i18n';
import styles from './RallyPage.module.css';

/**
 * The troop overview of the rally point (M33): one row per unit type with the village's own units
 * at home, away and in all, plus the support of other villages standing here (a column only while
 * there is some). Unit types run down like the battle reports' unit tables, so it fits a phone.
 */
export function TroopOverview({ troops }: { troops: Troops }) {
  const { t, language } = useI18n();
  const rows = troopOverview(troops);
  if (rows.length === 0) {
    return <p className={styles.muted}>{t.rally.noTroopsAnywhere}</p>;
  }
  const withSupport = rows.some((row) => row.support > 0);
  const cell = (count: number) => (
    <td className={count === 0 ? styles.overviewNone : undefined}>{formatNumber(count, language)}</td>
  );
  return (
    <>
      <div className={styles.overviewScroll}>
        <table className={styles.overview}>
          <caption className="sr-only">{t.rally.overviewCaption}</caption>
          <thead>
            <tr>
              <th scope="col">{t.rally.overviewUnit}</th>
              <th scope="col">{t.rally.atHome}</th>
              <th scope="col">{t.rally.overviewOutside}</th>
              <th scope="col">{t.rally.overviewTotal}</th>
              {withSupport && (
                <th scope="col" className={styles.overviewSupport}>
                  {t.rally.overviewSupport}
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.type}>
                <th scope="row">
                  <span className={styles.overviewUnit}>
                    <span className={styles.overviewIcon}>
                      <Icon name={row.type} />
                    </span>
                    {t.units[row.type]}
                  </span>
                </th>
                {cell(row.home)}
                {cell(row.outside)}
                {/* a type only here as support has a total of 0: muted like the other zeros */}
                <td className={`${styles.overviewTotal} ${row.total === 0 ? styles.overviewNone : ''}`}>
                  {formatNumber(row.total, language)}
                </td>
                {withSupport && (
                  <td className={`${styles.overviewSupport} ${row.support === 0 ? styles.overviewNone : ''}`}>
                    {formatNumber(row.support, language)}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className={styles.overviewHint}>
        {t.rally.overviewOutsideHint}
        {withSupport && ` ${t.rally.overviewSupportHint}`}
      </p>
    </>
  );
}

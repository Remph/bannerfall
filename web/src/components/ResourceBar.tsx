import { Link } from 'react-router';
import type { Village } from '../api/queries/villages';
import { formatNumber } from '../game/format';
import { isFull, projectResources, resourceTypes } from '../game/resources';
import { useServerNow } from '../game/serverTime';
import { useI18n, useT } from '../i18n';
import { warehousePath } from '../pages/warehouse/warehousePaths';
import { Icon } from './Icon';
import styles from './ResourceBar.module.css';

/**
 * Wood, clay, iron and population of a village, ticking once per second (05-frontend §5). Each value
 * shows its maximum ("812/1,000": warehouse or farm). Phone: four compact columns, or with `compact`
 * only the amounts in one slim row (red when full). Desktop: always a row with maximum, production
 * and a fill bar per resource (`compact` is ignored there). Wood, clay and iron open the warehouse page
 * (M35); population stays plain text, as the warehouse page doesn't cover the farm.
 */
export function ResourceBar({ village, compact = false }: { village: Village; compact?: boolean }) {
  const { t, language } = useI18n();
  const now = useServerNow();
  const amounts = projectResources(village, now);
  const capacity = village.storageCapacity;
  const capacityShown = formatNumber(capacity, language);
  const { used, capacity: farm } = village.population;

  return (
    <ul className={compact ? `${styles.bar} ${styles.compact}` : styles.bar} aria-label={t.resources.title}>
      {resourceTypes.map((type) => {
        const amount = amounts[type];
        const full = isFull(amount, capacity);
        const rate = formatNumber(village.production[type], language);
        return (
          <li key={type} className={full ? `${styles.item} ${styles.full}` : styles.item}>
            <Link className={styles.link} to={warehousePath(village.id)} title={t.resources.toWarehouse}>
              <Icon name={type} className={styles.icon} />
              <span className={styles.text}>
                <span className="sr-only">{t.resources[type]}: </span>
                <Amount value={formatNumber(amount, language)} max={capacityShown} />
                <span className={styles.detail}>
                  <span aria-hidden="true">{t.resources.perHour(rate)}</span>
                  <span className="sr-only">, {t.resources.perHourLong(rate)}</span>
                  {full && <span className="sr-only">, {t.resources.full}</span>}
                </span>
                <Fill fraction={amount / capacity} />
              </span>
            </Link>
          </li>
        );
      })}
      <li className={[styles.item, styles.plain, used >= farm && styles.full].filter(Boolean).join(' ')}>
        <Icon name="population" className={styles.icon} />
        <span className={styles.text}>
          <span className="sr-only">{t.resources.population}: </span>
          <Amount value={formatNumber(used, language)} max={formatNumber(farm, language)} />
          <span className={styles.detail} aria-hidden="true">
            {t.resources.population}
          </span>
          {used >= farm && <span className="sr-only">, {t.resources.populationFull}</span>}
          <Fill fraction={used / farm} />
        </span>
      </li>
    </ul>
  );
}

/** "812/1,000": the value in bold, the maximum lighter; it wraps below on narrow screens. */
function Amount({ value, max }: { value: string; max: string }) {
  const t = useT();
  return (
    <span className={styles.amountLine}>
      <span className={styles.amount}>{value}</span>
      <span className={styles.max} aria-hidden="true">
        /{max}
      </span>
      <span className="sr-only"> ({t.resources.capacity(max)})</span>
    </span>
  );
}

/** Thin bar under each value on desktop: how full the warehouse (or farm) is. */
function Fill({ fraction }: { fraction: number }) {
  const percent = Math.min(100, Math.max(0, fraction * 100));
  return (
    <span className={styles.fill} aria-hidden="true">
      <span style={{ width: `${percent}%` }} />
    </span>
  );
}

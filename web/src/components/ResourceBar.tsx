import { Link } from 'react-router';
import type { Village } from '../api/queries/villages';
import { formatNumber } from '../game/format';
import { isFarmAlmostFull, isFull, projectResources, resourceTypes } from '../game/resources';
import { useGameNow } from '../game/serverTime';
import { useI18n, useT } from '../i18n';
import { warehousePath } from '../pages/warehouse/warehousePaths';
import { useWorldEnded } from '../layout/worldEnd';
import { Icon } from './Icon';
import styles from './ResourceBar.module.css';
import { useCountedValue } from './useCountedValue';

/**
 * Wood, clay, iron and population of a village, ticking once per second (05-frontend §5). Each value
 * shows its maximum ("812/1,000": warehouse or farm). Phone: four compact columns, or with `compact`
 * only the amounts in one slim row (red when full, the farm amber when almost full). Desktop: always a row
 * with maximum, production and a fill bar per resource (`compact` is ignored there). Wood, clay and iron open
 * the warehouse page (M35); population stays plain text, as the warehouse page doesn't cover the farm. A jump
 * (loot, a trade, a payment) counts to the new amount, a gain glowing gold for a moment (UI review 1.3).
 */
export function ResourceBar({ village, compact = false }: { village: Village; compact?: boolean }) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const amounts = projectResources(village, now);
  const capacity = village.storageCapacity;
  const capacityShown = formatNumber(capacity, language);
  const { used, capacity: farm } = village.population;
  const farmAlmostFull = isFarmAlmostFull(used, farm);

  const ended = useWorldEnded();
  return (
    <ul className={compact ? `${styles.bar} ${styles.compact}` : styles.bar} aria-label={t.resources.title}>
      {resourceTypes.map((type) => {
        const amount = amounts[type];
        const full = isFull(amount, capacity);
        const rate = formatNumber(village.production[type], language);
        return (
          // keyed by village too, so switching villages shows the other amounts at once
          <li key={`${village.id}-${type}`} className={full ? `${styles.item} ${styles.full}` : styles.item}>
            <Link className={styles.link} to={warehousePath(village.id)} title={t.resources.toWarehouse}>
              <Icon name={type} className={styles.icon} />
              <span className={styles.text}>
                <span className="sr-only">{t.resources[type]}: </span>
                <CountedAmount target={amount} perHour={village.production[type]} max={capacityShown} />
                <span className={styles.detail}>
                  {/* an ended world is frozen (v1.3): nothing grows */}
                  <span aria-hidden="true">{ended ? t.worldEnd.paused : t.resources.perHour(rate)}</span>
                  <span className="sr-only">, {ended ? t.worldEnd.paused : t.resources.perHourLong(rate)}</span>
                  {full && <span className="sr-only">, {t.resources.full}</span>}
                </span>
                <Fill fraction={amount / capacity} />
              </span>
            </Link>
          </li>
        );
      })}
      <li
        className={[styles.item, styles.plain, used >= farm && styles.full, farmAlmostFull && styles.almostFull]
          .filter(Boolean)
          .join(' ')}
      >
        <Icon name="population" className={styles.icon} />
        <span className={styles.text}>
          <span className="sr-only">{t.resources.population}: </span>
          <Amount value={formatNumber(used, language)} max={formatNumber(farm, language)} />
          <span className={styles.detail} aria-hidden="true">
            {t.resources.population}
          </span>
          {used >= farm && <span className="sr-only">, {t.resources.populationFull}</span>}
          {farmAlmostFull && <span className="sr-only">, {t.resources.populationAlmostFull}</span>}
          <Fill fraction={used / farm} />
        </span>
      </li>
    </ul>
  );
}

/** A resource amount that counts to its new value after a jump (useCountedValue). */
function CountedAmount({ target, perHour, max }: { target: number; perHour: number; max: string }) {
  const { language } = useI18n();
  const { value, direction } = useCountedValue(target, perHour);
  const className = direction === 'up' ? styles.gain : direction === 'down' ? styles.spend : undefined;
  return <Amount value={formatNumber(value, language)} max={max} className={className} />;
}

/** "812/1,000": the value in bold, the maximum lighter; it wraps below on narrow screens. */
function Amount({ value, max, className }: { value: string; max: string; className?: string }) {
  const t = useT();
  return (
    <span className={className ? `${styles.amountLine} ${className}` : styles.amountLine}>
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

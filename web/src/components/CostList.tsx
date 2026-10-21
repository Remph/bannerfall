import type { ReactNode } from 'react';
import { formatDuration, formatNumber } from '../game/format';
import { resourceTypes, type ResourceAmounts } from '../game/resources';
import { useI18n } from '../i18n';
import styles from './CostList.module.css';
import { Icon } from './Icon';

interface CostListProps {
  /** Names the list for screen readers ("per unit", "Total for 12"); shown only with `showLabel`. */
  label: string;
  showLabel?: boolean;
  cost: ResourceAmounts;
  /** Population and time are left out when missing (the market has neither). */
  pop?: number;
  seconds?: number;
  /** Screen-reader name of the time ("Build time", "Time"). */
  timeLabel?: string;
  /** With stock and free population, values the village can't pay right now are shown in red. */
  stock?: ResourceAmounts;
  freePop?: number;
  /** Leave out the resources that are 0 (a transport usually carries one or two). */
  hideZero?: boolean;
  /** Extra class, e.g. for the grid of the screen the list sits in. */
  className?: string;
  /** Shown after the costs, in the same row (the noble's price factor, 1.6 UI review). */
  extra?: ReactNode;
}

/**
 * The cost rows (resource icons, population, time; red when the village is short) of the Town Hall,
 * the recruit screen and the market (M5b review, moved here in M13b).
 */
export function CostList(props: CostListProps) {
  const {
    label,
    showLabel = false,
    cost,
    pop,
    seconds,
    timeLabel = '',
    stock,
    freePop,
    hideZero = false,
    className,
    extra,
  } = props;
  const { t, language } = useI18n();
  return (
    <ul className={className === undefined ? styles.costs : `${styles.costs} ${className}`} aria-label={label}>
      {showLabel && (
        <li className={styles.label} aria-hidden="true">
          {label}
        </li>
      )}
      {resourceTypes
        .filter((type) => !hideZero || cost[type] > 0)
        .map((type) => (
          <Cost
            key={type}
            icon={type}
            label={t.resources[type]}
            value={formatNumber(cost[type], language)}
            short={stock !== undefined && Math.floor(stock[type]) < cost[type]}
          />
        ))}
      {pop !== undefined && (
        <Cost
          icon="population"
          label={t.resources.population}
          value={formatNumber(pop, language)}
          short={freePop !== undefined && pop > freePop}
        />
      )}
      {seconds !== undefined && <Cost icon="clock" label={timeLabel} value={formatDuration(seconds)} short={false} />}
      {extra !== undefined && <li className={styles.cost}>{extra}</li>}
    </ul>
  );
}

interface CostProps {
  icon: 'wood' | 'clay' | 'iron' | 'population' | 'clock';
  label: string;
  value: string;
  /** The village can't pay it right now: shown in red. */
  short: boolean;
}

function Cost({ icon, label, value, short }: CostProps) {
  const { t } = useI18n();
  return (
    <li className={short ? `${styles.cost} ${styles.short}` : styles.cost}>
      <Icon name={icon} className={styles.icon} />
      <span className="sr-only">{label}: </span>
      {value}
      {short && <span className="sr-only"> ({t.townHall.notEnough})</span>}
    </li>
  );
}

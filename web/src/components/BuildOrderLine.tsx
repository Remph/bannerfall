import type { ReactNode } from 'react';
import type { BuildOrder } from '../api/queries/buildings';
import { hasStarted, progress } from '../game/buildQueue';
import { formatWhen } from '../game/format';
import { useServerNow } from '../game/serverTime';
import { useI18n } from '../i18n';
import styles from './BuildOrderLine.module.css';
import { Countdown } from './Countdown';
import { Icon } from './Icon';

/** The list around BuildOrderLines, in running order (an <ol>: the order matters). */
export function BuildOrderList({ children }: { children: ReactNode }) {
  return <ol className={styles.list}>{children}</ol>;
}

/**
 * One order of a build queue: building, target level and when it is ready; the running order also
 * gets a countdown and a progress bar. `children` are buttons on the right (the Town Hall's controls).
 */
export function BuildOrderLine({ order, children }: { order: BuildOrder; children?: ReactNode }) {
  const { t, language } = useI18n();
  const now = useServerNow();
  const started = hasStarted(order, now);
  return (
    <li className={styles.line}>
      <span className={styles.icon}>
        <Icon name={order.building} />
      </span>
      <span className={styles.text}>
        <span className={styles.name}>
          {t.buildings[order.building]} <span className={styles.level}>{t.village.level(order.targetLevel)}</span>
        </span>
        <span className={styles.time}>
          {started && (
            <>
              <Countdown until={order.endAt} className={styles.countdown} />
              {' · '}
            </>
          )}
          {t.buildQueue.readyAt(formatWhen(Date.parse(order.endAt), now, language))}
        </span>
        {started && (
          <span className={styles.bar} aria-hidden="true">
            <span className={styles.fill} style={{ transform: `scaleX(${progress(order, now)})` }} />
          </span>
        )}
      </span>
      {children !== undefined && <span className={styles.actions}>{children}</span>}
    </li>
  );
}

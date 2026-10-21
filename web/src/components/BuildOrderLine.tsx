import type { ReactNode } from 'react';
import type { BuildOrder } from '../api/queries/buildings';
import { hasStarted, progress } from '../game/buildQueue';
import { formatWhen } from '../game/format';
import { useGameNow } from '../game/serverTime';
import { useI18n, useT } from '../i18n';
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
 * `finished` and `entering` come from useFinishedOrders: a done order folds away, the next slides in.
 */
export function BuildOrderLine({
  order,
  children,
  finished = false,
  entering = false,
}: {
  order: BuildOrder;
  children?: ReactNode;
  finished?: boolean;
  entering?: boolean;
}) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const started = hasStarted(order, now);
  if (finished) {
    return (
      <FinishedOrderLine>
        {t.buildings[order.building]} <span className={styles.level}>{t.village.level(order.targetLevel)}</span>
      </FinishedOrderLine>
    );
  }
  return (
    <li className={entering ? `${styles.line} ${styles.entering}` : styles.line}>
      <span className={styles.icon}>
        <Icon name={order.building} />
      </span>
      <span className={styles.text}>
        <span className={styles.name}>
          {t.buildings[order.building]} <span className={styles.level}>{t.village.level(order.targetLevel)}</span>
          {order.demolish && <span className={styles.demolish}>{t.buildQueue.demolition}</span>}
          {order.automated && <AutomatedMark />}
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

/** After the level of an order the village automation placed (1.6): a small cogwheel. */
export function AutomatedMark() {
  const t = useT();
  return (
    <>
      {' '}
      <Icon name="settings" className={styles.automated} />
      <span className="sr-only">({t.automation.automatedOrder})</span>
    </>
  );
}

/**
 * An order that has just finished: green with a check and "Done", then its line folds away
 * (UI review 1.3, motion). Hidden from screen readers, which hear the queue as it is now.
 */
export function FinishedOrderLine({ children }: { children: ReactNode }) {
  const t = useT();
  return (
    <li className={`${styles.line} ${styles.finished}`} aria-hidden="true">
      <span className={`${styles.icon} ${styles.doneIcon}`}>
        <Icon name="check" />
      </span>
      <span className={styles.text}>
        <span className={styles.name}>{children}</span>
        <span className={`${styles.time} ${styles.doneText}`}>{t.buildQueue.finished}</span>
        <span className={styles.bar}>
          <span className={styles.fill} />
        </span>
      </span>
    </li>
  );
}

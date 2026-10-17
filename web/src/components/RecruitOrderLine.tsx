import type { ReactNode } from 'react';
import type { RecruitOrder } from '../api/queries/recruitment';
import { hasStarted, progress } from '../game/buildQueue';
import { formatNumber, formatWhen } from '../game/format';
import { nextUnitAtMs, unitsDone } from '../game/recruitment';
import { useGameNow } from '../game/serverTime';
import { useI18n } from '../i18n';
// Same look as a build order line, so both queues read alike.
import { FinishedOrderLine } from './BuildOrderLine';
import styles from './BuildOrderLine.module.css';
import { Countdown } from './Countdown';
import { Icon } from './Icon';

/** The list around RecruitOrderLines, in running order. */
export function RecruitOrderList({ children }: { children: ReactNode }) {
  return <ol className={styles.list}>{children}</ol>;
}

/**
 * One order of a recruitment queue: "12 × Spearman" and when the last one is ready. The running
 * order also gets a countdown, a progress bar, how many are done and when the next one is. The
 * numbers are computed from the clock (units finish one by one), so they move without a reload.
 * `children` are buttons on the right (the recruit screen's cancel button). `finished` and `entering`
 * come from useFinishedOrders, as on a build order line.
 */
export function RecruitOrderLine({
  order,
  children,
  finished = false,
  entering = false,
}: {
  order: RecruitOrder;
  children?: ReactNode;
  finished?: boolean;
  entering?: boolean;
}) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const started = hasStarted(order, now);
  const done = unitsDone(order, now);
  const nextAt = nextUnitAtMs(order, done);
  if (finished) {
    return (
      <FinishedOrderLine>
        {t.recruitQueue.order(formatNumber(order.count, language), t.units[order.unit])}
      </FinishedOrderLine>
    );
  }
  return (
    <li className={entering ? `${styles.line} ${styles.entering}` : styles.line}>
      <span className={styles.icon}>
        <Icon name={order.unit} />
      </span>
      <span className={styles.text}>
        <span className={styles.name}>
          {t.recruitQueue.order(formatNumber(order.count, language), t.units[order.unit])}
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
          <>
            <span className={styles.bar} aria-hidden="true">
              <span className={styles.fill} style={{ transform: `scaleX(${progress(order, now)})` }} />
            </span>
            <span className={styles.time}>
              {t.recruitQueue.done(formatNumber(done, language), formatNumber(order.count, language))}
              {nextAt !== undefined && (
                <>
                  {' · '}
                  {t.recruitQueue.nextIn} <Countdown until={new Date(nextAt).toISOString()} />
                </>
              )}
            </span>
          </>
        )}
      </span>
      {children !== undefined && <span className={styles.actions}>{children}</span>}
    </li>
  );
}

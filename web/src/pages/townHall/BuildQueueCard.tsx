import { useEffect, useRef, useState } from 'react';
import { ApiError, errorMessage } from '../../api/errors';
import { useCancelBuild, useMoveBuild, type BuildOrder } from '../../api/queries/buildings';
import type { Village } from '../../api/queries/villages';
import { BuildOrderLine, BuildOrderList } from '../../components/BuildOrderLine';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { Sheet } from '../../components/Sheet';
import { useFinishedOrders } from '../../components/useFinishedOrders';
import { hasStarted, isPaidSlot, moveOptions } from '../../game/buildQueue';
import { formatWhen } from '../../game/format';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import type { Dictionary } from '../../i18n/en';
import styles from './TownHallPage.module.css';

interface BuildQueueCardProps {
  village: Village;
  /** Orders per village at the normal price (world setting); undefined while it is loading. */
  queueSize: number | undefined;
  /** Orders per village with the paid slots (world setting); undefined while it is loading. */
  queueMax: number | undefined;
}

/**
 * The build queue with its controls. The running order keeps its countdown and bar; each waiting
 * one is a slim line with its time and × (1.3). "Change order" shows the full lines with the up and
 * down buttons (no dragging – 05-frontend §8) until "Done". Every order can be cancelled after a
 * confirmation. Orders in the paid slots behind `queueSize` (M13c) are marked and never move.
 */
export function BuildQueueCard({ village, queueSize, queueMax }: BuildQueueCardProps) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const [reordering, setReordering] = useState(false);
  const cancel = useCancelBuild(village.id);
  const move = useMoveBuild(village.id);
  const [confirming, setConfirming] = useState<BuildOrder | null>(null);
  // The error itself, not its text: the message is translated while rendering, so it follows a
  // language switch.
  const [error, setError] = useState<{ failure: Error; action: 'move' | 'cancel' } | null>(null);
  const queue = village.buildQueue;
  const shown = useFinishedOrders(village.id, queue);
  const busy = cancel.isPending || move.isPending;

  // The move button pressed last gets the focus back once the move is done: it was disabled while
  // the move ran and the line may have moved in the DOM, both drop the focus (keyboard players).
  // The new queue can render after the move has ended, so this waits for it (or for a failure).
  const refocus = useRef<{ orderId: number; direction: 'up' | 'down'; queue: BuildOrder[] } | null>(null);
  const moveFailed = move.isError;
  useEffect(() => {
    const wanted = refocus.current;
    if (wanted === null || busy || (queue === wanted.queue && !moveFailed)) {
      return;
    }
    refocus.current = null;
    // Some browsers keep the focus on the disabled button: that one still counts as not moved on.
    const active = document.activeElement;
    if (
      active !== null &&
      active !== document.body &&
      !(active instanceof HTMLElement && 'moveOrder' in active.dataset)
    ) {
      return; // the player went on elsewhere meanwhile
    }
    const button = (direction: 'up' | 'down') =>
      document.querySelector<HTMLButtonElement>(
        `[data-move-order="${wanted.orderId}"][data-direction="${direction}"]:not(:disabled)`,
      );
    // at the top or bottom now: the other direction
    (button(wanted.direction) ?? button(wanted.direction === 'up' ? 'down' : 'up'))?.focus();
  }, [busy, queue, moveFailed]);

  function moveOrder(order: BuildOrder, position: number, direction: 'up' | 'down') {
    refocus.current = { orderId: order.id, direction, queue };
    setError(null);
    move.mutate({ orderId: order.id, position }, { onError: (failure) => setError({ failure, action: 'move' }) });
  }

  function cancelOrder(order: BuildOrder) {
    setError(null);
    cancel.mutate(order.id, {
      onSuccess: () => setConfirming(null),
      onError: (failure) => {
        setConfirming(null);
        setError({ failure, action: 'cancel' });
      },
    });
  }

  const title = queueMax === undefined ? t.buildQueue.title : t.buildQueue.titleCount(queue.length, queueMax);
  const paidCount = queueSize === undefined ? 0 : Math.max(0, queue.length - queueSize);
  // only offered while some order can move at all; it ends by itself when none can any more
  const movable = queue.some((_, index) => {
    const options = moveOptions(queue, index, now, queueSize);
    return options.up || options.down;
  });
  const reorder = reordering && movable;
  return (
    <Card title={title}>
      {shown.length === 0 ? (
        <p className={styles.empty}>{t.buildQueue.empty}</p>
      ) : (
        <BuildOrderList>
          {shown.map(({ order, finished, entering }) => {
            if (finished) {
              return <BuildOrderLine key={order.id} order={order} finished />;
            }
            const index = queue.indexOf(order);
            const name = orderName(t, order);
            const options = moveOptions(queue, index, now, queueSize);
            const over = Date.parse(order.endAt) <= now; // done, the server applies it any moment
            const paid = queueSize !== undefined && isPaidSlot(index, queueSize);
            if (!reorder && !hasStarted(order, now)) {
              return (
                <li key={order.id} className={styles.waiting}>
                  <span className={styles.waitingIcon}>
                    <Icon name={order.building} />
                  </span>
                  <span className={styles.waitingName}>
                    {t.buildings[order.building]}{' '}
                    <span className={styles.waitingLevel}>{t.village.level(order.targetLevel)}</span>
                  </span>
                  {paid && <span className={styles.paidSlot}>{t.buildQueue.paidSlot}</span>}
                  <span className={styles.waitingTime}>
                    <span className="sr-only">{t.buildQueue.readyAt('')}</span>
                    {formatWhen(Date.parse(order.endAt), now, language)}
                  </span>
                  <IconButton
                    icon="close"
                    variant="danger"
                    className={styles.waitingCancel}
                    label={t.buildQueue.cancel(name)}
                    disabled={busy || over}
                    onClick={() => setConfirming(order)}
                  />
                </li>
              );
            }
            return (
              <BuildOrderLine key={order.id} order={order} entering={entering}>
                {paid && <span className={styles.paidSlot}>{t.buildQueue.paidSlot}</span>}
                {!paid && !hasStarted(order, now) && (
                  <>
                    <IconButton
                      icon="chevronUp"
                      label={t.buildQueue.moveUp(name)}
                      disabled={busy || !options.up}
                      data-move-order={order.id}
                      data-direction="up"
                      onClick={() => moveOrder(order, index - 1, 'up')}
                    />
                    <IconButton
                      icon="chevronDown"
                      label={t.buildQueue.moveDown(name)}
                      disabled={busy || !options.down}
                      data-move-order={order.id}
                      data-direction="down"
                      onClick={() => moveOrder(order, index + 1, 'down')}
                    />
                  </>
                )}
                <IconButton
                  icon="close"
                  variant="danger"
                  label={t.buildQueue.cancel(name)}
                  disabled={busy || over}
                  onClick={() => setConfirming(order)}
                />
              </BuildOrderLine>
            );
          })}
        </BuildOrderList>
      )}
      {movable && (
        <div className={styles.reorderRow}>
          <button type="button" className={styles.reorderButton} onClick={() => setReordering(!reorder)}>
            {reorder ? (
              t.buildQueue.reorderDone
            ) : (
              <>
                <span className={styles.reorderIcons}>
                  <Icon name="chevronUp" />
                  <Icon name="chevronDown" />
                </span>
                {t.buildQueue.reorder}
              </>
            )}
          </button>
        </div>
      )}
      {paidCount > 0 && queueSize !== undefined && (
        <p className={styles.queueNote}>{t.buildQueue.paidSlotsNote(queueSize)}</p>
      )}
      {error !== null && (
        <div className={styles.cardError}>
          <ErrorMessage>
            {error.action === 'move' ? moveErrorMessage(t, error.failure) : errorMessage(t, error.failure)}
          </ErrorMessage>
        </div>
      )}
      <Sheet open={confirming !== null} onClose={() => setConfirming(null)} title={t.buildQueue.cancelTitle}>
        {confirming !== null && (
          <div className={styles.confirm}>
            <p>
              <strong>{t.buildQueue.cancelText(orderName(t, confirming))}</strong> {t.buildQueue.cancelRefund}
            </p>
            <div className={styles.confirmActions}>
              <Button type="button" variant="secondary" onClick={() => setConfirming(null)}>
                {t.buildQueue.keep}
              </Button>
              <Button type="button" disabled={cancel.isPending} onClick={() => cancelOrder(confirming)}>
                {t.buildQueue.confirmCancel}
              </Button>
            </div>
          </div>
        )}
      </Sheet>
    </Card>
  );
}

/** "Woodcutter Lv 2" – for button labels and the confirmation. */
function orderName(t: Dictionary, order: BuildOrder): string {
  return `${t.buildings[order.building]} ${t.village.level(order.targetLevel)}`;
}

/** For a move, REQUIREMENTS_NOT_MET means "it would come before what it needs". */
function moveErrorMessage(t: Dictionary, error: Error): string {
  if (error instanceof ApiError && error.code === 'REQUIREMENTS_NOT_MET') {
    return t.buildQueue.moveBlocked;
  }
  return errorMessage(t, error);
}

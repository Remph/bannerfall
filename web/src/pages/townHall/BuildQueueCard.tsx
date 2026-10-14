import { useState } from 'react';
import { ApiError, errorMessage } from '../../api/errors';
import { useCancelBuild, useMoveBuild, type BuildOrder } from '../../api/queries/buildings';
import type { Village } from '../../api/queries/villages';
import { BuildOrderLine, BuildOrderList } from '../../components/BuildOrderLine';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { IconButton } from '../../components/IconButton';
import { Sheet } from '../../components/Sheet';
import { hasStarted, isPaidSlot, moveOptions } from '../../game/buildQueue';
import { useServerNow } from '../../game/serverTime';
import { useT } from '../../i18n';
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
 * The build queue with its controls: orders that haven't started move up and down (buttons, no
 * dragging – 05-frontend §8), every order can be cancelled after a confirmation. Orders in the paid
 * slots behind `queueSize` (M13c) are marked and have no move buttons.
 */
export function BuildQueueCard({ village, queueSize, queueMax }: BuildQueueCardProps) {
  const t = useT();
  const now = useServerNow();
  const cancel = useCancelBuild(village.id);
  const move = useMoveBuild(village.id);
  const [confirming, setConfirming] = useState<BuildOrder | null>(null);
  // The error itself, not its text: the message is translated while rendering, so it follows a
  // language switch.
  const [error, setError] = useState<{ failure: Error; action: 'move' | 'cancel' } | null>(null);
  const queue = village.buildQueue;
  const busy = cancel.isPending || move.isPending;

  function moveOrder(order: BuildOrder, position: number) {
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
  return (
    <Card title={title}>
      {queue.length === 0 ? (
        <p className={styles.empty}>{t.buildQueue.empty}</p>
      ) : (
        <BuildOrderList>
          {queue.map((order, index) => {
            const name = orderName(t, order);
            const options = moveOptions(queue, index, now, queueSize);
            const over = Date.parse(order.endAt) <= now; // done, the server applies it any moment
            const paid = queueSize !== undefined && isPaidSlot(index, queueSize);
            return (
              <BuildOrderLine key={order.id} order={order}>
                {paid && <span className={styles.paidSlot}>{t.buildQueue.paidSlot}</span>}
                {!paid && !hasStarted(order, now) && (
                  <>
                    <IconButton
                      icon="chevronUp"
                      label={t.buildQueue.moveUp(name)}
                      disabled={busy || !options.up}
                      onClick={() => moveOrder(order, index - 1)}
                    />
                    <IconButton
                      icon="chevronDown"
                      label={t.buildQueue.moveDown(name)}
                      disabled={busy || !options.down}
                      onClick={() => moveOrder(order, index + 1)}
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

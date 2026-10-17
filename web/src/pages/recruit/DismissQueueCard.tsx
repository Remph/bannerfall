import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useCancelDismiss, type DismissOrder, type RecruitBuilding } from '../../api/queries/recruitment';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { IconButton } from '../../components/IconButton';
import { RecruitOrderLine, RecruitOrderList } from '../../components/RecruitOrderLine';
import { Sheet } from '../../components/Sheet';
import { formatNumber } from '../../game/format';
import { unitsDone } from '../../game/recruitment';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import styles from './RecruitPage.module.css';

interface DismissQueueCardProps {
  villageId: number;
  info: RecruitBuilding;
  /** Orders per building (world setting, the dismiss queue has the same size); undefined while loading. */
  queueSize: number | undefined;
  /** An attack is on its way: the server refuses a cancel then (units in an order can't be hit). */
  locked: boolean;
}

/**
 * The dismiss queue of one recruiting building (02-game-rules §5.2a), only shown while it has
 * orders. Looks like the recruit queue; cancelling asks first and puts the units not yet dismissed
 * back into the army.
 */
export function DismissQueueCard({ villageId, info, queueSize, locked }: DismissQueueCardProps) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const cancel = useCancelDismiss(villageId);
  const [confirming, setConfirming] = useState<DismissOrder | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const queue = info.dismissQueue;

  if (queue.length === 0) {
    return null;
  }

  function cancelOrder(order: DismissOrder) {
    setError(null);
    cancel.mutate(order.id, {
      onSuccess: () => setConfirming(null),
      onError: (failure) => {
        setConfirming(null);
        setError(failure);
      },
    });
  }

  const name = (order: DismissOrder, count: number) =>
    t.dismiss.order(formatNumber(count, language), t.units[order.unit]);
  const remaining = (order: DismissOrder) => order.count - unitsDone(order, now);
  const title = queueSize === undefined ? t.dismiss.queueTitle : t.dismiss.queueTitleCount(queue.length, queueSize);
  return (
    <Card title={title}>
      <RecruitOrderList>
        {queue.map((order) => (
          <RecruitOrderLine key={order.id} order={order}>
            <IconButton
              icon="close"
              variant="danger"
              label={t.dismiss.cancel(name(order, order.count))}
              // an order whose last unit is gone no longer exists on the server
              disabled={locked || cancel.isPending || Date.parse(order.endAt) <= now}
              title={locked ? t.dismiss.locked : undefined}
              onClick={() => setConfirming(order)}
            />
          </RecruitOrderLine>
        ))}
      </RecruitOrderList>
      {error !== null && (
        <div className={styles.cardError}>
          <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
        </div>
      )}
      <Sheet
        open={confirming !== null && Date.parse(confirming.endAt) > now}
        onClose={() => setConfirming(null)}
        title={t.dismiss.cancelTitle}
      >
        {confirming !== null && (
          <div className={styles.confirm}>
            <p>
              <strong>{t.dismiss.cancelText(name(confirming, remaining(confirming)))}</strong> {t.dismiss.cancelNote}
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

import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useCancelRecruit, type RecruitBuilding, type RecruitOrder } from '../../api/queries/recruitment';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { IconButton } from '../../components/IconButton';
import { RecruitOrderLine, RecruitOrderList } from '../../components/RecruitOrderLine';
import { Sheet } from '../../components/Sheet';
import { formatNumber } from '../../game/format';
import { unitsDone } from '../../game/recruitment';
import { useServerNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import styles from './RecruitPage.module.css';

interface RecruitQueueCardProps {
  villageId: number;
  info: RecruitBuilding;
  /** Orders per building (world setting); undefined while it is loading. */
  queueSize: number | undefined;
}

/**
 * The queue of one recruiting building. Orders run one after the other and can't be moved; each can
 * be cancelled after a confirmation (the units already done stay).
 */
export function RecruitQueueCard({ villageId, info, queueSize }: RecruitQueueCardProps) {
  const { t, language } = useI18n();
  const now = useServerNow();
  const cancel = useCancelRecruit(villageId);
  const [confirming, setConfirming] = useState<RecruitOrder | null>(null);
  // The error itself, not its text: the message is translated while rendering (language switch).
  const [error, setError] = useState<Error | null>(null);
  const queue = info.queue;

  function cancelOrder(order: RecruitOrder) {
    setError(null);
    cancel.mutate(order.id, {
      onSuccess: () => setConfirming(null),
      onError: (failure) => {
        setConfirming(null);
        setError(failure);
      },
    });
  }

  const name = (order: RecruitOrder, count: number) =>
    t.recruitQueue.order(formatNumber(count, language), t.units[order.unit]);
  // the units still in training: what a cancel takes back
  const remaining = (order: RecruitOrder) => order.count - unitsDone(order, now);
  const title = queueSize === undefined ? t.recruitQueue.title : t.recruitQueue.titleCount(queue.length, queueSize);
  return (
    <Card title={title}>
      {queue.length === 0 ? (
        <p className={styles.empty}>{t.recruitQueue.empty}</p>
      ) : (
        <RecruitOrderList>
          {queue.map((order) => (
            <RecruitOrderLine key={order.id} order={order}>
              <IconButton
                icon="close"
                variant="danger"
                label={t.recruitQueue.cancel(name(order, order.count))}
                // an order whose last unit is done is gone on the server
                disabled={cancel.isPending || Date.parse(order.endAt) <= now}
                onClick={() => setConfirming(order)}
              />
            </RecruitOrderLine>
          ))}
        </RecruitOrderList>
      )}
      {error !== null && (
        <div className={styles.cardError}>
          <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
        </div>
      )}
      {/* Closes by itself when the order's last unit is done meanwhile: there is nothing left to cancel
          (the server would answer RECRUIT_ORDER_NOT_FOUND). Closing calls onClose, which clears `confirming`. */}
      <Sheet
        open={confirming !== null && Date.parse(confirming.endAt) > now}
        onClose={() => setConfirming(null)}
        title={t.buildQueue.cancelTitle}
      >
        {confirming !== null && (
          <div className={styles.confirm}>
            <p>
              <strong>{t.recruitQueue.cancelText(name(confirming, remaining(confirming)))}</strong>{' '}
              {t.recruitQueue.cancelRefund}
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

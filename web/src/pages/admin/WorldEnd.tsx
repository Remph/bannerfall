import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useEndWorldNow, useReopenWorld } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './AdminPage.module.css';

/**
 * "End now" with its confirmation (v1.3, 02-game-rules §22): it works at once, not through the World
 * tab's save bar, which holds the end date.
 */
export function EndNow() {
  const { t } = useI18n();
  const labels = t.admin.worldEnd;
  const endNow = useEndWorldNow();
  const [confirming, setConfirming] = useState(false);
  return (
    <>
      {endNow.error !== null && <ErrorMessage>{errorMessage(t, endNow.error)}</ErrorMessage>}
      {!confirming ? (
        <div className={styles.actions}>
          <Button type="button" variant="danger" onClick={() => setConfirming(true)}>
            {labels.endNow}
          </Button>
        </div>
      ) : (
        <div className={styles.stack} role="alertdialog" aria-label={labels.endNow}>
          <p>{labels.confirmNow}</p>
          <div className={styles.actions}>
            <Button type="button" variant="danger" disabled={endNow.isPending} onClick={() => endNow.mutate()}>
              {labels.confirmYes}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setConfirming(false)}>
              {t.common.cancel}
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

/** An ended world: when it ended, and taking the end back. */
export function EndedForm({ endedAt }: { endedAt: string | null }) {
  const { t, language } = useI18n();
  const labels = t.admin.worldEnd;
  const reopen = useReopenWorld();
  const [confirming, setConfirming] = useState(false);
  return (
    <>
      <p>{labels.ended(endedAt === null ? '' : formatDateTime(Date.parse(endedAt), language))}</p>
      {reopen.error !== null && <ErrorMessage>{errorMessage(t, reopen.error)}</ErrorMessage>}
      {!confirming ? (
        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={() => setConfirming(true)}>
            {labels.reopen}
          </Button>
        </div>
      ) : (
        <div className={styles.stack} role="alertdialog" aria-label={labels.reopen}>
          <p>{labels.confirmReopen}</p>
          <div className={styles.actions}>
            <Button type="button" variant="primary" disabled={reopen.isPending} onClick={() => reopen.mutate()}>
              {labels.confirmReopenYes}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setConfirming(false)}>
              {t.common.cancel}
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

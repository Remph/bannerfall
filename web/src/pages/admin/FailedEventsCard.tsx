import { errorMessage } from '../../api/errors';
import { useFailedEvents, useRetryEvent, type FailedEvent } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './AdminPage.module.css';

/** Events the worker gave up on (04-api *Admin*): why they failed, and a button to try again. */
export function FailedEventsCard() {
  const { t, language } = useI18n();
  const events = useFailedEvents();
  const retry = useRetryEvent();

  return (
    <Card title={t.admin.events.title}>
      <div className={styles.stack}>
        <p className={styles.hint}>{t.admin.events.hint}</p>
        {events.isError && <ErrorMessage>{errorMessage(t, events.error)}</ErrorMessage>}
        {retry.isError && <ErrorMessage>{errorMessage(t, retry.error)}</ErrorMessage>}
        {events.isPending && <p>{t.common.loading}</p>}
        {events.data?.length === 0 && <p className={styles.hint}>{t.admin.events.empty}</p>}
        <ul className={styles.lords}>
          {events.data?.map((event: FailedEvent) => (
            <li key={event.id} className={styles.lord}>
              <div className={styles.lordHead}>
                <strong>{event.kind}</strong>
                <Button
                  type="button"
                  variant="secondary"
                  size="small"
                  disabled={retry.isPending}
                  onClick={() => retry.mutate(event.id)}
                >
                  {t.admin.events.retry}
                </Button>
              </div>
              <p className={styles.meta}>
                {t.admin.events.about(event.refId ?? null)} · {t.admin.events.attempts(event.attempts)}
                {event.processedAt != null &&
                  ` · ${t.admin.events.failedAt(formatDateTime(Date.parse(event.processedAt), language))}`}
              </p>
              {event.lastError != null && <p className={styles.error}>{event.lastError}</p>}
            </li>
          ))}
        </ul>
      </div>
    </Card>
  );
}

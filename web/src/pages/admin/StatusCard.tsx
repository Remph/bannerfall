import { errorMessage } from '../../api/errors';
import { useAdminStatus } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime, formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './AdminPage.module.css';
import { shortDuration } from './adminTabs';

/** GET /api/admin/status: what the CLI's `status` prints. */
export function StatusCard() {
  const { t, language } = useI18n();
  const labels = t.admin.status;
  const status = useAdminStatus();
  const s = status.data;

  return (
    <Card title={labels.title}>
      <div className={styles.stack}>
        {status.isError && <ErrorMessage>{errorMessage(t, status.error)}</ErrorMessage>}
        {status.isPending && <p>{t.common.loading}</p>}
        {s !== undefined && (
          <>
            <p className={styles.meta}>
              {labels.version(s.version)} · {labels.uptime(shortDuration(s.uptimeSeconds))}
            </p>
            <p className={s.cheatsEnabled ? styles.cheatsOn : styles.meta}>
              {s.cheatsEnabled ? labels.cheatsOn : labels.cheatsOff}
            </p>
            <dl className={styles.facts}>
              <dt>{labels.database}</dt>
              <dd className={s.databaseOk ? undefined : styles.bad}>
                {s.databaseOk ? labels.databaseOk : labels.databaseDown}
              </dd>
              <dt>{labels.humans}</dt>
              <dd>{formatNumber(s.humanPlayers, language)}</dd>
              <dt>{labels.villages}</dt>
              <dd>{formatNumber(s.villages, language)}</dd>
              <dt>{labels.barbarians}</dt>
              <dd>{formatNumber(s.barbarianVillages, language)}</dd>
              <dt>{labels.lords}</dt>
              <dd>{formatNumber(s.npcLords, language)}</dd>
              <dt>{labels.events}</dt>
              <dd>
                {labels.pending(s.events.pending)}
                {s.events.nextDueAt != null &&
                  ` · ${labels.nextDue(formatDateTime(Date.parse(s.events.nextDueAt), language))}`}
                {' · '}
                {s.events.lagSeconds > 0 ? labels.lag(shortDuration(s.events.lagSeconds)) : labels.noLag}
                {s.events.failed > 0 && (
                  <>
                    {' · '}
                    <span className={styles.bad}>{labels.failed(s.events.failed)}</span>
                  </>
                )}
              </dd>
            </dl>
          </>
        )}
        <div>
          <Button
            type="button"
            variant="secondary"
            size="small"
            disabled={status.isFetching}
            onClick={() => void status.refetch()}
          >
            {labels.refresh}
          </Button>
        </div>
      </div>
    </Card>
  );
}

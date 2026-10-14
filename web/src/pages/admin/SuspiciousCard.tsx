import { errorMessage } from '../../api/errors';
import { useInvariantCheck, useSuspicious } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './AdminPage.module.css';

/** GET /api/admin/suspicious (M14e) and the invariant check: who sends requests the game never sends. */
export function SuspiciousCard() {
  const { t, language } = useI18n();
  const labels = t.admin.suspicious;
  const suspicious = useSuspicious();
  const check = useInvariantCheck();

  return (
    <>
      <Card title={labels.title}>
        <div className={styles.stack}>
          <p className={styles.hint}>{labels.hint}</p>
          {suspicious.isError && <ErrorMessage>{errorMessage(t, suspicious.error)}</ErrorMessage>}
          {suspicious.isPending && <p>{t.common.loading}</p>}
          {suspicious.data?.length === 0 && <p className={styles.hint}>{labels.empty}</p>}
          <ul className={styles.lords}>
            {suspicious.data?.map((account) => (
              <li key={account.playerId} className={styles.lord}>
                <div className={styles.lordHead}>
                  <strong>
                    {account.name}
                    {account.bannedUntil != null && <span className={styles.tag}>{labels.banned}</span>}
                  </strong>
                  <span className={styles.meta}>{formatDateTime(Date.parse(account.lastAt), language)}</span>
                </div>
                <p className={styles.meta}>
                  {labels.total(account.total)} ·{' '}
                  {Object.entries(account.counts)
                    .map(([kind, count]) => `${labels.kinds[kind as keyof typeof labels.kinds] ?? kind} ${count}`)
                    .join(', ')}
                </p>
                <details>
                  <summary>{labels.recent}</summary>
                  <ul className={styles.details}>
                    {account.recent.map((event) => (
                      <li key={event.id}>
                        {formatDateTime(Date.parse(event.at), language)} · {event.endpoint} · {event.detail}
                      </li>
                    ))}
                  </ul>
                </details>
              </li>
            ))}
          </ul>
        </div>
      </Card>
      <Card title={labels.checkTitle}>
        <div className={styles.stack}>
          <p className={styles.hint}>{labels.checkHint}</p>
          {check.isError && <ErrorMessage>{errorMessage(t, check.error)}</ErrorMessage>}
          {check.data?.problems.length === 0 && <p className={styles.saved}>{labels.checkClean}</p>}
          <ul className={styles.lords}>
            {check.data?.problems.map((problem) => (
              <li key={problem.kind} className={styles.lord}>
                <strong className={styles.bad}>{problem.kind}</strong>
                <p className={styles.meta}>
                  {labels.problemCount(problem.count)} · {problem.examples.join(', ')}
                </p>
              </li>
            ))}
          </ul>
          <div>
            <Button
              type="button"
              variant="secondary"
              size="small"
              disabled={check.isPending}
              onClick={() => check.mutate()}
            >
              {labels.runCheck}
            </Button>
          </div>
        </div>
      </Card>
    </>
  );
}

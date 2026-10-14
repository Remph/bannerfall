import { errorMessage } from '../../api/errors';
import { useAudit, type AdminAction } from '../../api/queries/admin';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './AdminPage.module.css';
import { auditDetails } from './auditDetails';

/** GET /api/admin/audit: who did what (08-host-console §6). The details are shown as stored. */
export function AuditCard() {
  const { t, language } = useI18n();
  const labels = t.admin.audit;
  const audit = useAudit();

  return (
    <Card title={labels.title}>
      <div className={styles.stack}>
        <p className={styles.hint}>{labels.hint}</p>
        {audit.isError && <ErrorMessage>{errorMessage(t, audit.error)}</ErrorMessage>}
        {audit.isPending && <p>{t.common.loading}</p>}
        {audit.data?.length === 0 && <p className={styles.hint}>{labels.empty}</p>}
        <ul className={styles.lords}>
          {audit.data?.map((action: AdminAction) => (
            <li key={action.id} className={styles.lord}>
              <div className={styles.lordHead}>
                <strong>{action.action}</strong>
                <span className={styles.meta}>{formatDateTime(Date.parse(action.at), language)}</span>
              </div>
              <p className={styles.meta}>
                {action.actor}
                {action.targetType != null && ` · ${labels.target(action.targetType, action.targetId ?? null)}`}
              </p>
              {Object.keys(action.details).length > 0 && (
                <p className={styles.details}>{auditDetails(action.details)}</p>
              )}
            </li>
          ))}
        </ul>
      </div>
    </Card>
  );
}

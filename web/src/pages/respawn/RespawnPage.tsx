import { Link, useNavigate } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useRespawn } from '../../api/queries/auth';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { useT } from '../../i18n';
import { rememberVillage } from '../../layout/currentVillage';
import styles from './RespawnPage.module.css';

/**
 * The player lost his last village (`needsRespawn`, 02-game-rules §10): what happened, the way to
 * the reports, and a new start with a fresh village. "/" shows it while there is no village.
 */
export function RespawnPage() {
  const t = useT();
  const navigate = useNavigate();
  const respawn = useRespawn();
  return (
    <div className={styles.page}>
      <Card title={t.respawn.title}>
        <div className={styles.body}>
          <p>{t.respawn.text}</p>
          <p className={styles.muted}>
            {t.respawn.reportsHint} <Link to="/reports">{t.respawn.toReports}</Link>
          </p>
          {respawn.isError && <ErrorMessage>{errorMessage(t, respawn.error)}</ErrorMessage>}
          <Button
            type="button"
            disabled={respawn.isPending}
            onClick={() =>
              respawn.mutate(undefined, {
                onSuccess: (village) => {
                  rememberVillage(village.id);
                  void navigate(`/v/${village.id}`, { replace: true });
                },
              })
            }
          >
            {respawn.isPending ? t.respawn.starting : t.respawn.start}
          </Button>
        </div>
      </Card>
    </div>
  );
}

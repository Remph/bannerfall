import { Link } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { useT } from '../../i18n';
import { ActionsStayOn } from '../../layout/ActionsStayOn';
import styles from './VillagePage.module.css';

/**
 * A village screen that couldn't load its village: a foreign or unknown village gets a link to the
 * player's first one, anything else a retry button.
 */
export function VillageError({ error, onRetry }: { error: Error; onRetry: () => void }) {
  const t = useT();
  const me = useCurrentAccount();
  const notFound = error instanceof ApiError && error.code === 'VILLAGE_NOT_FOUND';
  const first = me.villages[0];
  return (
    <div className={styles.status}>
      <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
      {notFound ? (
        first !== undefined && (
          <Link className={styles.link} to={`/v/${first.id}`}>
            {t.village.toFirstVillage}
          </Link>
        )
      ) : (
        // reading again changes nothing, so it stays on in an ended world (v1.3)
        <ActionsStayOn>
          <Button type="button" onClick={onRetry}>
            {t.common.retry}
          </Button>
        </ActionsStayOn>
      )}
    </div>
  );
}

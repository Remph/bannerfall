import { usePublicWorld } from '../../api/queries/world';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import { OpeningCountdown } from './OpeningCountdown';
import styles from './Pregame.module.css';

/**
 * Under the login and register forms while the world has not opened (M14g): when it opens, and
 * that an account can be created already. Nothing once the world is open.
 */
export function PregameNotice() {
  const { t, language } = useI18n();
  const world = usePublicWorld();
  if (world.data?.phase !== 'PREGAME') {
    return null;
  }
  const { name, opensAt } = world.data;
  return (
    <aside className={styles.notice} aria-label={t.pregame.title(name)}>
      {opensAt === null || opensAt === undefined ? (
        <p>{t.pregame.notice.noTime(name)}</p>
      ) : (
        <>
          <p>{t.pregame.notice.opensAt(name, formatDateTime(Date.parse(opensAt), language))}</p>
          <p>
            {t.pregame.countdown}: <OpeningCountdown opensAt={opensAt} />
          </p>
        </>
      )}
      <p>{t.pregame.notice.register}</p>
    </aside>
  );
}

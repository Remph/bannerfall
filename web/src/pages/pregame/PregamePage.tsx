import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useWorld } from '../../api/queries/world';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import { OpeningCountdown } from './OpeningCountdown';
import styles from './Pregame.module.css';
import { WorldInfo } from './WorldInfo';

/**
 * What a player sees instead of a village while the world has not opened (M14g): when it opens, a
 * countdown, how many have registered and the world settings. The chat is in the frame around it.
 */
export function PregamePage() {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const world = useWorld();
  if (world.isError) {
    return <ErrorMessage>{errorMessage(t, world.error)}</ErrorMessage>;
  }
  if (world.data === undefined) {
    return <p>{t.common.loading}</p>;
  }
  const { name, opensAt, registeredPlayers, guildsEnabled } = world.data;
  return (
    <div className={styles.page}>
      <section className={styles.hero} aria-labelledby="pregame-title">
        <h1 id="pregame-title" className={styles.title}>
          {t.pregame.title(name)}
        </h1>
        {opensAt === null || opensAt === undefined ? (
          <p className={styles.opens}>{t.pregame.noTime}</p>
        ) : (
          <>
            <p className={styles.opens}>{t.pregame.opensAt(formatDateTime(Date.parse(opensAt), language))}</p>
            <p className={styles.countdown}>
              <span className={styles.countdownLabel}>{t.pregame.countdown}</span>
              <OpeningCountdown opensAt={opensAt} />
            </p>
          </>
        )}
        <p className={styles.text}>{t.pregame.registered(registeredPlayers)}</p>
        <p className={styles.text}>{t.pregame.explainer}</p>
      </section>
      {me.role === 'ADMIN' && (
        <p className={styles.adminHint}>
          {t.pregame.adminHint} <Link to="/admin?tab=world">{t.pregame.toAdmin}</Link>
        </p>
      )}
      {guildsEnabled && (
        <p className={styles.adminHint}>
          {t.pregame.guildsHint} <Link to="/guild">{t.pregame.toGuilds}</Link>
        </p>
      )}
      <Card title={t.worldInfo.title}>
        <WorldInfo />
      </Card>
    </div>
  );
}

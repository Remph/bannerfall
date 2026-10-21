import { Link, Navigate } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useAdminWorldSettings } from '../../api/queries/admin';
import { useCurrentAccount } from '../../api/queries/auth';
import { useSetupDone } from '../../api/queries/setup';
import { useWorld } from '../../api/queries/world';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import { pendingSetupCode } from '../setup/setupCode';
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
  if (pendingSetupCode() !== null) {
    // back from the login or registration the setup link sent the host to (1.6)
    return <Navigate to="/setup" replace />;
  }
  if (world.isError) {
    return <ErrorMessage>{errorMessage(t, world.error)}</ErrorMessage>;
  }
  if (world.data === undefined) {
    return <p>{t.common.loading}</p>;
  }
  const { name, opensAt, registeredPlayers, guildsEnabled, guildStartTogether } = world.data;
  return (
    <div className={styles.page}>
      {me.role === 'ADMIN' && <SetupOffer />}
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
      {me.role === 'ADMIN' && <AdminHint />}
      {guildsEnabled && (
        <p className={styles.adminHint}>
          {guildStartTogether ? t.pregame.guildsTogetherHint : t.pregame.guildsHint}{' '}
          <Link to="/guild">{t.pregame.toGuilds}</Link>
        </p>
      )}
      <Card title={t.worldInfo.title}>
        <WorldInfo />
      </Card>
    </div>
  );
}

/** The guided world setup (1.6), offered to the host until he finishes or skips it. */
function SetupOffer() {
  const { t } = useI18n();
  const settings = useAdminWorldSettings();
  const done = useSetupDone();
  if (settings.data === undefined || settings.data.setupDone) {
    return null;
  }
  return (
    <section className={styles.setupCard} aria-labelledby="setup-entry-title">
      <h2 id="setup-entry-title" className={styles.setupTitle}>
        {t.setup.entry.title}
      </h2>
      <p className={styles.text}>{t.setup.entry.text}</p>
      <div className={styles.setupActions}>
        <Link className={styles.setupStart} to="/setup">
          {t.setup.entry.start}
        </Link>
        <button type="button" className={styles.setupSkip} disabled={done.isPending} onClick={() => done.mutate(false)}>
          {t.setup.entry.skip}
        </button>
      </div>
      {done.isError && <ErrorMessage>{errorMessage(t, done.error)}</ErrorMessage>}
    </section>
  );
}

/** After the setup: where the host changes the settings now. */
function AdminHint() {
  const { t } = useI18n();
  const settings = useAdminWorldSettings();
  if (settings.data?.setupDone !== true) {
    return null;
  }
  return (
    <p className={styles.adminHint}>
      {t.pregame.adminHint} <Link to="/admin?tab=world">{t.pregame.toAdmin}</Link>
    </p>
  );
}

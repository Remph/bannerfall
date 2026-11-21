import { useCurrentAccount } from '../api/queries/auth';
import { useSetWorldClock } from '../api/queries/admin';
import { Button } from '../components/Button';
import { Icon } from '../components/Icon';
import { useI18n } from '../i18n';
import { ActionsStayOn } from './ActionsStayOn';
import { useWorldPaused } from './worldEnd';
import styles from './VictoryStrips.module.css';

/**
 * The strip under the top bar while the host has paused a solo world (2.1, 12-solo-and-allies §3): the
 * world stands still (resources, countdowns, marches) and the game's buttons are off until he presses
 * play. The host gets the play button right here; everybody else reads who can start it again.
 */
export function WorldPausedBanner() {
  const { t } = useI18n();
  const paused = useWorldPaused();
  const me = useCurrentAccount();
  const play = useSetWorldClock();
  if (!paused) {
    return null;
  }
  const host = me.role === 'ADMIN';
  return (
    <aside className={styles.paused} aria-label={t.pausedBanner.label}>
      <Icon name="clock" className={styles.icon} />
      <div className={styles.endedText}>
        <strong className={styles.endedTitle}>{t.pausedBanner.title}</strong>
        <span className={styles.pausedText}>{host ? t.pausedBanner.hostText : t.pausedBanner.text}</span>
      </div>
      {host && (
        <ActionsStayOn>
          <Button disabled={play.isPending} onClick={() => play.mutate({ paused: false })}>
            {t.pausedBanner.play}
          </Button>
        </ActionsStayOn>
      )}
    </aside>
  );
}

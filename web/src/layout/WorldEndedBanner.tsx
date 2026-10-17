import { Link } from 'react-router';
import { useVictory, useWorld, type WorldResult } from '../api/queries/world';
import { Icon } from '../components/Icon';
import { useI18n } from '../i18n';
import { rankingPath } from '../pages/ranking/rankingPaths';
import { endHeadline, useMySide } from '../pages/ranking/victoryState';
import styles from './VictoryStrips.module.css';

/**
 * The strip under the top bar once the world has ended (v1.3, 02-game-rules §22): on gold, the end as
 * its title, who won ("your guild" when it was the player's) and a link to the result above the
 * ranking. It stays while the world is frozen and is the reason the game's buttons are off.
 */
export function WorldEndedBanner() {
  const ended = useWorld().data?.phase === 'ENDED';
  const victory = useVictory(ended).data;
  return ended ? <EndedStrip result={victory?.result ?? null} /> : null;
}

/** The strip; the winner line once the result has loaded. */
function EndedStrip({ result }: { result: WorldResult | null }) {
  const { t } = useI18n();
  const mine = useMySide();
  return (
    <aside className={styles.ended} aria-label={t.endedBanner.label}>
      <Icon name="keep" className={styles.icon} />
      <div className={styles.endedText}>
        <strong className={styles.endedTitle}>{t.worldEnd.title}</strong>
        {result !== null && <span>{endHeadline(t, result.winner, mine(result.winner))}</span>}
        <Link className={styles.endedLink} to={rankingPath(0)}>
          {t.endedBanner.link} ›
        </Link>
      </div>
    </aside>
  );
}

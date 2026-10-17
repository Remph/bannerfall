import { Link, useLocation } from 'react-router';
import { useVictory, useWorld, type Victory, type VictorySide } from '../api/queries/world';
import { Icon } from '../components/Icon';
import { useI18n } from '../i18n';
import { OpeningCountdown } from '../pages/pregame/OpeningCountdown';
import { rankingPath } from '../pages/ranking/rankingPaths';
import { stateText, useMySide } from '../pages/ranking/victoryState';
import styles from './VictoryStrips.module.css';

/**
 * While a side's victory clock runs (v1.3, 02-game-rules §22), every page says so under the top bar:
 * who holds what and how long until they win, on gold when it is the player's own side, with the way
 * to the victory card. Not on the ranking, which has the card itself. The VICTORY_CLOCK notification
 * tells every player when a clock starts or stops.
 */
export function VictoryClockStrip() {
  const running = useWorld().data?.phase === 'OPEN';
  const victory = useVictory(running).data;
  const onRanking = useLocation().pathname.startsWith('/ranking');
  if (!running || victory === undefined || victory.leader === null || victory.winsAt === null || onRanking) {
    return null;
  }
  return <ClockStrip victory={victory} leader={victory.leader} winsAt={victory.winsAt} />;
}

function ClockStrip({ victory, leader, winsAt }: { victory: Victory; leader: VictorySide; winsAt: string }) {
  const { t, language } = useI18n();
  const mine = useMySide()(leader);
  return (
    <Link to={rankingPath(0)} className={mine ? `${styles.clock} ${styles.clockMine}` : styles.clock}>
      <Icon name="keep" className={styles.icon} />
      <span className={styles.text}>
        <strong>{stateText(t, victory, leader, mine, language)}</strong>
        <span className={styles.time}>
          {t.victory.winsIn} <OpeningCountdown opensAt={winsAt} />
        </span>
      </span>
      <Icon name="chevronRight" className={styles.chevron} />
    </Link>
  );
}

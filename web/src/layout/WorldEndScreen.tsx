import { useState } from 'react';
import { Link } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { usePlayerProfile, useRanking } from '../api/queries/ranking';
import { useVictory, useWorld, type WorldResult } from '../api/queries/world';
import { Icon } from '../components/Icon';
import { Sheet } from '../components/Sheet';
import { formatDateTime, formatNumber } from '../game/format';
import { useI18n } from '../i18n';
import { rankingPath } from '../pages/ranking/rankingPaths';
import { endHeadline, placeText, useMySide } from '../pages/ranking/victoryState';
import styles from './WorldEndScreen.module.css';
import { endScreenDue, loadWorldEndSeen, saveWorldEndSeen } from './worldEndSeen';

/**
 * The first time the game opens after the world ended (v1.3), a moment for it: who won and how, the
 * player's place and a few of his numbers, the way to the result. Once per end and browser
 * (worldEndSeen); the strip under the top bar stays.
 */
export function WorldEndScreen() {
  const ended = useWorld().data?.phase === 'ENDED';
  const result = useVictory(ended).data?.result ?? null;
  const [seen, setSeen] = useState(loadWorldEndSeen);
  if (!ended || result === null || !endScreenDue(result.endedAt, seen)) {
    return null;
  }
  const endedAt = result.endedAt;
  return (
    <EndScreen
      result={result}
      onClose={() => {
        saveWorldEndSeen(endedAt);
        setSeen(endedAt);
      }}
    />
  );
}

/** The screen itself: only now the player's profile and the ranking's size are loaded. */
function EndScreen({ result, onClose: close }: { result: WorldResult; onClose: () => void }) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const profile = usePlayerProfile(me.playerId).data;
  const total = useRanking(0).data?.total;
  const mine = useMySide();
  const winner = result.winner;
  const won = mine(winner);
  const place = placeText(t, result, mine);
  return (
    <Sheet open onClose={close} title={t.worldEnd.title}>
      <div className={styles.screen}>
        <div className={won ? `${styles.crest} ${styles.crestWon}` : styles.crest} aria-hidden="true">
          <Icon name="keep" />
        </div>
        <p className={styles.headline}>{endHeadline(t, winner, won)}</p>
        {winner !== null && <p className={styles.reason}>{t.victory.wonBy[result.reason]}</p>}
        <p className={styles.when}>{t.worldEnd.when(formatDateTime(Date.parse(result.endedAt), language))}</p>
        {place !== null && <p className={styles.place}>{place}</p>}
        {profile !== undefined && (
          <section className={styles.numbers} aria-label={t.worldEnd.numbers}>
            <h3>{t.worldEnd.numbers}</h3>
            <dl>
              <div>
                <dt>{t.worldEnd.rank}</dt>
                <dd>{total === undefined ? profile.rank : t.worldEnd.rankValue(profile.rank, total)}</dd>
              </div>
              <div>
                <dt>{t.worldEnd.points}</dt>
                <dd>{formatNumber(profile.points, language)}</dd>
              </div>
              <div>
                <dt>{t.worldEnd.villages}</dt>
                <dd>{profile.villages.length}</dd>
              </div>
              <div>
                <dt>{t.worldEnd.achievements}</dt>
                <dd>{profile.achievements.length}</dd>
              </div>
            </dl>
          </section>
        )}
        <p className={styles.stays}>{t.worldEnd.stays}</p>
        <div className={styles.actions}>
          <Link to={rankingPath(0)} className={styles.lead} onClick={close}>
            {t.worldEnd.seeResult}
          </Link>
          <button type="button" className={styles.second} onClick={close}>
            {t.worldEnd.lookAround}
          </button>
        </div>
      </div>
    </Sheet>
  );
}

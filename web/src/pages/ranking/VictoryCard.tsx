import { useState } from 'react';
import { useCurrentAccount } from '../../api/queries/auth';
import { useVictory, useWorld, type Victory, type VictorySide } from '../../api/queries/world';
import { Card } from '../../components/Card';
import { Icon } from '../../components/Icon';
import { formatDateTime, formatNumber, formatShare } from '../../game/format';
import {
  isPlayer,
  sameSide,
  shownSides,
  sideMeasure,
  sideName,
  villageShare,
  type SideMeasure,
} from '../../game/victory';
import { useI18n } from '../../i18n';
import { DESKTOP_QUERY, useMediaQuery } from '../../layout/useMediaQuery';
import { OpeningCountdown } from '../pregame/OpeningCountdown';
import styles from './RankingPage.module.css';
import { endHeadline, placeText, stateText, useMySide } from './victoryState';

const PERCENT = 100;
/** The final standings show this many sides. */
const FINAL_SIDES = 10;

/**
 * How the world is won, above the ranking (v1.3, 02-game-rules §22). The card leads with the state –
 * who holds what and how long until they win – with the race as keep pips or a share bar, the rule in
 * one sentence and the whole of it behind "Rules". Titled "Victory condition" while the world runs
 * and "Result" after its end; the player's own side is marked, NPC lords only show in the top three.
 * On phones a running world shows a one-line strip above the ranking that opens into the card.
 */
export function VictoryCard() {
  const { t } = useI18n();
  const victory = useVictory();
  const desktop = useMediaQuery(DESKTOP_QUERY);
  if (victory.data === undefined) {
    return null;
  }
  const ended = victory.data.result !== null;
  if (!ended && !desktop) {
    return <PhoneStrip victory={victory.data} />;
  }
  return (
    <Card title={ended ? t.victory.titleEnded : t.victory.titleRunning}>
      <div className={styles.victory}>
        {ended ? <Ended victory={victory.data} /> : <Running victory={victory.data} />}
      </div>
    </Card>
  );
}

/** Phones: the state in one line; a tap opens the whole card. */
function PhoneStrip({ victory }: { victory: Victory }) {
  const { t, language } = useI18n();
  const mine = useMySide();
  const [open, setOpen] = useState(false);
  const leader = victory.leader;
  return (
    <details className={styles.victoryStrip} onToggle={(event) => setOpen(event.currentTarget.open)}>
      <summary className={styles.victoryStripHead}>
        <Icon name="keep" className={styles.victoryStripIcon} />
        <span className={styles.victoryStripText}>
          <strong>
            {open || leader === null || victory.mode === 'NONE'
              ? t.victory.titleRunning
              : stateText(t, victory, leader, mine(leader), language)}
          </strong>
          {open ? null : leader !== null && victory.winsAt !== null ? (
            <span>
              {t.victory.winsIn} <OpeningCountdown opensAt={victory.winsAt} />
            </span>
          ) : (
            <span>{victory.mode === 'NONE' ? t.victory.ruleNone : t.victory.noLeaderYet}</span>
          )}
        </span>
        <Icon name="chevronDown" className={styles.victoryStripChevron} />
      </summary>
      <div className={styles.victory}>
        <Running victory={victory} />
      </div>
    </details>
  );
}

function Running({ victory }: { victory: Victory }) {
  const { t, language } = useI18n();
  const guilds = useWorld().data?.guildsEnabled ?? false;
  const mine = useMySide();
  const labels = t.victory;
  // a keep world lists only the sides that hold a keep: the rest would be a list of zeros
  const standings = shownSides(
    victory.mode === 'KEEPS' ? victory.sides.filter((side) => side.keeps > 0) : victory.sides,
  );
  const fullRule =
    victory.mode === 'DOMINATION'
      ? labels.ruleDomination(victory.dominationPercent, victory.holdHours)
      : labels.ruleKeeps(victory.keepsNeeded, victory.keepCount, victory.holdHours);
  if (victory.mode === 'NONE') {
    return (
      <>
        <p className={styles.victoryText}>{labels.ruleNone}</p>
        {victory.endsAt !== null && (
          <p className={styles.meta}>{labels.endsAt(formatDateTime(Date.parse(victory.endsAt), language))}</p>
        )}
      </>
    );
  }
  const leader = victory.leader;
  const closest = standings[0];
  // sides level with the closest one: naming one of them would read as if it were ahead
  const level =
    closest === undefined
      ? 0
      : victory.sides.filter((side) =>
          victory.mode === 'KEEPS' ? side.keeps === closest.keeps : side.villages === closest.villages,
        ).length;
  return (
    <>
      <div
        className={leader !== null && mine(leader) ? `${styles.leader} ${styles.leaderMine}` : styles.leader}
        role="status"
      >
        <p className={styles.stateLine}>
          {leader === null ? labels.noLeaderYet : stateText(t, victory, leader, mine(leader), language)}
        </p>
        <Race victory={victory} side={leader ?? closest} />
        {leader !== null && victory.winsAt !== null ? (
          <>
            <p className={styles.winsIn}>
              <span className={styles.winsInLabel}>{labels.winsIn}</span>
              <OpeningCountdown opensAt={victory.winsAt} />
            </p>
            <p className={styles.meta}>
              {(mine(leader) ? (leader.kind === 'GUILD' ? labels.winsAtYours : labels.winsAtYou) : labels.winsAt)(
                formatDateTime(Date.parse(victory.winsAt), language),
              )}
            </p>
          </>
        ) : (
          closest !== undefined && (
            <p className={styles.meta}>
              {victory.mode === 'KEEPS'
                ? level > 1
                  ? labels.levelKeeps(level, closest.keeps, victory.keepsNeeded)
                  : labels.closestKeeps(sideName(closest), closest.keeps, victory.keepsNeeded)
                : level > 1
                  ? labels.levelShare(
                      level,
                      formatShare(villageShare(closest, victory.allVillages), language),
                      victory.dominationPercent,
                    )
                  : labels.closestShare(
                      sideName(closest),
                      formatShare(villageShare(closest, victory.allVillages), language),
                      victory.dominationPercent,
                    )}
            </p>
          )
        )}
      </div>
      <p className={styles.victoryText}>
        {victory.mode === 'KEEPS'
          ? labels.ruleShortKeeps(victory.keepsNeeded, victory.keepCount, victory.holdHours)
          : labels.ruleShortDomination(victory.dominationPercent, victory.holdHours)}
      </p>
      {victory.endsAt !== null && (
        <p className={styles.meta}>{labels.endsAt(formatDateTime(Date.parse(victory.endsAt), language))}</p>
      )}
      <details className={styles.rules}>
        <summary>
          {labels.rules}
          <Icon name="chevronDown" className={styles.rulesChevron} />
        </summary>
        <p>
          {fullRule} {guilds ? labels.sidesGuilds : labels.sidesPlayers}
        </p>
      </details>
      {standings.length > 0 && (
        <Sides title={labels.standings} sides={standings} victory={victory} marked={victory.leader} />
      )}
    </>
  );
}

/** The race at a glance: a pip per keep (the side's in gold, the needed ones edged), or the share against the goal. */
function Race({ victory, side }: { victory: Victory; side: VictorySide | undefined }) {
  const { t, language } = useI18n();
  const held = side?.keeps ?? 0;
  if (victory.mode === 'KEEPS') {
    return (
      <span
        className={styles.pips}
        role="img"
        aria-label={t.victory.pips(held, victory.keepCount, victory.keepsNeeded)}
      >
        {Array.from({ length: victory.keepCount }, (_, index) => (
          <span
            key={index}
            className={styles.pip}
            data-held={index < held || undefined}
            data-needed={index < victory.keepsNeeded || undefined}
          >
            <Icon name="keep" />
          </span>
        ))}
      </span>
    );
  }
  const share = side === undefined ? 0 : villageShare(side, victory.allVillages);
  return (
    <span className={styles.shareBar} role="img" aria-label={formatShare(share, language)}>
      <span className={styles.shareFill} style={{ width: `${Math.min(PERCENT, share)}%` }} />
      <span className={styles.shareGoal} style={{ left: `${victory.dominationPercent}%` }} />
    </span>
  );
}

function Ended({ victory }: { victory: Victory }) {
  const { t, language } = useI18n();
  const mine = useMySide();
  const labels = t.victory;
  const result = victory.result;
  if (result === null) {
    return null;
  }
  const winner = result.winner;
  const won = mine(winner);
  const place = placeText(t, result, mine);
  return (
    <>
      <div className={won ? `${styles.leader} ${styles.leaderMine}` : styles.leader} role="status">
        <p className={styles.stateLine}>{endHeadline(t, winner, won)}</p>
        {winner !== null && <p className={styles.victoryText}>{labels.wonBy[result.reason]}</p>}
        <p className={styles.meta}>{labels.ended(formatDateTime(Date.parse(result.endedAt), language))}</p>
      </div>
      {place !== null && <p className={styles.victoryText}>{place}</p>}
      <p className={styles.meta}>{labels.frozen}</p>
      {result.sides.length > 0 && (
        <Sides
          title={labels.finalStandings}
          sides={shownSides(result.sides).slice(0, FINAL_SIDES)}
          victory={victory}
          marked={result.winner}
        />
      )}
    </>
  );
}

/** The leading sides, each with what counts in this world: share of the villages, keeps or points. */
function Sides({
  title,
  sides,
  victory,
  marked,
}: {
  title: string;
  sides: VictorySide[];
  victory: Victory;
  marked: VictorySide | null;
}) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const mine = useMySide();
  const labels = t.victory;
  const measure = sideMeasure(victory);
  const ended = victory.result !== null;
  const values: Record<SideMeasure, (side: VictorySide) => string> = {
    share: (side) => labels.share(formatShare(villageShare(side, victory.allVillages), language), side.villages),
    keeps: (side) => labels.keeps(side.keeps, victory.keepCount),
    points: (side) => labels.points(formatNumber(side.points, language)),
  };
  const value = values[measure];
  return (
    <div className={styles.sides}>
      <h3 className={styles.sidesTitle}>{title}</h3>
      <ol className={styles.sideList}>
        {sides.map((side) => (
          <li key={`${side.kind}:${side.id}`} className={mine(side) ? `${styles.side} ${styles.mine}` : styles.side}>
            <span className={styles.name}>
              {sideName(side)}
              {isPlayer(side, me.playerId) && <span className={styles.tag}>{t.map.youTag}</span>}
              {side.kind === 'GUILD' && mine(side) && <span className={styles.tag}>{labels.guildTag}</span>}
              {side.npc && <span className={styles.tag}>{t.map.npcTag}</span>}
              {sameSide(side, marked) && (
                <span className={`${styles.tag} ${styles.leadTag}`}>{ended ? labels.winnerTag : labels.leaderTag}</span>
              )}
            </span>
            <span className={styles.sideValue}>{value(side)}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}

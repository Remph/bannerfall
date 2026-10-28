import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useEnemies } from '../../api/queries/guilds';
import { RANKING_PAGE_SIZE, useRanking, type RankingEntry, type RankingKind } from '../../api/queries/ranking';
import { ActionsStayOn } from '../../layout/ActionsStayOn';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Pagination } from '../../components/Pagination';
import { SegmentedControl } from '../../components/SegmentedControl';
import { enemySets, isEnemyPlayer, type EnemySets } from '../../game/enemies';
import { formatNumber } from '../../game/format';
import { bracketTag } from '../../game/guilds';
import { pageCount } from '../../game/reports';
import { useI18n } from '../../i18n';
import { EnemyPill } from '../guild/EnemyPill';
import { pageFromSearch } from '../reports/reportPaths';
import { RANKING_KINDS, STATISTICS_PATH, playerPath, rankingKindFromSearch, rankingPath } from './rankingPaths';
import styles from './RankingPage.module.css';
import { VictoryCard } from './VictoryCard';

/**
 * /ranking?by=&page= – how the world is won (v1.3), then all players by points or, in the other
 * tabs (1.5), by a statistics total, NPC lords marked (05-frontend §3); a line opens the profile.
 */
export function RankingPage() {
  const { t } = useI18n();
  const [params] = useSearchParams();
  const page = pageFromSearch(params);
  const by = rankingKindFromSearch(params);
  const ranking = useRanking(page, by);
  const enemies = enemySets(useEnemies().data);
  const navigate = useNavigate();

  if (ranking.isError) {
    return (
      <div className={styles.page}>
        <ErrorMessage>{errorMessage(t, ranking.error)}</ErrorMessage>
        {/* reading again changes nothing, so it stays on in an ended world (v1.3) */}
        <ActionsStayOn>
          <div>
            <Button type="button" onClick={() => void ranking.refetch()}>
              {t.common.retry}
            </Button>
          </div>
        </ActionsStayOn>
      </div>
    );
  }
  if (ranking.isPending) {
    return <p>{t.common.loading}</p>;
  }

  const { entries, total } = ranking.data;
  const pages = pageCount(total, RANKING_PAGE_SIZE);
  // a page beyond the last one (players were deleted meanwhile, or a typed URL): the last page
  if (entries.length === 0 && page > 0 && total > 0 && !ranking.isFetching) {
    return <Navigate to={rankingPath(pages - 1, by)} replace />;
  }

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.ranking.title}</h1>
        <p className={styles.meta}>
          {t.ranking.count(total)} ·{' '}
          <Link to={STATISTICS_PATH} className={styles.statisticsLink}>
            {t.ranking.worldStatistics}
          </Link>
        </p>
      </header>
      {/* desktop: the list left, the victory card at the right (1.3); phones: the card first */}
      <div className={styles.columns}>
        <div className={styles.victoryColumn}>
          <VictoryCard />
        </div>
        <div className={styles.listColumn}>
          <div className={styles.tabs}>
            <SegmentedControl
              label={t.ranking.tabsLabel}
              options={RANKING_KINDS.map((kind) => ({ value: kind, label: t.ranking.tabs[kind] }))}
              value={by}
              onChange={(kind) => void navigate(rankingPath(0, kind))}
              scroll
            />
            <p className={styles.meta}>{t.ranking.tabHint[by]}</p>
          </div>
          {entries.length === 0 ? (
            <p className={styles.meta}>{t.ranking.empty}</p>
          ) : (
            <ol className={styles.list} start={entries[0]?.rank}>
              {entries.map((entry, index) => (
                <Line
                  key={entry.playerId}
                  entry={entry}
                  by={by}
                  showRank={entries[index - 1]?.rank !== entry.rank}
                  enemies={enemies}
                />
              ))}
            </ol>
          )}
          {pages > 1 && (
            <Pagination
              page={page}
              pages={pages}
              pathFor={(target) => rankingPath(target, by)}
              label={t.ranking.pagesLabel}
              previous={t.ranking.previous}
              next={t.ranking.next}
              info={t.ranking.page(page + 1, pages)}
            />
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * One player: the rank (not repeated for equal values), name with tags, villages, and what the tab
 * sorts by (the points, or the statistics total).
 */
function Line({
  entry,
  by,
  showRank,
  enemies,
}: {
  entry: RankingEntry;
  by: RankingKind;
  showRank: boolean;
  /** The marks of the viewer's guild (1.7): a marked player, or one of a marked guild, gets the pill. */
  enemies: EnemySets;
}) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const mine = entry.playerId === me.playerId;
  return (
    <li className={mine ? `${styles.line} ${styles.mine}` : styles.line}>
      <Link to={playerPath(entry.playerId)} className={styles.lineLink}>
        <span className={styles.rank}>{showRank ? entry.rank : <span className="sr-only">{entry.rank}</span>}</span>
        <span className={styles.who}>
          <span className={styles.name}>
            {entry.guildTag != null && <span className={styles.guildTag}>{bracketTag(entry.guildTag)}</span>}
            {entry.name}
            {mine && <span className={styles.tag}>{t.map.youTag}</span>}
            {entry.npc && <span className={styles.tag}>{t.map.npcTag}</span>}
            {isEnemyPlayer(enemies, { id: entry.playerId, guildId: entry.guildId }) && <EnemyPill />}
          </span>
          <span className={styles.sub}>{t.ranking.villageCount(entry.villageCount)}</span>
        </span>
        <span className={styles.points}>{formatNumber(by === 'POINTS' ? entry.points : entry.value, language)}</span>
      </Link>
    </li>
  );
}

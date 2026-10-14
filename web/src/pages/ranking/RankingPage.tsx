import { Link, Navigate, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { RANKING_PAGE_SIZE, useRanking, type RankingEntry } from '../../api/queries/ranking';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Pagination } from '../../components/Pagination';
import { formatNumber } from '../../game/format';
import { bracketTag } from '../../game/guilds';
import { pageCount } from '../../game/reports';
import { useI18n } from '../../i18n';
import { pageFromSearch } from '../reports/reportPaths';
import { playerPath, rankingPath } from './rankingPaths';
import styles from './RankingPage.module.css';

/** /ranking?page= – all players by points, NPC lords marked (05-frontend §3); a line opens the profile. */
export function RankingPage() {
  const { t } = useI18n();
  const [params] = useSearchParams();
  const page = pageFromSearch(params);
  const ranking = useRanking(page);

  if (ranking.isError) {
    return (
      <div className={styles.page}>
        <ErrorMessage>{errorMessage(t, ranking.error)}</ErrorMessage>
        <div>
          <Button type="button" onClick={() => void ranking.refetch()}>
            {t.common.retry}
          </Button>
        </div>
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
    return <Navigate to={rankingPath(pages - 1)} replace />;
  }

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.ranking.title}</h1>
        <p className={styles.meta}>{t.ranking.count(total)}</p>
      </header>
      {entries.length === 0 ? (
        <p className={styles.meta}>{t.ranking.empty}</p>
      ) : (
        <ol className={styles.list} start={entries[0]?.rank}>
          {entries.map((entry, index) => (
            <Line key={entry.playerId} entry={entry} showRank={entries[index - 1]?.rank !== entry.rank} />
          ))}
        </ol>
      )}
      {pages > 1 && (
        <Pagination
          page={page}
          pages={pages}
          pathFor={rankingPath}
          label={t.ranking.pagesLabel}
          previous={t.ranking.previous}
          next={t.ranking.next}
          info={t.ranking.page(page + 1, pages)}
        />
      )}
    </div>
  );
}

/** One player: the rank (not repeated for equal points), name with tags, points and villages. */
function Line({ entry, showRank }: { entry: RankingEntry; showRank: boolean }) {
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
          </span>
          <span className={styles.sub}>{t.ranking.villageCount(entry.villageCount)}</span>
        </span>
        <span className={styles.points}>{formatNumber(entry.points, language)}</span>
      </Link>
    </li>
  );
}

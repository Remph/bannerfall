import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useWorldStatistics, type WorldStatistics } from '../../api/queries/statistics';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { LineChart } from '../../components/LineChart';
import { SegmentedControl } from '../../components/SegmentedControl';
import { formatDateTime, formatNumber, formatShortDay } from '../../game/format';
import { useI18n } from '../../i18n';
import { ActionsStayOn } from '../../layout/ActionsStayOn';
import { rankingPath } from '../ranking/rankingPaths';
import styles from './WorldStatisticsPage.module.css';

type WorldView = 'VILLAGES' | 'BARBARIANS' | 'PLAYERS' | 'POINTS';
const WORLD_VIEWS: readonly WorldView[] = ['VILLAGES', 'BARBARIANS', 'PLAYERS', 'POINTS'];

/**
 * /statistics – the world over time (1.5, 02-game-rules §24): today's leaders' points and the
 * world's totals per day. Reached from the ranking.
 */
export function WorldStatisticsPage() {
  const { t } = useI18n();
  const statistics = useWorldStatistics();
  return (
    <div className={styles.page}>
      <BackLink to={rankingPath(0)}>{t.ranking.title}</BackLink>
      <header className={styles.head}>
        <h1>{t.statistics.worldTitle}</h1>
        <p className={styles.meta}>{t.statistics.worldIntro}</p>
      </header>
      {statistics.isError ? (
        <>
          <ErrorMessage>{errorMessage(t, statistics.error)}</ErrorMessage>
          {/* reading again changes nothing, so it stays on in an ended world */}
          <ActionsStayOn>
            <div>
              <Button type="button" onClick={() => void statistics.refetch()}>
                {t.common.retry}
              </Button>
            </div>
          </ActionsStayOn>
        </>
      ) : statistics.isPending ? (
        <p>{t.common.loading}</p>
      ) : (
        <Charts statistics={statistics.data} />
      )}
    </div>
  );
}

function Charts({ statistics }: { statistics: WorldStatistics }) {
  const { t, language } = useI18n();
  const [view, setView] = useState<WorldView>('VILLAGES');
  const n = (value: number) => formatNumber(value, language);
  const day = (value: string) => formatShortDay(value, language);
  const today = statistics.days[statistics.days.length - 1];
  const worldValue = (entry: WorldStatistics['days'][number]): number =>
    ({
      VILLAGES: entry.villages,
      BARBARIANS: entry.barbarianVillages,
      PLAYERS: entry.players,
      POINTS: entry.points,
    })[view];
  return (
    <div className={styles.columns}>
      <Card title={t.statistics.leaders}>
        <p className={styles.cardHint}>{t.statistics.leadersHint}</p>
        <LineChart
          label={t.statistics.leaders}
          lines={statistics.leaders.map((leader) => ({
            key: leader.playerId,
            label: leader.name,
            points: leader.days.map((entry) => ({ day: entry.day, value: entry.points })),
          }))}
          formatValue={n}
          formatDay={day}
          dayLabel={t.statistics.day}
        />
      </Card>
      <Card
        title={t.statistics.world}
        action={
          <SegmentedControl
            label={t.statistics.chartLabel}
            options={WORLD_VIEWS.map((value) => ({ value, label: t.statistics.worldChart[value] }))}
            value={view}
            onChange={setView}
            scroll
            compact
          />
        }
      >
        {today !== undefined && (
          <p className={styles.cardHint}>
            {t.statistics.worldFacts(n(today.players), n(today.villages), n(today.barbarianVillages))}
          </p>
        )}
        <LineChart
          label={`${t.statistics.world}: ${t.statistics.worldChart[view]}`}
          lines={[
            {
              key: view,
              label: t.statistics.worldChart[view],
              points: statistics.days.map((entry) => ({ day: entry.day, value: worldValue(entry) })),
            },
          ]}
          formatValue={n}
          formatDay={day}
          dayLabel={t.statistics.day}
        />
      </Card>
      <p className={styles.meta}>{t.statistics.since(formatDateTime(Date.parse(statistics.since), language))}</p>
    </div>
  );
}

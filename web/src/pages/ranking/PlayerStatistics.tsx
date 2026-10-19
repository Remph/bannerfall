import { useState } from 'react';
import { usePlayerHistory, type PlayerStats } from '../../api/queries/ranking';
import { Card } from '../../components/Card';
import { LineChart } from '../../components/LineChart';
import { SegmentedControl } from '../../components/SegmentedControl';
import { formatDateTime, formatNumber, formatShortDay } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './PlayerStatistics.module.css';

/**
 * A player's statistics on his profile (1.5, 02-game-rules §24): what he did since the world
 * started counting. Everybody sees everybody's, NPC lords' too.
 */
export function StatisticsCard({ stats }: { stats: PlayerStats }) {
  const { t, language } = useI18n();
  const n = (value: number) => formatNumber(value, language);
  const rows: [string, string][] = [
    [t.statistics.attacks, t.statistics.wonLost(n(stats.attacksWon), n(stats.attacksLost))],
    [t.statistics.defences, t.statistics.wonLost(n(stats.defencesWon), n(stats.defencesLost))],
    [t.statistics.killedAttacking, n(stats.killedAttacking)],
    [t.statistics.killedDefending, n(stats.killedDefending)],
    [t.statistics.unitsLost, n(stats.unitsLost)],
    [t.statistics.loot, t.statistics.takenLost(n(stats.lootTaken), n(stats.lootLost))],
    [t.statistics.villages, t.statistics.conqueredLost(n(stats.villagesConquered), n(stats.villagesLost))],
    [t.statistics.unitsRecruited, n(stats.unitsRecruited)],
    [t.statistics.buildingLevels, n(stats.buildingLevels)],
  ];
  return (
    <Card title={t.statistics.title}>
      <dl className={styles.rows}>
        {rows.map(([label, value]) => (
          <div key={label} className={styles.row}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <p className={styles.note}>
        {t.statistics.since(formatDateTime(Date.parse(stats.since), language))} {t.statistics.populationNote}
      </p>
    </Card>
  );
}

type HistoryView = 'POINTS' | 'VILLAGES';

/** Points or villages per day (1.5): one line, today's value last. Nothing while it loads or fails. */
export function HistoryCard({ playerId, name }: { playerId: number; name: string }) {
  const { t, language } = useI18n();
  const history = usePlayerHistory(playerId);
  const [view, setView] = useState<HistoryView>('POINTS');
  if (!history.isSuccess || history.data.days.length === 0) {
    return null;
  }
  const days = history.data.days;
  const points = days.map((day) => ({ day: day.day, value: view === 'POINTS' ? day.points : day.villages }));
  return (
    <Card
      title={t.statistics.history}
      action={
        <SegmentedControl
          label={t.statistics.chartLabel}
          options={(['POINTS', 'VILLAGES'] as const).map((value) => ({ value, label: t.statistics.chart[value] }))}
          value={view}
          onChange={setView}
          compact
        />
      }
    >
      <LineChart
        label={`${name}: ${t.statistics.chart[view]}`}
        lines={[{ key: playerId, label: name, points }]}
        formatValue={(value) => formatNumber(value, language)}
        formatDay={(day) => formatShortDay(day, language)}
        dayLabel={t.statistics.day}
      />
      {days.length === 1 && <p className={styles.note}>{t.statistics.oneDay}</p>}
    </Card>
  );
}

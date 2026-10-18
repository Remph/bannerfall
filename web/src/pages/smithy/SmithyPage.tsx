import { Link, Navigate, useParams } from 'react-router';
import { BackLink } from '../../components/BackLink';
import { useRefreshWhenResearchEnds, useSmithy, useStartResearch, type Smithy } from '../../api/queries/smithy';
import { useVillage, type Village } from '../../api/queries/villages';
import { Card } from '../../components/Card';
import { useI18n } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import { ResearchCard, UnitRow } from './ResearchParts';
import styles from './Smithy.module.css';

/**
 * /v/:villageId/smithy – what every unit can do and, if the world has unit levels, the research
 * (M30, 02-game-rules §5.4). Reached from the village overview only; it has no tab of its own. The
 * values are shown with unit levels off too: the player sees what the units can do either way.
 */
export function SmithyPage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village gets a fresh page
  return <SmithyScreen key={villageId} villageId={villageId} />;
}

function SmithyScreen({ villageId }: { villageId: number }) {
  const { t } = useI18n();
  const village = useVillage(villageId);
  const smithy = useSmithy(villageId);
  useRefreshWhenResearchEnds(villageId, smithy.data, smithy.dataUpdatedAt, smithy.errorUpdatedAt);
  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (smithy.isError) {
    return <VillageError error={smithy.error} onRetry={() => void smithy.refetch()} />;
  }
  if (village.isPending || smithy.isPending) {
    return <p>{t.common.loading}</p>;
  }
  return <SmithyView village={village.data} smithy={smithy.data} />;
}

function SmithyView({ village, smithy }: { village: Village; smithy: Smithy }) {
  const { t } = useI18n();
  const s = t.smithy;
  const start = useStartResearch(village.id);
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <BackLink to={`/v/${village.id}`}>{s.back}</BackLink>
        <h1>{s.title}</h1>
        <p className={styles.hint}>{smithy.smithyLevel === 0 ? s.notBuilt : t.village.level(smithy.smithyLevel)}</p>
        {smithy.smithyLevel === 0 && (
          <p>
            <Link className={styles.link} to={{ pathname: `/v/${village.id}/hq`, hash: 'SMITHY' }}>
              {s.toTownHall}
            </Link>
          </p>
        )}
        <p className={styles.hint}>{smithy.levelsEnabled ? s.levelsOn : s.levelsOff}</p>
      </header>
      {smithy.levelsEnabled && <ResearchCard villageId={village.id} order={smithy.running} place="SMITHY" />}
      <Card title={s.unitsTitle}>
        <ul className={styles.list}>
          {smithy.units.map((unit) => (
            <UnitRow
              key={unit.unit}
              unit={unit}
              village={village}
              place="SMITHY"
              levelsEnabled={smithy.levelsEnabled}
              starting={start.isPending && start.variables === unit.unit}
              anyStarting={start.isPending}
              error={start.isError && start.variables === unit.unit ? start.error : null}
              onStart={() => start.mutate(unit.unit)}
            />
          ))}
        </ul>
      </Card>
    </div>
  );
}

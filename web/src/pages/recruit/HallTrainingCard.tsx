import { errorMessage } from '../../api/errors';
import { useHallTraining, useRefreshWhenResearchEnds, useStartResearch } from '../../api/queries/smithy';
import type { Village } from '../../api/queries/villages';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import type { UnitType } from '../../game/gameData';
import { useT } from '../../i18n';
import { ResearchCard, UnitRow } from '../smithy/ResearchParts';
import smithyStyles from '../smithy/Smithy.module.css';
import styles from './RecruitPage.module.css';

/**
 * Hall training (1.4, 02-game-rules §5.4) on the Banner Hall's recruiting tab: the paladin's and the
 * banner thief's levels, trained here like a research in the smithy, in every world that has them.
 */
export function HallTrainingCard({ village }: { village: Village }) {
  const t = useT();
  const s = t.hallTraining;
  const hall = useHallTraining(village.id);
  const start = useStartResearch(village.id);
  useRefreshWhenResearchEnds(village.id, hall.data, hall.dataUpdatedAt, hall.errorUpdatedAt);
  if (hall.isError) {
    return (
      <Card title={s.title}>
        <ErrorMessage>{errorMessage(t, hall.error)}</ErrorMessage>
      </Card>
    );
  }
  if (hall.isPending) {
    return (
      <Card title={s.title}>
        <p className={styles.notice}>{t.common.loading}</p>
      </Card>
    );
  }
  const training = hall.data;
  const has = (type: UnitType) => training.units.some((unit) => unit.unit === type);
  return (
    <>
      <ResearchCard villageId={village.id} order={training.running} place="BANNER_HALL" />
      <Card title={s.title}>
        <div className={smithyStyles.body}>
          <p className={smithyStyles.hint}>{s.intro(has('PALADIN'), has('BANNER_THIEF'))}</p>
        </div>
        <ul className={smithyStyles.list}>
          {training.units.map((unit) => (
            <UnitRow
              key={unit.unit}
              unit={unit}
              village={village}
              place="BANNER_HALL"
              levelsEnabled
              theftChances={training.theftChancePerThief}
              starting={start.isPending && start.variables === unit.unit}
              anyStarting={start.isPending}
              error={start.isError && start.variables === unit.unit ? start.error : null}
              onStart={() => start.mutate(unit.unit)}
            />
          ))}
        </ul>
      </Card>
    </>
  );
}

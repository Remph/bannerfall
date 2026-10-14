import { Link } from 'react-router';
import { useFarmTargets } from '../../api/queries/farm';
import type { VillageRef } from '../../api/queries/troops';
import { Icon } from '../../components/Icon';
import { templateOf } from '../../game/farm';
import { formatCoords } from '../../game/format';
import type { UnitMap } from '../../game/troops';
import { useT } from '../../i18n';
import { farmPath, farmUnitsState } from '../farm/farmPaths';
import styles from './ReportsPage.module.css';
import { villageName } from './reportText';

interface AddToFarmLinkProps {
  /** The other side's village (`farmCandidate`). */
  village: VillageRef;
  /** The village whose farm list gets the target (`farmListVillageId`). */
  villageId: number;
  /** What the reader's attack sent (attack reports only): filled in as the template. */
  sent?: UnitMap;
}

/**
 * "Add to farm list" on a battle report (M38): opens a farm list (the sending village's for the
 * reader's attacks, else the current village's) with the village (and the units the attack sent) filled in, where the player checks the template and adds
 * it. A village already on that list gets a plain link to the list instead.
 */
export function AddToFarmLink({ village, villageId, sent }: AddToFarmLinkProps) {
  const t = useT();
  const targets = useFarmTargets(villageId);
  const name = `${villageName(t, village)} ${formatCoords(village.x, village.y)}`;
  if (targets.data?.some((target) => target.target.id === village.id)) {
    return (
      <Link className={styles.reportAction} to={farmPath(villageId)}>
        <Icon name="FARM" /> {t.reports.onFarmList(name)}
      </Link>
    );
  }
  return (
    <Link
      className={styles.reportAction}
      to={farmPath(villageId, village)}
      state={sent === undefined ? undefined : farmUnitsState(templateOf(sent))}
    >
      <Icon name="plus" /> {t.reports.addToFarm(name)}
    </Link>
  );
}

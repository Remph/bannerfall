import type { ReactNode } from 'react';
import type { BuildingInfo } from '../../api/queries/buildings';
import { Icon } from '../../components/Icon';
import { useT } from '../../i18n';
import styles from './TownHallPage.module.css';

/**
 * Icon, name, level and – if upgrades or (1.6) demolitions are queued – the level after them; the
 * info button is pinned to the end of the row. Used by both sides of the Town Hall's switch.
 */
export function BuildingHead({ info, infoButton }: { info: BuildingInfo; infoButton: ReactNode }) {
  const t = useT();
  const queued = info.queuedLevel !== info.level;
  return (
    <div className={styles.rowHead}>
      <span className={styles.icon}>
        <Icon name={info.building} />
      </span>
      <h3 className={styles.name}>{t.buildings[info.building]}</h3>
      {info.level === 0 ? (
        <span className={styles.badgeEmpty}>{t.village.notBuilt}</span>
      ) : (
        <span className={styles.badge}>{t.village.level(info.level)}</span>
      )}
      {queued && (
        <span className={styles.queued}>
          <span aria-hidden="true">{t.townHall.queuedLevel(info.queuedLevel)}</span>
          <span className="sr-only">{t.townHall.queuedLevelLong(info.queuedLevel)}</span>
        </span>
      )}
      <span className={styles.info}>{infoButton}</span>
    </div>
  );
}

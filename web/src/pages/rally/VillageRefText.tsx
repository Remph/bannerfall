import { Link } from 'react-router';
import type { VillageRef } from '../../api/queries/troops';
import { formatCoords } from '../../game/format';
import { useT } from '../../i18n';
import styles from './RallyPage.module.css';
import { mapPath } from './rallyPaths';

interface VillageRefTextProps {
  village: VillageRef;
  /** The village of the URL: the map link keeps it. */
  currentVillageId: number;
  /** Without the map link (inside a sheet, where leaving the page would be a surprise). */
  link?: boolean;
}

/** A village of a movement or stack: its name and coordinates; the coordinates open the map there. */
export function VillageRefText({ village, currentVillageId, link = true }: VillageRefTextProps) {
  const t = useT();
  const coords = formatCoords(village.x, village.y);
  return (
    <>
      {village.name ?? t.map.barbarianVillage}{' '}
      {link ? (
        <Link className={styles.coordsLink} to={mapPath(currentVillageId, village)}>
          {coords}
        </Link>
      ) : (
        <span className={styles.coords}>{coords}</span>
      )}
    </>
  );
}

import { MARKER_PATHS, type MarkerKind } from '../../game/mapMovements';
import styles from './MapPage.module.css';

/**
 * A movement badge as the canvas draws it (M26), for the legend and the village panel: a round
 * badge in the kind's colour with the swords. Decoration only, the text next to it says what it is.
 */
export function MarkerIcon({ kind }: { kind: MarkerKind }) {
  return (
    <svg className={styles.marker} data-kind={kind} viewBox="-3 -3 30 30" aria-hidden="true">
      <circle cx="12" cy="12" r="14" />
      <path d={MARKER_PATHS[kind]} transform="translate(2.4 2.4) scale(0.8)" />
    </svg>
  );
}

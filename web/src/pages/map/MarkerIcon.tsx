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

/** The faded banner of a used banner chance as the canvas draws it (v1.3), for the legend. */
export function UsedBannerIcon() {
  return (
    <svg className={styles.usedBanner} viewBox="0 0 16 16" aria-hidden="true">
      <path d="M11 15 V2 H5" />
      <path d="M11 2 H5 V10 L8 8 L11 10 Z" />
    </svg>
  );
}

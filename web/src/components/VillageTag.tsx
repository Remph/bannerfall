import styles from './VillageTag.module.css';

/**
 * The player's own tag on a village (1.7, 02-game-rules §26) as a small pill next to its name.
 * `onBar` is for the header (the sky since 2.0).
 */
export function VillageTag({ tag, onBar = false }: { tag: string; onBar?: boolean }) {
  return <span className={onBar ? `${styles.tag} ${styles.onBar}` : styles.tag}>{tag}</span>;
}

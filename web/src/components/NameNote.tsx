import type { ReactNode } from 'react';
import styles from './NameNote.module.css';

/**
 * A quiet note after a name in running text ("you", "NPC", "leads"), with the gap as a wide space
 * instead of a left margin: when the note drops to the next line the space ends the line before it,
 * so the note starts flush instead of indented (2.1). The note itself keeps its own class.
 */
export function NameNote({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <>
      <NoteGap />
      <span className={className}>{children}</span>
    </>
  );
}

/** Only the gap, for a note that renders its own span (the enemy pill). */
export function NoteGap() {
  return <span className={styles.gap}> </span>;
}

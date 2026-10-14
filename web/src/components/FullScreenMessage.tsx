import type { ReactNode } from 'react';
import styles from './FullScreenMessage.module.css';

/** Centered message for whole-page states (loading, server unreachable). */
export function FullScreenMessage({ children }: { children: ReactNode }) {
  return <div className={styles.screen}>{children}</div>;
}

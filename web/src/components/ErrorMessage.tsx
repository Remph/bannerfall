import type { ReactNode } from 'react';
import styles from './ErrorMessage.module.css';

/** A visible error box; role="alert" makes screen readers announce it right away. */
export function ErrorMessage({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className={styles.error}>
      {children}
    </p>
  );
}

import { useId, type ReactNode } from 'react';
import styles from './Card.module.css';

interface CardProps {
  title: string;
  /** For links to the card (`#incoming`). */
  id?: string;
  /** Extra class for the outer element, e.g. to place it in a grid. */
  className?: string;
  /** A control at the right of the title (the village page's picture | list switch). */
  action?: ReactNode;
  children: ReactNode;
}

/** A parchment panel with a title (05-frontend §8). The title also names the section for screen readers. */
export function Card({ title, id, className, action, children }: CardProps) {
  const titleId = useId();
  const heading = (
    <h2 id={titleId} className={styles.title}>
      {title}
    </h2>
  );
  return (
    <section id={id} className={[styles.card, className].filter(Boolean).join(' ')} aria-labelledby={titleId}>
      {action === undefined ? (
        heading
      ) : (
        <div className={styles.header}>
          {heading}
          <div className={styles.action}>{action}</div>
        </div>
      )}
      {children}
    </section>
  );
}

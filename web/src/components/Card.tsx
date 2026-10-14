import { useId, type ReactNode } from 'react';
import styles from './Card.module.css';

interface CardProps {
  title: string;
  /** For links to the card (`#incoming`). */
  id?: string;
  /** Extra class for the outer element, e.g. to place it in a grid. */
  className?: string;
  children: ReactNode;
}

/** A parchment panel with a title (05-frontend §8). The title also names the section for screen readers. */
export function Card({ title, id, className, children }: CardProps) {
  const titleId = useId();
  return (
    <section id={id} className={[styles.card, className].filter(Boolean).join(' ')} aria-labelledby={titleId}>
      <h2 id={titleId} className={styles.title}>
        {title}
      </h2>
      {children}
    </section>
  );
}

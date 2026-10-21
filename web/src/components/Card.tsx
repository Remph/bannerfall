import { useId, type ReactNode } from 'react';
import styles from './Card.module.css';
import { Icon } from './Icon';

interface CardProps {
  title: string;
  /** For links to the card (`#incoming`). */
  id?: string;
  /** Extra class for the outer element, e.g. to place it in a grid. */
  className?: string;
  /** A control at the right of the title (the village page's picture | list switch). */
  action?: ReactNode;
  /**
   * Makes the title a button that folds the card's body away (1.6.1). `summary` stays next to the
   * title while it is folded (a count), so nothing is hidden without a trace.
   */
  collapse?: { collapsed: boolean; onToggle: () => void; summary?: ReactNode };
  children: ReactNode;
}

/** A parchment panel with a title (05-frontend §8). The title also names the section for screen readers. */
export function Card({ title, id, className, action, collapse, children }: CardProps) {
  const titleId = useId();
  const bodyId = useId();
  const heading = (
    <h2 id={titleId} className={styles.title}>
      {collapse === undefined ? (
        title
      ) : (
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={!collapse.collapsed}
          aria-controls={bodyId}
          onClick={collapse.onToggle}
        >
          <span>{title}</span>
          {collapse.collapsed && collapse.summary}
          <Icon name={collapse.collapsed ? 'chevronDown' : 'chevronUp'} className={styles.toggleIcon} />
        </button>
      )}
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
      {collapse === undefined ? (
        children
      ) : (
        <div id={bodyId} hidden={collapse.collapsed}>
          {children}
        </div>
      )}
    </section>
  );
}

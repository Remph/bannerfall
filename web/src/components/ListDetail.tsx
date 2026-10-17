import { useEffect, useRef, type ReactNode } from 'react';
import { InListDetailContext } from './listDetailContext';
import styles from './ListDetail.module.css';

interface ListDetailProps {
  list: ReactNode;
  /** The open item's page, or null: then `empty` says what to do. */
  detail: ReactNode | null;
  empty: string;
  /** Names the detail column for screen readers ("Open report"). */
  label: string;
  /** Which item is open: the detail column starts at its top again when it changes. */
  openKey: string | number | undefined;
  /** The detail fills the column's height instead of scrolling in it (a conversation with its input at the bottom). */
  fill?: boolean;
}

/**
 * Desktop (≥ 1024 px, 1.3): a list and the item opened from it side by side, so opening one
 * doesn't leave the list (reports, messages). The detail column stays in view below the bars
 * while the list scrolls. Phones show the list and the item as pages of their own instead. An
 * opened item fades in (1.3, motion); it starts afresh (`openKey`) for each item.
 */
export function ListDetail({ list, detail, empty, label, openKey, fill = false }: ListDetailProps) {
  const detailRef = useRef<HTMLElement>(null);
  const shownKey = useRef(openKey);

  // another item: from its top, not where the last one was scrolled to. Opened from the list, the
  // focus moves to it, so keyboard and screen reader users carry on in the item instead of the rest
  // of the list (not when the page opens with it: the focus is nowhere yet).
  useEffect(() => {
    const detail = detailRef.current;
    if (detail === null) {
      return;
    }
    detail.scrollTop = 0;
    if (openKey !== undefined && openKey !== shownKey.current) {
      detail.focus({ preventScroll: true });
    }
    shownKey.current = openKey;
  }, [openKey]);

  return (
    <div className={styles.split}>
      <div className={styles.list}>{list}</div>
      <section
        ref={detailRef}
        className={fill && detail !== null ? `${styles.detail} ${styles.fill}` : styles.detail}
        aria-label={label}
        tabIndex={-1}
      >
        {detail === null ? (
          <p className={styles.empty}>{empty}</p>
        ) : (
          <InListDetailContext key={openKey} value={true}>
            {detail}
          </InListDetailContext>
        )}
      </section>
    </div>
  );
}

import { useEffect, useId, useRef } from 'react';
import { useNow, useRefreshWhenNowEnds } from '../../api/queries/now';
import { Icon } from '../../components/Icon';
import { hostileCount, nowSections } from '../../game/now';
import { badgeText } from '../../game/reports';
import { useT } from '../../i18n';
import styles from './NowDrawer.module.css';
import { NowList } from './NowList';

interface NowDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentVillageId: number | undefined;
}

/**
 * 1024 to 1599 px, and on the map at any desktop width (2.0): a tab on the right edge, and a click
 * slides the Now list in over the page. Esc, a click outside or the close button put it away again,
 * and the keyboard focus goes back to the tab.
 */
export function NowDrawer({ open, onOpenChange, currentVillageId }: NowDrawerProps) {
  const t = useT();
  const now = useNow();
  // the tab's count follows the timers while the drawer is shut too
  useRefreshWhenNowEnds(now.data, now.dataUpdatedAt, now.errorUpdatedAt);
  const panelId = useId();
  const tabRef = useRef<HTMLButtonElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const restoreFocus = useRef(false);
  const count = now.data === undefined ? 0 : nowSections(now.data, 'all', currentVillageId).count;
  const attacks = now.data === undefined ? 0 : hostileCount(now.data);

  // the keyboard lands in the drawer when it opens, and back on the tab when it closes
  useEffect(() => {
    if (open) {
      headingRef.current?.focus({ preventScroll: true });
      const onKey = (event: KeyboardEvent) => {
        if (event.key === 'Escape') {
          restoreFocus.current = true;
          onOpenChange(false);
        }
      };
      document.addEventListener('keydown', onKey);
      return () => document.removeEventListener('keydown', onKey);
    }
    if (restoreFocus.current) {
      restoreFocus.current = false;
      tabRef.current?.focus({ preventScroll: true });
    }
  }, [open, onOpenChange]);

  const close = () => {
    restoreFocus.current = true;
    onOpenChange(false);
  };

  return (
    <>
      {!open && (
        <button
          ref={tabRef}
          type="button"
          className={attacks > 0 ? `${styles.tab} ${styles.tabWar}` : styles.tab}
          onClick={() => onOpenChange(true)}
          aria-expanded={false}
          aria-controls={panelId}
          aria-label={attacks > 0 ? `${t.now.tab(count)}, ${t.now.tabAttacks(attacks)}` : t.now.tab(count)}
        >
          {attacks > 0 && <i className={styles.dot} aria-hidden="true" />}
          <span className={styles.label} aria-hidden="true">
            {t.now.title}
          </span>
          <em className={styles.count} aria-hidden="true">
            {badgeText(count) ?? 0}
          </em>
        </button>
      )}
      {open && (
        <>
          <div className={styles.scrim} onClick={close} aria-hidden="true" />
          <aside id={panelId} className={styles.drawer} role="dialog" aria-label={t.now.title}>
            <NowList
              currentVillageId={currentVillageId}
              onNavigate={() => onOpenChange(false)}
              headingRef={headingRef}
              headingAction={
                <button
                  type="button"
                  className={styles.close}
                  onClick={close}
                  aria-label={t.now.close}
                  title={t.now.close}
                >
                  <Icon name="close" />
                </button>
              }
            />
          </aside>
        </>
      )}
    </>
  );
}

import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import { Icon, type IconName } from './Icon';
import { IconButton } from './IconButton';
import styles from './MoreMenu.module.css';

export interface MoreMenuItem {
  label: string;
  icon: IconName;
  onSelect: () => void;
  /** Red text: the item removes something. */
  danger?: boolean;
  disabled?: boolean;
}

interface MoreMenuProps {
  /** The button's accessible name and tooltip, e.g. "More for Tomburg". */
  label: string;
  items: MoreMenuItem[];
  /**
   * Which edge of the list lines up with the button's. Left: the list opens to the right (a "…" at the
   * left of its pane, a conversation's next to Profile). Without it: towards the middle of the window.
   */
  align?: 'left' | 'right';
}

/** One item (the touch target), the list's padding and border, the gap to the button. */
const ITEM_PX = 44;
const FRAME_PX = 12;
const GAP_PX = 4;

/** The least room the list keeps to the window's edge. */
const EDGE_PX = 8;

/**
 * A "…" button with the rarer actions of a list row (1.3), so the row keeps one visible button.
 * The list sits at a fixed place on the screen under the button (above it when there is no room
 * below), so a card's clipped edge can't cut it off, and follows the button in the tab order. A
 * choice, a tap elsewhere, Escape, a scroll or the focus moving on closes it; after a choice or
 * Escape the focus is back on the button.
 */
export function MoreMenu({ label, items, align }: MoreMenuProps) {
  const [place, setPlace] = useState<CSSProperties | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const listId = useId();
  const open = place !== null;

  useEffect(() => {
    if (!open) {
      return;
    }
    const close = () => setPlace(null);
    const onPointerDown = (event: PointerEvent) => {
      if (!(event.target instanceof Node) || ref.current?.contains(event.target) !== true) {
        close();
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        close();
        ref.current?.querySelector('button')?.focus();
      }
    };
    window.addEventListener('scroll', close, { capture: true, passive: true });
    window.addEventListener('resize', close);
    window.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', onPointerDown);
    return () => {
      window.removeEventListener('scroll', close, { capture: true });
      window.removeEventListener('resize', close);
      window.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPointerDown);
    };
  }, [open]);

  // A list that opens to the right of a "…" near the right edge (a long German label on a 320 px phone) ends
  // past the window: slide it back, so its rounded corners show (2.0 UI review follow-up)
  useLayoutEffect(() => {
    const menu = menuRef.current;
    if (menu === null || place === null || typeof place.left !== 'number') {
      return;
    }
    // from the list's width and the state's `left`, not from where it is drawn: the global reduced-motion
    // rule gives every property a transition, so for a moment the list is still at the old place
    const fits = Math.max(
      EDGE_PX,
      Math.floor(document.documentElement.clientWidth - EDGE_PX - menu.getBoundingClientRect().width),
    );
    if (fits < place.left - 0.5) {
      setPlace({ ...place, left: fits });
    }
  }, [place]);

  function toggle(button: HTMLElement) {
    if (open) {
      setPlace(null);
      return;
    }
    const rect = button.getBoundingClientRect();
    const viewport = document.documentElement;
    // the list opens towards the middle of the window: under a "…" at the right its right edge is the
    // button's, under one at the left (a conversation's, next to Profile) its left edge, so it is not
    // cut off by the window's edge
    const opensLeft = align === undefined ? (rect.left + rect.right) / 2 > viewport.clientWidth / 2 : align === 'right';
    const side: CSSProperties = opensLeft ? { right: viewport.clientWidth - rect.right } : { left: rect.left };
    const room = items.length * ITEM_PX + FRAME_PX + GAP_PX;
    setPlace(
      viewport.clientHeight - rect.bottom >= room
        ? { top: rect.bottom + GAP_PX, ...side }
        : { bottom: viewport.clientHeight - rect.top + GAP_PX, ...side },
    );
  }

  return (
    <div
      ref={ref}
      className={styles.wrap}
      onBlur={(event) => {
        // only when the focus goes somewhere else: a tap on a non-focusable spot is the pointerdown's
        // (and Safari doesn't focus buttons on a tap, so a null target would close before the click)
        const next = event.relatedTarget;
        if (open && next !== null && !event.currentTarget.contains(next)) {
          setPlace(null);
        }
      }}
    >
      <IconButton
        icon="more"
        label={label}
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        onClick={(event) => toggle(event.currentTarget)}
      />
      {open && (
        <div id={listId} ref={menuRef} className={styles.menu} style={place}>
          {items.map((item) => (
            <button
              key={item.label}
              type="button"
              className={item.danger === true ? `${styles.item} ${styles.danger}` : styles.item}
              disabled={item.disabled}
              onClick={() => {
                setPlace(null);
                // the item goes with the list: the focus goes back to "…" first, so a sheet the item
                // opens hands it back there when it closes, instead of to the page's start
                ref.current?.querySelector('button')?.focus();
                item.onSelect();
              }}
            >
              <Icon name={item.icon} />
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

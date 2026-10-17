import { useEffect, useId, useRef, useState, type CSSProperties } from 'react';
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
}

/** One item (the touch target), the list's padding and border, the gap to the button. */
const ITEM_PX = 44;
const FRAME_PX = 12;
const GAP_PX = 4;

/**
 * A "…" button with the rarer actions of a list row (1.3), so the row keeps one visible button.
 * The list sits at a fixed place on the screen under the button (above it when there is no room
 * below), so a card's clipped edge can't cut it off, and follows the button in the tab order. A
 * choice, a tap elsewhere, Escape, a scroll or the focus moving on closes it; after a choice or
 * Escape the focus is back on the button.
 */
export function MoreMenu({ label, items }: MoreMenuProps) {
  const [place, setPlace] = useState<CSSProperties | null>(null);
  const ref = useRef<HTMLDivElement>(null);
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

  function toggle(button: HTMLElement) {
    if (open) {
      setPlace(null);
      return;
    }
    const rect = button.getBoundingClientRect();
    const viewport = document.documentElement;
    const right = viewport.clientWidth - rect.right;
    const room = items.length * ITEM_PX + FRAME_PX + GAP_PX;
    setPlace(
      viewport.clientHeight - rect.bottom >= room
        ? { top: rect.bottom + GAP_PX, right }
        : { bottom: viewport.clientHeight - rect.top + GAP_PX, right },
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
        <div id={listId} className={styles.menu} style={place}>
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

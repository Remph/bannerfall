import { useEffect, useRef, type CSSProperties, type ReactNode, type RefObject } from 'react';
import styles from './FloatingMenu.module.css';

interface FloatingMenuProps {
  /** Where the menu hangs (menuPlaceUnder of the button); null: closed. */
  place: CSSProperties | null;
  /** A function that stays the same between renders (useCallback), or the menu's listeners restart with every render. */
  onClose: () => void;
  /** The button that opens it: the menu hangs under it, and a tap on it is the button's own (it toggles). */
  anchor: RefObject<HTMLElement | null>;
  /** The menu's accessible name, usually its title. */
  label: string;
  children: ReactNode;
}

/**
 * A small menu under the button that opened it, on the PC (2.0): the gear's settings and the village
 * switch, instead of a sheet in the middle of the window. Not modal: the page behind stays in sight.
 * A tap elsewhere, Escape, a resize or the focus moving on closes it; after Escape the focus is back
 * on the button. A link in it should call onClose too.
 */
export function FloatingMenu({ place, onClose, anchor, label, children }: FloatingMenuProps) {
  const ref = useRef<HTMLDivElement>(null);
  const open = place !== null;

  useEffect(() => {
    const button = anchor.current;
    if (!open || button === null) {
      return;
    }
    ref.current?.focus({ preventScroll: true });
    const onPointerDown = (event: PointerEvent) => {
      if (event.target instanceof Node && !ref.current?.contains(event.target) && !button.contains(event.target)) {
        onClose();
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
        button.focus();
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('keydown', onKey);
    window.addEventListener('resize', onClose);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('resize', onClose);
    };
  }, [open, anchor, onClose]);

  if (place === null) {
    return null;
  }
  return (
    <div
      ref={ref}
      role="dialog"
      aria-label={label}
      tabIndex={-1}
      className={styles.menu}
      style={place}
      onBlur={(event) => {
        // only when the focus goes to something else on the page (a tap on a spot that takes no focus is the pointerdown's)
        const next = event.relatedTarget;
        if (next instanceof Node && !event.currentTarget.contains(next) && !anchor.current?.contains(next)) {
          onClose();
        }
      }}
    >
      <h2 className={styles.title}>{label}</h2>
      {children}
    </div>
  );
}

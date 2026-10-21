import { useEffect, useId, useState, type ReactNode } from 'react';
import { useMediaQuery } from '../layout/useMediaQuery';
import { Icon } from './Icon';
import styles from './InfoRow.module.css';
import { Sheet } from './Sheet';

/** A mouse (or trackpad) that can hover. Phones and tablets don't match: they get the sheet only. */
const HOVER_QUERY = '(hover: hover) and (pointer: fine)';

/** Where the row is on screen when the popover opens; the popover is placed next to it. */
interface Anchor {
  top: number;
  bottom: number;
  left: number;
}

const POPOVER_WIDTH_PX = 360;
const MARGIN_PX = 8;
/** The gap between the row and the popover. */
const GAP_PX = 4;

interface InfoRowProps {
  /** The list item's id (a link can scroll to it). */
  id?: string;
  /** The sheet's title. */
  title: string;
  /** The info button's name for screen readers. */
  label: string;
  /** The row's class names (highlight, locked). */
  className: string | undefined;
  /** What the popover and the sheet show. */
  body: ReactNode;
  /**
   * The row's content; gets the info button to put into the row head, and a function that opens the
   * sheet for a second opener in the row (the noble's price factor chip, 1.6 UI review).
   */
  children: (infoButton: ReactNode, openSheet: () => void) => ReactNode;
}

/**
 * A list row with an info button (the Town Hall's upgrade preview, M24; the noble's price in the
 * Academy, 1.6). On a computer the info opens when the mouse is over the row or the keyboard focus is
 * on its info button (a popover that never takes the mouse: `pointer-events: none`, so it can't get
 * in the way of the buttons). The info button opens the same content as a sheet – the only way on a
 * phone, where nothing hovers.
 */
export function InfoRow({ id, title, label, className, body, children }: InfoRowProps) {
  const hoverCapable = useMediaQuery(HOVER_QUERY);
  const [hovered, setHovered] = useState<Anchor | null>(null);
  const [focused, setFocused] = useState<Anchor | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const popoverId = useId();
  const closeSheet = () => {
    setSheetOpen(false);
    // Closing the sheet gives the focus back to the info button, which would open the popover and
    // leave it open until the focus moves on: the player has just seen the same content.
    setFocused(null);
  };
  const anchor = hovered ?? focused;
  const popoverOpen = hoverCapable && anchor !== null && !sheetOpen;

  // The popover sits at a fixed place on the screen, so it must not outlive a scroll; Escape closes it.
  useEffect(() => {
    if (!popoverOpen) {
      return;
    }
    const close = () => {
      setHovered(null);
      setFocused(null);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        close();
      }
    };
    window.addEventListener('scroll', close, { capture: true, passive: true });
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('scroll', close, { capture: true });
      window.removeEventListener('keydown', onKey);
    };
  }, [popoverOpen]);

  const infoButton = (
    <button
      type="button"
      className={styles.infoButton}
      aria-label={label}
      aria-haspopup="dialog"
      aria-describedby={popoverOpen ? popoverId : undefined}
      onClick={() => setSheetOpen(true)}
      onFocus={(event) => setFocused(anchorOf(event.currentTarget.closest('li')))}
      onBlur={() => setFocused(null)}
    >
      <Icon name="info" />
    </button>
  );

  return (
    <li
      id={id}
      className={className}
      // mouse only: a tap on a phone also fires mouseenter and would leave the popover stuck open
      onPointerEnter={(event) => event.pointerType === 'mouse' && setHovered(anchorOf(event.currentTarget))}
      onPointerLeave={() => setHovered(null)}
    >
      {children(infoButton, () => setSheetOpen(true))}
      {popoverOpen && (
        <div id={popoverId} role="tooltip" className={styles.popover} ref={(node) => place(node, anchor)}>
          {body}
        </div>
      )}
      <Sheet open={sheetOpen} onClose={closeSheet} title={title}>
        {body}
      </Sheet>
    </li>
  );
}

function anchorOf(element: Element | null): Anchor | null {
  if (element === null) {
    return null;
  }
  const rect = element.getBoundingClientRect();
  return { top: rect.top, bottom: rect.bottom, left: rect.left };
}

/**
 * Puts the popover below the row, or above it when only that has room for its measured height (the
 * noble's info is taller than a building's preview); with room on neither side it is pushed up into
 * the window and covers the row, which it can do: the mouse goes through it. Placed on every render,
 * so a body that changes height while open stays inside the window.
 */
function place(node: HTMLDivElement | null, anchor: Anchor) {
  if (node === null) {
    return;
  }
  const width = Math.min(POPOVER_WIDTH_PX, window.innerWidth - 2 * MARGIN_PX);
  node.style.width = `${width}px`;
  node.style.left = `${Math.max(MARGIN_PX, Math.min(anchor.left + 16, window.innerWidth - width - MARGIN_PX))}px`;
  const height = node.offsetHeight;
  const below = anchor.bottom + GAP_PX;
  const above = anchor.top - GAP_PX - height;
  const top =
    below + height <= window.innerHeight - MARGIN_PX
      ? below
      : above >= MARGIN_PX
        ? above
        : Math.max(MARGIN_PX, window.innerHeight - MARGIN_PX - height);
  node.style.top = `${top}px`;
}

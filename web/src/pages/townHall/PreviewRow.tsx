import { useEffect, useId, useState, type CSSProperties, type ReactNode } from 'react';
import type { BuildingInfo } from '../../api/queries/buildings';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { useT } from '../../i18n';
import { useMediaQuery } from '../../layout/useMediaQuery';
import { UpgradePreviewBody, type PreviewContext } from './UpgradePreview';
import styles from './UpgradePreview.module.css';

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
/** Room the popover needs below the row; with less, it opens above the row. */
const POPOVER_HEIGHT_PX = 280;

interface RowProps {
  info: BuildingInfo;
  context: PreviewContext;
  /** The row's class names (highlight, locked). */
  className: string | undefined;
  /** The row's content; gets the info button to put into the row head. */
  children: (infoButton: ReactNode) => ReactNode;
}

/**
 * A building row of the Town Hall with the upgrade preview (M24). On a computer the preview opens
 * when the mouse is over the row or the keyboard focus is on its info button (a popover that never
 * takes the mouse: `pointer-events: none`, so it can't get in the way of the buttons). The info
 * button opens the same preview as a sheet – the only way on a phone, where nothing hovers.
 */
export function PreviewRow({ info, context, className, children }: RowProps) {
  const t = useT();
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

  const building = t.buildings[info.building];
  const label = t.townHall.preview.button(building);
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
      id={info.building}
      className={className}
      // mouse only: a tap on a phone also fires mouseenter and would leave the popover stuck open
      onPointerEnter={(event) => event.pointerType === 'mouse' && setHovered(anchorOf(event.currentTarget))}
      onPointerLeave={() => setHovered(null)}
    >
      {children(infoButton)}
      {popoverOpen && (
        <div id={popoverId} role="tooltip" className={styles.popover} style={popoverStyle(anchor)}>
          <UpgradePreviewBody info={info} context={context} />
        </div>
      )}
      <Sheet open={sheetOpen} onClose={closeSheet} title={building}>
        <UpgradePreviewBody info={info} context={context} />
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

/** Below the row, or above it when the screen has no room underneath; kept inside the window. */
function popoverStyle(anchor: Anchor): CSSProperties {
  const width = Math.min(POPOVER_WIDTH_PX, window.innerWidth - 2 * MARGIN_PX);
  const left = Math.max(MARGIN_PX, Math.min(anchor.left + 16, window.innerWidth - width - MARGIN_PX));
  const roomBelow = window.innerHeight - anchor.bottom;
  return roomBelow >= POPOVER_HEIGHT_PX || roomBelow >= anchor.top
    ? { width, left, top: anchor.bottom + 4 }
    : { width, left, bottom: window.innerHeight - anchor.top + 4 };
}

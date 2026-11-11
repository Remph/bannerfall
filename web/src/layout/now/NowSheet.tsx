import { useCallback, useEffect, useRef, type RefObject } from 'react';
import { useNow, useRefreshWhenNowEnds } from '../../api/queries/now';
import { useT } from '../../i18n';
import { markNowHintSeen } from './nowHint';
import { edgeAt, opensOnRelease, reveal, startsDrag } from './nowSwipe';
import { NowList } from './NowList';
import styles from './NowSheet.module.css';

interface NowSheetProps {
  /** The sky: a pull down on it opens the sheet, a pull up closes it. */
  headerRef: RefObject<HTMLElement | null>;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentVillageId: number | undefined;
}

/** The sheet's content rolls this far (rem) out from under the sky while it opens. */
const ROLL_REM = 2.6;

/**
 * Phones (2.0): the Now list as the sky's own land pulled down. A pull on the sky opens it – the
 * edge follows the finger, a small lip rides along it, the rows roll out from under the sky – and a
 * pull up on the sky or the lip, a tap on the dimmed page, Esc or the attack alarm closes it again.
 * The edge is set directly while dragging (no re-render per move); `open` follows when the finger
 * lets go. The "More" menu has a row for it, for anyone who doesn't swipe.
 */
export function NowSheet({ headerRef, open, onOpenChange, currentVillageId }: NowSheetProps) {
  const t = useT();
  // the More tile's count follows the timers while the sheet is shut
  const now = useNow();
  useRefreshWhenNowEnds(now.data, now.dataUpdatedAt, now.errorUpdatedAt);
  const liveRef = useRef<HTMLDivElement>(null);
  const clipRef = useRef<HTMLDivElement>(null);
  const innerRef = useRef<HTMLDivElement>(null);
  const lipRef = useRef<HTMLButtonElement>(null);
  const scrimRef = useRef<HTMLDivElement>(null);
  const shadeRef = useRef<HTMLDivElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  // the sheet's full height and whether it is settled open, for the gesture (no state: no re-render)
  const geometry = useRef({ height: 0, open: false });

  /** Puts the edge `y` px below the sky: everything that depends on it follows. */
  const place = useCallback((y: number, animate: boolean) => {
    const live = liveRef.current;
    const clip = clipRef.current;
    const inner = innerRef.current;
    const lip = lipRef.current;
    const scrim = scrimRef.current;
    const shade = shadeRef.current;
    if (!live || !clip || !inner || !lip || !scrim || !shade) {
      return;
    }
    const { height } = geometry.current;
    const progress = reveal(y, height);
    for (const element of [live, scrim, shade]) {
      element.dataset.animate = animate ? 'true' : 'false';
      element.style.visibility = 'visible';
    }
    clip.style.clipPath = `inset(0 0 ${Math.max(0, height - y)}px 0)`;
    inner.style.transform = `translateY(${-(1 - progress) * ROLL_REM}rem)`;
    inner.style.opacity = String(0.25 + 0.75 * progress);
    lip.style.top = `${Math.max(0, y) - 1}px`;
    lip.style.opacity = y > 4 ? '1' : '0';
    scrim.style.opacity = String(progress);
    shade.style.opacity = String(Math.min(1, progress * 3));
  }, []);

  /** Lets the sheet settle open or shut (animated). */
  const settle = useCallback(
    (toOpen: boolean) => {
      const clip = clipRef.current;
      if (!clip) {
        return;
      }
      if (toOpen) {
        geometry.current.height = clip.offsetHeight;
        geometry.current.open = true;
        place(geometry.current.height, true);
        return;
      }
      geometry.current.open = false;
      place(0, true);
      // after the roll-up nothing of it stays reachable (focus, screen readers)
      window.setTimeout(() => {
        if (!geometry.current.open) {
          for (const element of [liveRef.current, scrimRef.current, shadeRef.current]) {
            if (element) {
              element.style.visibility = 'hidden';
            }
          }
        }
      }, 320);
    },
    [place],
  );

  // the state drives the sheet (the More row, the alarm, a route change), the gesture settles it itself
  const everOpened = useRef(false);
  useEffect(() => {
    if (!open && !everOpened.current) {
      return; // nothing to put away yet
    }
    everOpened.current = true;
    settle(open);
    if (open) {
      markNowHintSeen();
      headingRef.current?.focus({ preventScroll: true });
    }
  }, [open, settle]);

  // the list grows or shrinks while the sheet is open: keep the edge at its bottom
  useEffect(() => {
    const clip = clipRef.current;
    if (!clip || !open || typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver(() => {
      if (geometry.current.open) {
        geometry.current.height = clip.offsetHeight;
        place(geometry.current.height, false);
      }
    });
    observer.observe(clip);
    return () => observer.disconnect();
  }, [open, place]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onOpenChange(false);
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onOpenChange]);

  // the gesture on the sky (touch and pen; a mouse has the drawer or the column)
  useEffect(() => {
    const header = headerRef.current;
    if (!header) {
      return;
    }
    let drag: {
      id: number;
      x: number;
      y: number;
      on: boolean;
      base: number;
      last: number;
      lastAt: number;
      v: number;
    } | null = null;

    const down = (event: PointerEvent) => {
      if (event.pointerType === 'mouse') {
        return;
      }
      drag = {
        id: event.pointerId,
        x: event.clientX,
        y: event.clientY,
        on: false,
        base: 0,
        last: event.clientY,
        lastAt: performance.now(),
        v: 0,
      };
    };
    const move = (event: PointerEvent) => {
      if (!drag || event.pointerId !== drag.id) {
        return;
      }
      const dy = event.clientY - drag.y;
      if (!drag.on) {
        if (!startsDrag(event.clientX - drag.x, dy, geometry.current.open)) {
          return;
        }
        drag.on = true;
        const clip = clipRef.current;
        if (clip) {
          geometry.current.height = clip.offsetHeight;
        }
        drag.base = geometry.current.open ? geometry.current.height : 0;
        // the dead zone is not part of the pull: the edge starts under the finger
        drag.y = event.clientY;
        header.setPointerCapture(event.pointerId);
      }
      const at = performance.now();
      drag.v = (event.clientY - drag.last) / Math.max(1, at - drag.lastAt);
      drag.last = event.clientY;
      drag.lastAt = at;
      place(edgeAt(drag.base, event.clientY - drag.y, geometry.current.height), false);
    };
    const up = (event: PointerEvent) => {
      if (!drag || event.pointerId !== drag.id) {
        return;
      }
      const finished = drag;
      drag = null;
      if (!finished.on) {
        return;
      }
      // the finger rested before it let go: no flick
      const velocity = performance.now() - finished.lastAt > 80 ? 0 : finished.v;
      // the drag ends in a click on whatever lay under the finger: eat that one
      const eat = (click: Event) => {
        click.stopPropagation();
        click.preventDefault();
      };
      header.addEventListener('click', eat, true);
      window.setTimeout(() => header.removeEventListener('click', eat, true), 60);
      const { height } = geometry.current;
      const edge = edgeAt(finished.base, event.clientY - finished.y, height);
      const next = opensOnRelease(finished.base > 0, edge, height, velocity);
      settle(next);
      onOpenChange(next);
    };
    header.addEventListener('pointerdown', down);
    header.addEventListener('pointermove', move);
    header.addEventListener('pointerup', up);
    header.addEventListener('pointercancel', up);
    return () => {
      header.removeEventListener('pointerdown', down);
      header.removeEventListener('pointermove', move);
      header.removeEventListener('pointerup', up);
      header.removeEventListener('pointercancel', up);
    };
  }, [headerRef, place, settle, onOpenChange]);

  // the lip is a handle too: drag it up to close, tap it to close
  const lipDrag = useRef<number | null>(null);
  const lipDown = (event: React.PointerEvent<HTMLButtonElement>) => {
    lipDrag.current = event.clientY;
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const lipMove = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (lipDrag.current !== null && geometry.current.open) {
      const { height } = geometry.current;
      place(edgeAt(height, event.clientY - lipDrag.current, height), false);
    }
  };
  const lipUp = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (lipDrag.current === null) {
      return;
    }
    const moved = event.clientY - lipDrag.current;
    lipDrag.current = null;
    const { height } = geometry.current;
    if (moved < -height * 0.2 || Math.abs(moved) < 6) {
      settle(false);
      onOpenChange(false);
    } else {
      settle(true);
    }
  };

  return (
    <>
      <div ref={scrimRef} className={styles.scrim} onClick={() => onOpenChange(false)} aria-hidden="true" />
      <div ref={liveRef} className={styles.live} role="dialog" aria-label={t.now.title} aria-modal="false">
        <div ref={clipRef} className={styles.clip}>
          <div ref={innerRef} className={styles.inner}>
            <NowList
              currentVillageId={currentVillageId}
              onNavigate={() => onOpenChange(false)}
              headingRef={headingRef}
            />
          </div>
        </div>
        <button
          ref={lipRef}
          type="button"
          className={styles.lip}
          aria-label={t.now.lipLabel}
          onPointerDown={lipDown}
          onPointerMove={lipMove}
          onPointerUp={lipUp}
          onPointerCancel={lipUp}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              onOpenChange(false);
            }
          }}
        >
          <span />
        </button>
      </div>
      <div ref={shadeRef} className={styles.shade} aria-hidden="true" />
    </>
  );
}

import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { useT } from '../i18n';
import { Icon } from './Icon';
import styles from './Sheet.module.css';

interface SheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  /** Fills the phone's screen, its content takes the full height (the chat, M9b). */
  fullScreen?: boolean;
  children: ReactNode;
}

/**
 * A modal panel: slides up from the bottom on phones, centred on wider screens. Built on the native
 * <dialog> element, which brings the dimmed backdrop, Escape to close and keeps keyboard focus inside.
 * Closing mirrors opening (1.3): it slides down (fades on wider screens) with what it showed, then
 * the dialog closes.
 */
export function Sheet({ open, onClose, title, fullScreen = false, children }: SheetProps) {
  const t = useT();
  const titleId = useId();
  // useRef gives us the real DOM element after React has rendered it (ref.current).
  const ref = useRef<HTMLDialogElement>(null);
  // What the sheet showed while open: the parent usually drops the content together with `open`
  // (`{confirming !== null && …}`), but the closing sheet still shows it.
  const [shown, setShown] = useState<{ title: string; children: ReactNode } | null>(null);
  if (open && (shown === null || shown.title !== title || shown.children !== children)) {
    setShown({ title, children });
  }

  // showModal()/close() can't be expressed as JSX, so an effect keeps the dialog in sync with `open`.
  useEffect(() => {
    const dialog = ref.current;
    if (dialog === null) {
      return;
    }
    if (open) {
      // opened again while closing: stay
      dialog.classList.remove(closingClass);
      if (!dialog.open) {
        dialog.showModal();
      }
    } else if (dialog.open) {
      closeAnimated(dialog);
    }
  }, [open]);

  return (
    <dialog
      ref={ref}
      className={fullScreen ? `${styles.sheet} ${styles.full}` : styles.sheet}
      aria-labelledby={titleId}
      // fires for Escape and dialog.close() alike; the closed sheet lets its content go. React
      // passes a `close` up the component tree although the DOM event doesn't bubble: a sheet
      // inside a sheet (the code sheet in the plan editor) must not close the sheet around it.
      onClose={(event) => {
        if (event.target !== event.currentTarget) {
          return;
        }
        setShown(null);
        onClose();
      }}
      // a click on the dialog element itself (not its content) is a click on the backdrop
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div className={styles.content}>
        <header className={styles.header}>
          <h2 id={titleId} className={styles.title}>
            {open ? title : (shown?.title ?? title)}
          </h2>
          <button type="button" className={styles.close} onClick={onClose} aria-label={t.common.close}>
            <Icon name="close" />
          </button>
        </header>
        {/* Content only while open (and closing): nothing hidden keeps re-rendering (e.g. a ticking clock). */}
        <div className={styles.body}>{open ? children : shown?.children}</div>
      </div>
    </dialog>
  );
}

const closingClass = styles.closing ?? '';

/**
 * Plays the closing animation, then closes the dialog. Without one (reduced motion, a browser or test
 * environment without the Web Animations API) it closes at once; opened again meanwhile, it stays.
 */
function closeAnimated(dialog: HTMLDialogElement): void {
  dialog.classList.add(closingClass);
  const running = typeof dialog.getAnimations === 'function' ? dialog.getAnimations() : [];
  if (running.length === 0) {
    dialog.classList.remove(closingClass);
    dialog.close();
    return;
  }
  void Promise.all(running.map((animation) => animation.finished)).then(
    () => {
      if (dialog.classList.contains(closingClass)) {
        dialog.classList.remove(closingClass);
        dialog.close();
      }
    },
    // cancelled: the sheet was opened again
    () => undefined,
  );
}

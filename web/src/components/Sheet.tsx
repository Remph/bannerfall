import { useEffect, useId, useRef, type ReactNode } from 'react';
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
 */
export function Sheet({ open, onClose, title, fullScreen = false, children }: SheetProps) {
  const t = useT();
  const titleId = useId();
  // useRef gives us the real DOM element after React has rendered it (ref.current).
  const ref = useRef<HTMLDialogElement>(null);

  // showModal()/close() can't be expressed as JSX, so an effect keeps the dialog in sync with `open`.
  useEffect(() => {
    const dialog = ref.current;
    if (dialog === null) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={ref}
      className={fullScreen ? `${styles.sheet} ${styles.full}` : styles.sheet}
      aria-labelledby={titleId}
      // fires for Escape, our close button and dialog.close() alike
      onClose={onClose}
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
            {title}
          </h2>
          <button type="button" className={styles.close} onClick={onClose} aria-label={t.common.close}>
            <Icon name="close" />
          </button>
        </header>
        {/* Content only while open: nothing hidden keeps re-rendering (e.g. a ticking clock). */}
        <div className={styles.body}>{open && children}</div>
      </div>
    </dialog>
  );
}

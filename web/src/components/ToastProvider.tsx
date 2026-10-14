import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { useT } from '../i18n';
import { Icon } from './Icon';
import styles from './ToastProvider.module.css';
import { MAX_TOASTS, ToastContext, toastDurationMs, type ShowToast, type ToastContent } from './toasts';

interface Toast extends ToastContent {
  id: number;
}

/**
 * Toasts: short messages at the top of the screen (live notifications, M9b). They go away by
 * themselves, with their close button, or when tapped (which also opens their link). Screen readers
 * hear them through the `role="status"` list.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const t = useT();
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: number) => {
    clearTimeout(timers.current.get(id));
    timers.current.delete(id);
    setToasts((shown) => shown.filter((toast) => toast.id !== id));
  }, []);

  const show = useCallback<ShowToast>(
    (content) => {
      const id = nextId.current++;
      setToasts((shown) => [...shown, { ...content, id }].slice(-MAX_TOASTS));
      timers.current.set(
        id,
        setTimeout(() => dismiss(id), toastDurationMs(content)),
      );
    },
    [dismiss],
  );

  // no timer outlives the provider (logout)
  useEffect(() => {
    const running = timers.current;
    return () => running.forEach((timer) => clearTimeout(timer));
  }, []);

  return (
    <ToastContext value={show}>
      {children}
      <ol className={styles.toasts} role="status" aria-label={t.realtime.notifications}>
        {toasts.map((toast) => (
          <li key={toast.id} className={`${styles.toast} ${styles[toast.tone]}`}>
            {toast.to === undefined ? (
              <span className={styles.text}>{toast.text}</span>
            ) : (
              <Link className={styles.text} to={toast.to} onClick={() => dismiss(toast.id)}>
                {toast.text}
              </Link>
            )}
            <button
              type="button"
              className={styles.close}
              onClick={() => dismiss(toast.id)}
              aria-label={t.realtime.dismiss}
            >
              <Icon name="close" />
            </button>
          </li>
        ))}
      </ol>
    </ToastContext>
  );
}

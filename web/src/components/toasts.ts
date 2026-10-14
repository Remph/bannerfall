import { createContext, useContext } from 'react';

export type ToastTone = 'info' | 'success' | 'danger';

/** A short message at the edge of the screen; tapping it opens `to` when there is one. */
export interface ToastContent {
  text: string;
  tone: ToastTone;
  to?: { pathname: string; hash?: string };
}

/** Shows a toast for a few seconds (05-frontend §7); filled by <ToastProvider>. */
export type ShowToast = (toast: ToastContent) => void;

export const ToastContext = createContext<ShowToast | null>(null);

export function useShowToast(): ShowToast {
  const show = useContext(ToastContext);
  if (show === null) {
    throw new Error('useShowToast() must be used inside <ToastProvider>');
  }
  return show;
}

/** How long a toast stays: attacks longer, they matter most. */
export function toastDurationMs(toast: ToastContent): number {
  return toast.tone === 'danger' ? 12_000 : 6_000;
}

/** At most this many at once; a new one pushes the oldest out. */
export const MAX_TOASTS = 4;

import { useEffect, useRef, useState } from 'react';
import { useBroadcasts } from '../api/queries/broadcast';
import { Button } from '../components/Button';
import { Sheet } from '../components/Sheet';
import { newestBroadcastId, unseenBroadcasts } from '../game/broadcast';
import { useT } from '../i18n';
import styles from './BroadcastPopup.module.css';

const STORAGE_KEY = 'bannerfall.broadcastSeen';

/** The newest broadcast dismissed on this device; null when none (or the storage is blocked). */
function loadSeenId(): number | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value !== null && /^\d+$/.test(value) ? Number(value) : null;
  } catch {
    return null;
  }
}

function storeSeenId(id: number): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(id));
  } catch {
    // blocked (private mode): the popup stays dismissed until the page is loaded again
  }
}

/**
 * The host's messages (1.7): a sheet over any page with the texts and one OK button. A broadcast
 * that arrives while it is open joins the others in it. Dismissing remembers the newest one on this
 * device, so a reload or a reconnect within the ten minutes the server keeps them does not show it
 * again.
 */
export function BroadcastPopup() {
  const t = useT();
  const broadcasts = useBroadcasts();
  const [seenId, setSeenId] = useState(loadSeenId);
  const unseen = unseenBroadcasts(broadcasts.data ?? [], seenId);
  const buttons = useRef<HTMLDivElement>(null);
  const showing = unseen.length > 0;

  // The popup opens by itself, not from a click: the dialog would focus its close button and draw
  // the keyboard ring around it. The button that answers the popup gets the focus instead.
  useEffect(() => {
    if (showing) {
      buttons.current?.querySelector('button')?.focus();
    }
  }, [showing]);

  const dismiss = () => {
    const newest = newestBroadcastId(unseen);
    if (newest !== null) {
      storeSeenId(newest);
      setSeenId(newest);
    }
  };

  return (
    <Sheet open={showing} onClose={dismiss} title={t.broadcast.title}>
      <div className={styles.popup}>
        {unseen.map((broadcast) => (
          <p key={broadcast.id} className={styles.text}>
            {broadcast.text}
          </p>
        ))}
        <div ref={buttons} className={styles.buttons}>
          <Button type="button" onClick={dismiss}>
            {t.broadcast.ok}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}

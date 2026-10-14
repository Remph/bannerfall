import { useId, useState } from 'react';
import { Icon } from '../../components/Icon';
import { badgeText } from '../../game/reports';
import { useT } from '../../i18n';
import { useChatUnread } from '../../realtime/chatSeen';
import { ChatPanel } from './ChatPanel';
import styles from './ChatDock.module.css';
import { loadChatDockOpen, saveChatDockOpen } from './chatDockStorage';

/**
 * Desktop (≥ 1024 px): the world chat as a floating panel at the bottom right, minimised to a
 * button with the unread count (05-frontend §8). The choice is remembered.
 * `onMap`: on the map it starts minimised, so it doesn't cover the side column (M11b review);
 * opening or minimising it there isn't remembered, the other pages keep the stored choice.
 */
export function ChatDock({ onMap = false }: { onMap?: boolean }) {
  const t = useT();
  const [open, setOpenState] = useState(() => !onMap && loadChatDockOpen());
  // Entering or leaving the map: adjust while rendering (react.dev "Storing information from previous renders").
  const [wasOnMap, setWasOnMap] = useState(onMap);
  if (onMap !== wasOnMap) {
    setWasOnMap(onMap);
    setOpenState(!onMap && loadChatDockOpen());
  }
  const unread = useChatUnread();
  const badge = badgeText(unread);
  const panelId = useId();
  const titleId = useId();

  function setOpen(next: boolean) {
    setOpenState(next);
    if (!onMap) {
      saveChatDockOpen(next);
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        className={styles.launcher}
        onClick={() => setOpen(true)}
        aria-expanded={false}
        title={t.chat.open}
      >
        <Icon name="chat" />
        <span>{t.nav.chat}</span>
        {badge !== undefined && (
          <span className={styles.badge}>
            <span aria-hidden="true">{badge}</span>
            <span className="sr-only">, {t.chat.unreadBadge(unread)}</span>
          </span>
        )}
      </button>
    );
  }

  return (
    <section id={panelId} className={styles.dock} aria-labelledby={titleId}>
      <header className={styles.header}>
        <Icon name="chat" />
        <h2 id={titleId} className={styles.title}>
          {t.chat.title}
        </h2>
        <button
          type="button"
          className={styles.minimize}
          onClick={() => setOpen(false)}
          aria-expanded={true}
          aria-controls={panelId}
          aria-label={t.chat.minimize}
          title={t.chat.minimize}
        >
          <Icon name="chevronDown" />
        </button>
      </header>
      <ChatPanel />
    </section>
  );
}

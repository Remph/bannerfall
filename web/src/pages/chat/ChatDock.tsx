import { useId, type RefObject } from 'react';
import { Icon } from '../../components/Icon';
import { badgeText } from '../../game/reports';
import { useT } from '../../i18n';
import { CHAT_COLUMN_QUERY, useMediaQuery } from '../../layout/useMediaQuery';
import { useChatUnread } from '../../realtime/chatSeen';
import { ChatPanel } from './ChatPanel';
import styles from './ChatDock.module.css';
import { useChatDock } from './useChatDock';

interface ChatDockProps {
  onMap?: boolean;
  /**
   * From `useChatDock`, when the frame holds the state: the game's rail has a Chat entry that opens
   * and closes the panel (2.0), so there is no button here. Without them (the pre-game frame, which
   * has no rail) the dock keeps its own state and minimises to a button with the unread count.
   */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  /** Where the keyboard focus goes when the panel is minimised (the rail's Chat entry), so it isn't lost. */
  focusAfterMinimise?: RefObject<HTMLElement | null>;
  /** The right column has the Now list above it (2.0): the chat takes the lower part of the strip. */
  belowNow?: boolean;
  /** The Now tab sits at the right edge (2.0): the floating panel keeps clear of it. */
  besideNowTab?: boolean;
}

/**
 * Desktop (≥ 1024 px): the world chat as a floating panel at the bottom right (05-frontend §8).
 * The choice is remembered. From 1600 px the open chat is a column at the right edge below the sky
 * instead, and the page moves left (1.3); not on the map, where it floats over the side column.
 */
export function ChatDock({
  onMap = false,
  open: givenOpen,
  onOpenChange,
  focusAfterMinimise,
  belowNow = false,
  besideNowTab = false,
}: ChatDockProps) {
  const t = useT();
  const [ownOpen, setOwnOpen] = useChatDock(onMap);
  const framed = givenOpen !== undefined && onOpenChange !== undefined;
  const open = framed ? givenOpen : ownOpen;
  const setOpen = framed ? onOpenChange : setOwnOpen;
  const unread = useChatUnread();
  const badge = badgeText(unread);
  const panelId = useId();
  const titleId = useId();
  // not on the map: there it would take the map's width (it floats over the side column instead)
  const column = useMediaQuery(CHAT_COLUMN_QUERY) && !onMap;

  if (!open && framed) {
    return null;
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
    <section
      id={panelId}
      className={[
        styles.dock,
        column && styles.column,
        column && belowNow && styles.belowNow,
        !column && besideNowTab && styles.besideNowTab,
      ]
        .filter(Boolean)
        .join(' ')}
      aria-labelledby={titleId}
      // AppLayout's CSS makes room for it
      data-chat-column={column || undefined}
    >
      <header className={styles.header}>
        <Icon name="chat" />
        <h2 id={titleId} className={styles.title}>
          {t.chat.title}
        </h2>
        <button
          type="button"
          className={styles.minimize}
          onClick={() => {
            // focus first: the button that has it is about to leave the page
            focusAfterMinimise?.current?.focus();
            setOpen(false);
          }}
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

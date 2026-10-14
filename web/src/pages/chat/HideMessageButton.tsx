import { useEffect, useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useHideChatMessage } from '../../api/queries/admin';
import { useHideGuildChatMessage, type ChatChannel } from '../../api/queries/chat';
import { useT } from '../../i18n';
import styles from './ChatPanel.module.css';

/** How long the link waits for the second tap. */
const ARMED_MS = 4000;

/**
 * Admin (world chat) or guild leader (guild chat) only: hides a message with two taps, like
 * deleting a report. The first turns the link into
 * "Really hide?", the second hides; without it, it turns back after a few seconds. Hiding can't be
 * undone on the page, so a stray tap alone does nothing.
 */
export function HideMessageButton({ messageId, channel }: { messageId: number; channel: ChatChannel }) {
  const t = useT();
  const hideInWorld = useHideChatMessage();
  const hideInGuild = useHideGuildChatMessage();
  const hide = channel === 'guild' ? hideInGuild : hideInWorld;
  const [armed, setArmed] = useState(false);

  useEffect(() => {
    if (!armed) {
      return;
    }
    const timer = setTimeout(() => setArmed(false), ARMED_MS);
    return () => clearTimeout(timer);
  }, [armed]);

  function onClick() {
    if (!armed) {
      setArmed(true);
      return;
    }
    setArmed(false);
    hide.mutate(messageId);
  }

  return (
    <>
      <button
        type="button"
        className={armed ? `${styles.hide} ${styles.hideArmed}` : styles.hide}
        onClick={onClick}
        // a mouse or keyboard user who moves on doesn't leave it armed
        onBlur={() => setArmed(false)}
        disabled={hide.isPending}
      >
        {armed ? t.chat.reallyHide : t.chat.hide}
      </button>
      {hide.isError && <span className={styles.error}> {errorMessage(t, hide.error)}</span>}
    </>
  );
}

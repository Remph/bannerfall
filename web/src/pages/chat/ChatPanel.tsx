import { useEffect, useId, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useCurrentAccount } from '../../api/queries/auth';
import { useMyGuild } from '../../api/queries/guilds';
import {
  useChat,
  useLoadOlderChat,
  useLoadOlderGuildChat,
  useGuildChat,
  useGuildChatId,
  type ChatChannel,
  type ChatMessage,
} from '../../api/queries/chat';
import { ApiError, errorMessage } from '../../api/errors';
import { Button } from '../../components/Button';
import { Icon } from '../../components/Icon';
import { SegmentedControl } from '../../components/SegmentedControl';
import {
  CHAT_LENGTH_HINT_FROM,
  CHAT_MAX_LENGTH,
  chatLength,
  clampChatText,
  isNearBottom,
  newestId,
  olderPageBefore,
  startsGroup,
} from '../../game/chat';
import { formatRecent } from '../../game/format';
import { badgeText } from '../../game/reports';
import { serverNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import { markChatSeen, useChannelUnread } from '../../realtime/chatSeen';
import { useRealtime } from '../../realtime/context';
import styles from './ChatPanel.module.css';
import { HideMessageButton } from './HideMessageButton';

/**
 * The chat (M9b): the history with live messages and the field to write. With a guild (and the
 * guilds switched on) a switch above it chooses between the world chat and the guild's own (M15b).
 * Rendered while it is visible (the phone's full-screen sheet, the desktop panel when not
 * minimised), so whatever it shows counts as read.
 */
export function ChatPanel() {
  const { t } = useI18n();
  const hasGuildChat = useGuildChatId() !== undefined;
  const [chosen, setChosen] = useState<ChatChannel>('world');
  // a player who left his guild while the guild tab was open lands in the world chat
  const channel: ChatChannel = hasGuildChat ? chosen : 'world';
  const worldUnread = useChannelUnread('world');
  const guildUnread = useChannelUnread('guild');

  const tabLabel = (name: string, unread: number) => (unread > 0 ? `${name} (${badgeText(unread)})` : name);

  return (
    <div className={styles.panel}>
      {hasGuildChat && (
        <div className={styles.channels}>
          <SegmentedControl
            label={t.chat.channels}
            options={[
              { value: 'world', label: tabLabel(t.chat.world, channel === 'world' ? 0 : worldUnread) },
              { value: 'guild', label: tabLabel(t.chat.guild, channel === 'guild' ? 0 : guildUnread) },
            ]}
            value={channel}
            onChange={setChosen}
          />
        </div>
      )}
      {/* keyed: the text being written stays with its channel */}
      <ChannelView key={channel} channel={channel} />
    </div>
  );
}

function ChannelView({ channel }: { channel: ChatChannel }) {
  const { t } = useI18n();
  const me = useCurrentAccount();
  const worldChat = useChat();
  const guildChat = useGuildChat();
  const guildId = useGuildChatId();
  const chat = channel === 'guild' ? guildChat : worldChat;
  const messages = chat.data?.messages;
  const newest = messages === undefined ? 0 : newestId(messages);
  const seenIn = channel === 'guild' ? guildId : undefined;

  // everything shown is read (the badges go to 0, and stay there while new messages come in)
  useEffect(() => {
    if (newest > 0 && (channel === 'world' || seenIn !== undefined)) {
      markChatSeen(me.playerId, newest, seenIn);
    }
  }, [me.playerId, newest, seenIn, channel]);

  return (
    <>
      {chat.isError ? (
        <div className={styles.status}>
          <p>{errorMessage(t, chat.error)}</p>
          <Button type="button" variant="secondary" onClick={() => void chat.refetch()}>
            {t.common.retry}
          </Button>
        </div>
      ) : messages === undefined ? (
        <p className={styles.status}>{t.common.loading}</p>
      ) : (
        <MessageList channel={channel} messages={messages} canLoadOlder={olderPageBefore(chat.data!) !== undefined} />
      )}
      <Composer channel={channel} />
    </>
  );
}

function MessageList({
  channel,
  messages,
  canLoadOlder,
}: {
  channel: ChatChannel;
  messages: ChatMessage[];
  canLoadOlder: boolean;
}) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const myGuild = useMyGuild();
  const loadOlderInWorld = useLoadOlderChat();
  const loadOlderInGuild = useLoadOlderGuildChat();
  const loadOlder = channel === 'guild' ? loadOlderInGuild : loadOlderInWorld;
  // the host hides in the world chat, the leader in the guild's own
  const canHide = channel === 'guild' ? myGuild.data?.role === 'LEADER' : me.role === 'ADMIN';
  const listRef = useRef<HTMLDivElement>(null);
  // where the reader is, measured from the end: new messages only scroll along when he is at the
  // end, and older ones put in front must not move what he is looking at
  const fromBottom = useRef(0);
  const shownEnds = useRef<{ first: number; last: number } | null>(null);
  const [unseenBelow, setUnseenBelow] = useState(false);
  const now = serverNow();

  const first = messages[0]?.id ?? 0;
  const last = newestId(messages);
  const lastIsMine = messages[messages.length - 1]?.playerId === me.playerId;

  // before the browser paints: no visible jump
  useLayoutEffect(() => {
    const list = listRef.current;
    if (list === null) {
      return;
    }
    const before = shownEnds.current;
    shownEnds.current = { first, last };
    const atEnd = fromBottom.current <= list.clientHeight + 48;
    if (before === null || (last !== before.last && (atEnd || lastIsMine))) {
      // first render, or a new message while at the end (or my own): to the end
      list.scrollTop = list.scrollHeight;
    } else if (last !== before.last) {
      setUnseenBelow(true);
    } else if (first !== before.first) {
      // older messages in front: keep the same distance from the end
      list.scrollTop = list.scrollHeight - fromBottom.current;
    }
    fromBottom.current = list.scrollHeight - list.scrollTop;
  }, [first, last, lastIsMine]);

  function onScroll() {
    const list = listRef.current;
    if (list === null) {
      return;
    }
    fromBottom.current = list.scrollHeight - list.scrollTop;
    if (isNearBottom(list.scrollTop, list.clientHeight, list.scrollHeight)) {
      setUnseenBelow(false);
    }
  }

  function toEnd() {
    const list = listRef.current;
    if (list !== null) {
      list.scrollTop = list.scrollHeight;
    }
    setUnseenBelow(false);
  }

  return (
    <div className={styles.listWrap}>
      <div ref={listRef} className={styles.list} onScroll={onScroll} role="log" aria-label={t.chat.messages}>
        {canLoadOlder ? (
          <div className={styles.older}>
            <Button
              type="button"
              variant="secondary"
              size="small"
              disabled={loadOlder.isPending}
              onClick={() => loadOlder.mutate()}
            >
              {loadOlder.isPending ? t.common.loading : t.chat.loadOlder}
            </Button>
            {loadOlder.isError && <p className={styles.error}>{errorMessage(t, loadOlder.error)}</p>}
          </div>
        ) : (
          messages.length > 0 && <p className={styles.start}>{t.chat.start}</p>
        )}
        {messages.length === 0 && <p className={styles.empty}>{t.chat.empty}</p>}
        <ol className={styles.messages}>
          {messages.map((message, index) => {
            const mine = message.playerId === me.playerId;
            const broadcast = message.playerId === null;
            const header = startsGroup(messages[index - 1], message);
            const name = message.playerName ?? t.chat.host;
            return (
              <li
                key={message.id}
                className={[
                  styles.message,
                  header && styles.groupStart,
                  mine && styles.mine,
                  broadcast && styles.broadcast,
                ]
                  .filter(Boolean)
                  .join(' ')}
              >
                {header ? (
                  <p className={styles.meta}>
                    <span className={styles.name}>{name}</span>{' '}
                    <time dateTime={message.createdAt}>
                      {formatRecent(Date.parse(message.createdAt), now, language)}
                    </time>
                  </p>
                ) : (
                  // screen readers still hear who wrote each message
                  <span className="sr-only">{name}: </span>
                )}
                <p className={styles.text}>{message.text}</p>
                {canHide && <HideMessageButton messageId={message.id} channel={channel} />}
              </li>
            );
          })}
        </ol>
      </div>
      {unseenBelow && (
        <button type="button" className={styles.newMessages} onClick={toEnd}>
          {t.chat.newMessages}
          <Icon name="chevronDown" />
        </button>
      )}
    </div>
  );
}

function Composer({ channel }: { channel: ChatChannel }) {
  const { t } = useI18n();
  const { connected, sendChat, sendGuildChat, onChatError } = useRealtime();
  const [text, setText] = useState('');
  const [error, setError] = useState<string | undefined>();
  const lastSent = useRef('');
  const messageId = useId();

  // a refused message (too fast, too long) comes back on its own queue: say why and give the text back
  useEffect(
    () =>
      onChatError((refused) => {
        setError(errorMessage(t, new ApiError(0, refused.code, refused.detail)));
        setText((current) => (current === '' ? lastSent.current : current));
      }),
    [onChatError, t],
  );

  const trimmed = text.trim();
  const left = CHAT_MAX_LENGTH - chatLength(text);

  function send(event?: FormEvent) {
    event?.preventDefault();
    if (trimmed === '' || !(channel === 'guild' ? sendGuildChat(trimmed) : sendChat(trimmed))) {
      return; // nothing to send, or not connected: the text stays
    }
    lastSent.current = trimmed;
    setText('');
    setError(undefined);
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends, Shift+Enter is a new line; not while an input method is composing a character
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      send();
    }
  }

  const message =
    error ??
    (!connected
      ? t.chat.offline
      : left <= CHAT_MAX_LENGTH - CHAT_LENGTH_HINT_FROM
        ? t.chat.charsLeft(left)
        : undefined);

  return (
    <form className={styles.composer} onSubmit={send}>
      <div className={styles.row}>
        <textarea
          className={styles.input}
          value={text}
          onChange={(event) => {
            // limited in code points like the server (maxLength would count an emoji twice)
            setText(clampChatText(event.target.value));
            setError(undefined);
          }}
          onKeyDown={onKeyDown}
          rows={1}
          enterKeyHint="send"
          placeholder={t.chat.placeholder}
          aria-label={t.chat.label}
          aria-describedby={message !== undefined ? messageId : undefined}
          aria-invalid={error !== undefined}
        />
        <Button type="submit" disabled={!connected || trimmed === ''}>
          {t.chat.send}
        </Button>
      </div>
      {message !== undefined && (
        <p
          id={messageId}
          className={error !== undefined || !connected ? styles.error : styles.hint}
          role={error !== undefined ? 'alert' : undefined}
        >
          {message}
        </p>
      )}
    </form>
  );
}

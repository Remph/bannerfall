import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import {
  loadOlderMessages,
  useBlockPlayer,
  useBlocks,
  useConversation,
  useMarkRead,
  useSendMessage,
  useUnblockPlayer,
  type PrivateMessage,
} from '../../api/queries/messages';
import { usePlayerProfile } from '../../api/queries/ranking';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { CHAT_LENGTH_HINT_FROM, CHAT_MAX_LENGTH, chatLength, clampChatText } from '../../game/chat';
import { formatRecent } from '../../game/format';
import { newestIncomingId, olderMessagesBefore } from '../../game/messages';
import { serverNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import { playerPath } from '../ranking/rankingPaths';
import { MESSAGES_PATH, parseConversationPlayerId } from './messagePaths';
import styles from './Messages.module.css';

/** /messages/:playerId – the conversation with one player (02-game-rules §17). */
export function ConversationPage() {
  const playerId = parseConversationPlayerId(useParams().playerId);
  if (playerId === undefined) {
    return <ConversationError error={new ApiError(404, 'PLAYER_NOT_FOUND', 'Not found')} />;
  }
  return <Conversation playerId={playerId} />;
}

function BackLink() {
  const { t } = useI18n();
  return (
    <Link to={MESSAGES_PATH} className={styles.back}>
      <Icon name="chevronLeft" /> {t.messages.back}
    </Link>
  );
}

function ConversationError({ error }: { error: Error }) {
  const { t } = useI18n();
  return (
    <div className={styles.page}>
      <BackLink />
      <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
    </div>
  );
}

function Conversation({ playerId }: { playerId: number }) {
  const { t } = useI18n();
  const profile = usePlayerProfile(playerId);
  if (profile.isError) {
    return <ConversationError error={profile.error} />;
  }
  if (profile.isPending) {
    return <p>{t.common.loading}</p>;
  }
  // keyed by the player: going from one conversation to another (a toast's link) must not take the
  // draft or the "marked as read up to" id of the first along
  return <Thread key={playerId} playerId={playerId} name={profile.data.name} />;
}

function Thread({ playerId, name }: { playerId: number; name: string }) {
  const { t } = useI18n();
  const conversation = useConversation(playerId);
  const blocks = useBlocks();
  const blocked = blocks.data?.some((entry) => entry.playerId === playerId) === true;
  const markRead = useMarkRead(playerId);
  const messages = conversation.data;
  const newestIncoming = messages === undefined ? 0 : newestIncomingId(messages);

  // Whatever the other player wrote is read once it is on screen: when the page opens and for each
  // message that arrives while it is open. A ref remembers the last id asked for, so the
  // invalidation after marking doesn't start another round.
  const markedUpTo = useRef(0);
  const markNow = markRead.mutate;
  useEffect(() => {
    if (newestIncoming > markedUpTo.current && !blocked) {
      markedUpTo.current = newestIncoming;
      markNow();
    }
  }, [newestIncoming, blocked, markNow]);

  return (
    <div className={styles.page}>
      <BackLink />
      <header className={styles.head}>
        <h1>{t.messages.withPlayer(name)}</h1>
        <div className={styles.tools}>
          <Link to={playerPath(playerId)}>{t.messages.profile}</Link>
        </div>
      </header>
      <Card title={t.messages.list}>
        {conversation.isError ? (
          <div className={styles.body}>
            <ErrorMessage>{errorMessage(t, conversation.error)}</ErrorMessage>
          </div>
        ) : messages === undefined ? (
          <div className={styles.body}>
            <p>{t.common.loading}</p>
          </div>
        ) : (
          <MessageList playerId={playerId} messages={messages} />
        )}
        {blocked ? <BlockedNote playerId={playerId} /> : <Composer playerId={playerId} />}
      </Card>
      {!blocked && <BlockButton playerId={playerId} name={name} />}
    </div>
  );
}

function MessageList({ playerId, messages }: { playerId: number; messages: PrivateMessage[] }) {
  const { t, language } = useI18n();
  const queryClient = useQueryClient();
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [noMoreOlder, setNoMoreOlder] = useState(false);
  const [olderError, setOlderError] = useState<Error | null>(null);
  const before = noMoreOlder ? undefined : olderMessagesBefore(messages);
  const end = useRef<HTMLLIElement>(null);
  const newest = messages.at(-1)?.id;

  // the newest message comes into view when the page opens and when one is added at the end
  useEffect(() => {
    end.current?.scrollIntoView({ block: 'nearest' });
  }, [newest]);

  async function loadOlder(beforeId: number) {
    setLoadingOlder(true);
    setOlderError(null);
    try {
      const loaded = await loadOlderMessages(queryClient, playerId, beforeId);
      setNoMoreOlder(loaded === 0);
    } catch (error) {
      // shown under the button (the click is fire-and-forget, nobody else would see it)
      setOlderError(error instanceof Error ? error : new Error(String(error)));
    } finally {
      setLoadingOlder(false);
    }
  }

  return (
    <div className={styles.thread}>
      {before !== undefined && (
        <Button
          type="button"
          variant="secondary"
          className={styles.older}
          disabled={loadingOlder}
          onClick={() => void loadOlder(before)}
        >
          {t.messages.loadOlder}
        </Button>
      )}
      {olderError !== null && <ErrorMessage>{errorMessage(t, olderError)}</ErrorMessage>}
      {messages.length === 0 ? (
        <p className={styles.empty}>{t.messages.empty}</p>
      ) : (
        <ul className={styles.messages}>
          {messages.map((message) => (
            <li key={message.id} className={message.mine ? `${styles.bubble} ${styles.mine}` : styles.bubble}>
              <p className={styles.bubbleText}>{message.text}</p>
              <span className={styles.bubbleWhen}>
                {formatRecent(Date.parse(message.createdAt), serverNow(), language)}
              </span>
            </li>
          ))}
          <li ref={end} aria-hidden="true" />
        </ul>
      )}
    </div>
  );
}

function Composer({ playerId }: { playerId: number }) {
  const { t } = useI18n();
  const send = useSendMessage(playerId);
  const [text, setText] = useState('');
  const messageId = useId();
  const trimmed = text.trim();
  const left = CHAT_MAX_LENGTH - chatLength(text);

  function submit(event?: FormEvent) {
    event?.preventDefault();
    if (trimmed === '' || send.isPending) {
      return;
    }
    send.mutate(trimmed, { onSuccess: () => setText('') });
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends, Shift+Enter is a new line; not while an input method is composing a character
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  }

  const error = send.isError ? errorMessage(t, send.error) : undefined;
  const hint = left <= CHAT_MAX_LENGTH - CHAT_LENGTH_HINT_FROM ? t.messages.charsLeft(left) : undefined;
  const message = error ?? hint;

  return (
    <form className={styles.composer} onSubmit={submit}>
      <div className={styles.composerRow}>
        <textarea
          className={styles.input}
          value={text}
          onChange={(event) => {
            // limited in code points like the server (maxLength would count an emoji twice)
            setText(clampChatText(event.target.value));
            send.reset();
          }}
          onKeyDown={onKeyDown}
          rows={1}
          enterKeyHint="send"
          placeholder={t.messages.placeholder}
          aria-label={t.messages.label}
          aria-describedby={message !== undefined ? messageId : undefined}
          aria-invalid={error !== undefined}
        />
        <Button type="submit" disabled={trimmed === '' || send.isPending}>
          {t.messages.send}
        </Button>
      </div>
      {message !== undefined && (
        <p id={messageId} className={styles.note} role={error !== undefined ? 'alert' : undefined}>
          {message}
        </p>
      )}
    </form>
  );
}

/** Instead of the composer while the player is blocked: why, and the way back. */
function BlockedNote({ playerId }: { playerId: number }) {
  const { t } = useI18n();
  const unblock = useUnblockPlayer();
  return (
    <div className={styles.composer}>
      <p className={styles.note}>{t.messages.blockedNote}</p>
      {unblock.isError && <ErrorMessage>{errorMessage(t, unblock.error)}</ErrorMessage>}
      <div>
        <Button type="button" variant="secondary" disabled={unblock.isPending} onClick={() => unblock.mutate(playerId)}>
          {t.messages.unblock}
        </Button>
      </div>
    </div>
  );
}

/** "Block" with a question first: blocking hides the whole conversation. */
function BlockButton({ playerId, name }: { playerId: number; name: string }) {
  const { t } = useI18n();
  const block = useBlockPlayer();
  const [open, setOpen] = useState(false);
  return (
    <>
      <div>
        <Button type="button" variant="secondary" onClick={() => setOpen(true)}>
          {t.messages.block}
        </Button>
      </div>
      <ConfirmSheet
        open={open}
        title={t.messages.blockTitle(name)}
        text={t.messages.blockText}
        confirmLabel={t.messages.blockConfirm}
        danger
        pending={block.isPending}
        error={block.error}
        onConfirm={() => block.mutate(playerId, { onSuccess: () => setOpen(false) })}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

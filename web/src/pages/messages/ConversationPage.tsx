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
import { useHelpRequests, type HelpRequest } from '../../api/queries/diplomacy';
import { usePlayerProfile } from '../../api/queries/ranking';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { useInListDetail } from '../../components/listDetailContext';
import { MoreMenu } from '../../components/MoreMenu';
import { CHAT_LENGTH_HINT_FROM, CHAT_MAX_LENGTH, chatLength, clampChatText } from '../../game/chat';
import { formatRecent } from '../../game/format';
import { newestIncomingId, olderMessagesBefore } from '../../game/messages';
import { serverNow, useGameNow } from '../../game/serverTime';
import { letterText } from '../../game/letters';
import { HelpRequestActions } from '../diplomacy/HelpRequestSheet';
import { useI18n } from '../../i18n';
import { playerPath } from '../ranking/rankingPaths';
import { MESSAGES_PATH, parseConversationPlayerId } from './messagePaths';
import styles from './Messages.module.css';

/**
 * /messages/:playerId – the conversation with one player (02-game-rules §17), a child of the inbox's
 * route: on desktop it is shown next to the inbox (1.3), as high as the window with its messages
 * scrolling inside.
 */
export function ConversationPage() {
  const playerId = parseConversationPlayerId(useParams().playerId);
  if (playerId === undefined) {
    return <ConversationError error={new ApiError(404, 'PLAYER_NOT_FOUND', 'Not found')} />;
  }
  return <Conversation playerId={playerId} />;
}

function ToMessages() {
  const { t } = useI18n();
  // next to the inbox (desktop) the way back is right there
  const inListDetail = useInListDetail();
  return inListDetail ? null : <BackLink to={MESSAGES_PATH}>{t.messages.back}</BackLink>;
}

function ConversationError({ error }: { error: Error }) {
  const { t } = useI18n();
  return (
    <div className={styles.page}>
      <ToMessages />
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
  return <Thread key={playerId} playerId={playerId} name={profile.data.name} npc={profile.data.npc} />;
}

function Thread({ playerId, name, npc }: { playerId: number; name: string; npc: boolean }) {
  const { t } = useI18n();
  // desktop, next to the inbox: as high as the column, the messages scroll and the input stays put
  const inListDetail = useInListDetail();
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
    <div className={inListDetail ? `${styles.page} ${styles.threadPage}` : styles.page}>
      <ToMessages />
      <header className={styles.head}>
        <h1>{t.messages.withPlayer(name)}</h1>
        <div className={styles.tools}>
          <Link to={playerPath(playerId)}>
            {t.messages.profile}
            <Icon name="chevronRight" />
          </Link>
          {!blocked && <BlockMenu playerId={playerId} name={name} />}
        </div>
      </header>
      <Card title={t.messages.list} className={styles.threadCard}>
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
        {/* a lord answers by letter only (2.1 S2b): no field to write into */}
        {npc ? <LordNote /> : blocked ? <BlockedNote playerId={playerId} /> : <Composer playerId={playerId} />}
      </Card>
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

  const asks = messages.some((message) => message.letter?.request != null);
  const helpRequests = useHelpRequests(asks);
  const requests = helpRequests.data ?? [];
  // the help actions under a lord's letter load after the letters and push the end down
  const actionsReady = !asks || helpRequests.isFetched;

  // the newest message comes into view when the page opens and when one is added at the end, and
  // again once the help actions under the newest letters are there (UI review 2.1, finding 03)
  useEffect(() => {
    scrollToEnd(end.current);
  }, [newest, actionsReady]);
  const openRequest = (id: number | null | undefined) =>
    id == null ? undefined : requests.find((request) => request.id === id);

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
              <p className={styles.bubbleText}>
                {message.letter != null ? letterText(t, message.letter, language) : message.text}
              </p>
              {/* a lord's help request (2.1 S4): the same buttons as its sheet while it is open */}
              <LetterRequest request={openRequest(message.letter?.request)} />
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

/**
 * The thread to its very end: its own scroller (beside the inbox on a PC) or the page (a phone, where
 * the lords' note and the room for the tab bar lie below the last letter, so `scrollIntoView` with
 * "nearest" stopped 163 px short behind the tab bar; UI review 2.1, finding 03).
 */
function scrollToEnd(end: HTMLElement | null) {
  if (end === null) {
    return;
  }
  for (let box = end.parentElement; box !== null; box = box.parentElement) {
    if (/auto|scroll/.test(getComputedStyle(box).overflowY) && box.scrollHeight > box.clientHeight) {
      box.scrollTop = box.scrollHeight;
      return;
    }
  }
  window.scrollTo(0, document.documentElement.scrollHeight);
}

/** Under a lord's letter that asks for help: the request sheet's buttons, while the request is open. */
function LetterRequest({ request }: { request: HelpRequest | undefined }) {
  return request === undefined ? null : <OpenLetterRequest request={request} />;
}

/** The buttons until the deadline passes (the server ends the request in the lord's next round). */
function OpenLetterRequest({ request }: { request: HelpRequest }) {
  const now = useGameNow();
  if (Date.parse(request.deadline) <= now) {
    return null;
  }
  return (
    <div className={styles.letterActions}>
      <HelpRequestActions request={request} />
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

/** Instead of the composer in a lord's conversation: where lords are asked instead. */
function LordNote() {
  const { t } = useI18n();
  return (
    <div className={styles.composer}>
      <p className={styles.note}>
        {t.messages.lordLetters} <Link to="/diplomacy">{t.messages.toDiplomacy}</Link>
      </p>
    </div>
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

/**
 * "Block" behind "…" next to Profile (2.0 UI review: as an outlined button right under "Send" it was
 * the first thing under the message field, rarely needed and easy to hit), with a question first:
 * blocking hides the whole conversation.
 */
function BlockMenu({ playerId, name }: { playerId: number; name: string }) {
  const { t } = useI18n();
  const block = useBlockPlayer();
  const [open, setOpen] = useState(false);
  return (
    <>
      <MoreMenu
        label={t.messages.moreFor(name)}
        align="left"
        items={[{ label: t.messages.blockPlayer, icon: 'lock', danger: true, onSelect: () => setOpen(true) }]}
      />
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

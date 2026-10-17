import { Link, useMatch, useOutlet } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useBlocks, useConversations, useUnblockPlayer, type Conversation } from '../../api/queries/messages';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { ListDetail } from '../../components/ListDetail';
import { formatRecent } from '../../game/format';
import { serverNow } from '../../game/serverTime';
import { bracketTag } from '../../game/guilds';
import { useI18n } from '../../i18n';
import { DESKTOP_QUERY, useMediaQuery } from '../../layout/useMediaQuery';
import { playerPath } from '../ranking/rankingPaths';
import { conversationPath, parseConversationPlayerId } from './messagePaths';
import styles from './Messages.module.css';

/**
 * /messages and /messages/:playerId (the child route, `ConversationPage`). Phones: the inbox, or the
 * conversation as a page of its own. Desktop (1.3): the inbox with the open conversation next to
 * it, or a hint to pick one.
 */
export function MessagesPage() {
  const { t } = useI18n();
  const split = useMediaQuery(DESKTOP_QUERY);
  const detail = useOutlet();
  const openId = parseConversationPlayerId(useMatch('/messages/:playerId')?.params.playerId);
  if (!split) {
    return detail ?? <InboxPage openId={undefined} />;
  }
  return (
    <ListDetail
      list={<InboxPage openId={openId} />}
      detail={detail}
      empty={t.messages.pickConversation}
      label={t.messages.openConversation}
      openKey={openId}
      fill
    />
  );
}

/** The inbox: the players you talked to, and the players you blocked (02-game-rules §17). */
function InboxPage({ openId }: { openId: number | undefined }) {
  const { t } = useI18n();
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.messages.title}</h1>
        <p className={styles.hint}>{t.messages.explainer}</p>
      </header>
      <Inbox openId={openId} />
      <BlockedPlayers />
    </div>
  );
}

/** `openId`: the conversation open next to the inbox (desktop), marked in the list. */
function Inbox({ openId }: { openId: number | undefined }) {
  const { t } = useI18n();
  const conversations = useConversations();
  return (
    <Card title={t.messages.title}>
      {conversations.isError ? (
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, conversations.error)}</ErrorMessage>
        </div>
      ) : conversations.isPending ? (
        <div className={styles.body}>
          <p>{t.common.loading}</p>
        </div>
      ) : conversations.data.length === 0 ? (
        <div className={styles.body}>
          <p className={styles.empty}>{t.messages.inboxEmpty}</p>
        </div>
      ) : (
        <ul className={styles.list}>
          {conversations.data.map((conversation) => (
            <li key={conversation.playerId}>
              <ConversationLine conversation={conversation} open={conversation.playerId === openId} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function ConversationLine({ conversation, open }: { conversation: Conversation; open: boolean }) {
  const { t, language } = useI18n();
  const unread = conversation.unread;
  return (
    <Link
      to={conversationPath(conversation.playerId)}
      className={[styles.line, unread > 0 && styles.unreadLine, open && styles.openLine].filter(Boolean).join(' ')}
      aria-current={open ? 'page' : undefined}
    >
      <span className={styles.lineTop}>
        <span className={styles.name}>
          {conversation.guildTag != null && <span className={styles.tag}>{bracketTag(conversation.guildTag)}</span>}
          {conversation.playerName}
        </span>
        <span className={styles.when}>{formatRecent(Date.parse(conversation.lastAt), serverNow(), language)}</span>
      </span>
      <span className={styles.preview}>
        <span className={styles.previewText}>
          {conversation.lastMine && `${t.messages.you}: `}
          {conversation.lastText}
        </span>
        {unread > 0 && <span className={styles.unread}>{t.messages.unreadFrom(unread)}</span>}
      </span>
    </Link>
  );
}

function BlockedPlayers() {
  const { t } = useI18n();
  const blocks = useBlocks();
  const unblock = useUnblockPlayer();
  // nothing to show before the list is there, and a failed list isn't worth an error of its own
  if (blocks.data === undefined) {
    return null;
  }
  return (
    <Card title={t.messages.blockedTitle}>
      {blocks.data.length === 0 ? (
        <div className={styles.body}>
          <p className={styles.empty}>{t.messages.blockedNone}</p>
        </div>
      ) : (
        <ul className={styles.list}>
          {blocks.data.map((blocked) => (
            <li key={blocked.playerId} className={styles.row}>
              <Link to={playerPath(blocked.playerId)}>{blocked.name}</Link>
              <Button
                type="button"
                variant="secondary"
                disabled={unblock.isPending}
                onClick={() => unblock.mutate(blocked.playerId)}
              >
                {t.messages.unblock}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {unblock.isError && (
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, unblock.error)}</ErrorMessage>
        </div>
      )}
    </Card>
  );
}

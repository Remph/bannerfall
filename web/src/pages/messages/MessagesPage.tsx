import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useBlocks, useConversations, useUnblockPlayer, type Conversation } from '../../api/queries/messages';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatRecent } from '../../game/format';
import { serverNow } from '../../game/serverTime';
import { bracketTag } from '../../game/guilds';
import { useI18n } from '../../i18n';
import { playerPath } from '../ranking/rankingPaths';
import { conversationPath } from './messagePaths';
import styles from './Messages.module.css';

/** /messages – the inbox: the players you talked to, and the players you blocked (02-game-rules §17). */
export function MessagesPage() {
  const { t } = useI18n();
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.messages.title}</h1>
        <p className={styles.hint}>{t.messages.explainer}</p>
      </header>
      <Inbox />
      <BlockedPlayers />
    </div>
  );
}

function Inbox() {
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
              <ConversationLine conversation={conversation} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function ConversationLine({ conversation }: { conversation: Conversation }) {
  const { t, language } = useI18n();
  const unread = conversation.unread;
  return (
    <Link
      to={conversationPath(conversation.playerId)}
      className={unread > 0 ? `${styles.line} ${styles.unreadLine}` : styles.line}
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

import { type UseMutationResult } from '@tanstack/react-query';
import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useBroadcast, useChatSay } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { chatLength, CHAT_MAX_LENGTH } from '../../game/chat';
import { useT } from '../../i18n';
import styles from './AdminPage.module.css';

/** POST /api/admin/broadcast: a popup for everybody who is playing (1.7), nothing in the chat. */
export function BroadcastCard() {
  const t = useT();
  return <MessageCard labels={t.admin.broadcast} broadcast={useBroadcast()} />;
}

/** POST /api/admin/chat: a chat message without author that everybody also gets as a toast. */
export function ChatSayCard() {
  const t = useT();
  return <MessageCard labels={t.admin.chatSay} broadcast={useChatSay()} />;
}

interface MessageCardProps {
  labels: { title: string; hint: string; label: string; send: string; sent: string };
  broadcast: UseMutationResult<unknown, Error, string>;
}

function MessageCard({ labels, broadcast }: MessageCardProps) {
  const t = useT();
  const [text, setText] = useState('');
  const trimmed = text.trim();
  const valid = trimmed !== '' && chatLength(trimmed) <= CHAT_MAX_LENGTH;

  return (
    <Card title={labels.title}>
      <form
        className={styles.stack}
        onSubmit={(event) => {
          event.preventDefault();
          if (valid) {
            broadcast.mutate(trimmed, { onSuccess: () => setText('') });
          }
        }}
      >
        <p className={styles.hint}>{labels.hint}</p>
        <TextField
          label={labels.label}
          value={text}
          maxLength={CHAT_MAX_LENGTH * 2}
          onChange={(event) => {
            setText(event.target.value);
            broadcast.reset();
          }}
        />
        {broadcast.isError && <ErrorMessage>{errorMessage(t, broadcast.error)}</ErrorMessage>}
        {broadcast.isSuccess && <p className={styles.saved}>{labels.sent}</p>}
        <div>
          <Button type="submit" disabled={!valid || broadcast.isPending}>
            {labels.send}
          </Button>
        </div>
      </form>
    </Card>
  );
}

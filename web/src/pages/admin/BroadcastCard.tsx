import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useBroadcast } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { chatLength, CHAT_MAX_LENGTH } from '../../game/chat';
import { useT } from '../../i18n';
import styles from './AdminPage.module.css';

/** POST /api/admin/broadcast: a chat message without author that everybody also gets as a toast. */
export function BroadcastCard() {
  const t = useT();
  const labels = t.admin.broadcast;
  const broadcast = useBroadcast();
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

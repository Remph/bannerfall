import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useAdminWorldSettings, useEndWorldNow, useReopenWorld, useSetWorldEnd } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import { fromLocalInput, toLocalInput } from '../pregame/openingTime';
import styles from './AdminPage.module.css';

/**
 * The end of the world (v1.3, 02-game-rules §22): an end date (the most points win then), ending it
 * now, and taking an end back.
 */
export function WorldEndCard() {
  const { t } = useI18n();
  const settings = useAdminWorldSettings();
  if (settings.data === undefined) {
    return null;
  }
  return (
    <Card title={t.admin.worldEnd.title}>
      <div className={styles.stack}>
        {settings.data.phase === 'ENDED' ? (
          <EndedForm endedAt={settings.data.endedAt ?? null} />
        ) : (
          // a new key = the field follows the stored time after a save
          <EndDateForm key={settings.data.endsAt ?? 'none'} endsAt={settings.data.endsAt ?? null} />
        )}
      </div>
    </Card>
  );
}

function EndDateForm({ endsAt }: { endsAt: string | null }) {
  const { t, language } = useI18n();
  const labels = t.admin.worldEnd;
  const set = useSetWorldEnd();
  const endNow = useEndWorldNow();
  const [text, setText] = useState(toLocalInput(endsAt));
  const [confirming, setConfirming] = useState(false);
  const chosen = fromLocalInput(text);
  const busy = set.isPending || endNow.isPending;
  const failure = set.error ?? endNow.error;

  return (
    <>
      <p className={styles.hint}>{labels.hint}</p>
      <p>{endsAt === null ? labels.noDate : labels.status(formatDateTime(Date.parse(endsAt), language))}</p>
      <div className={styles.fields}>
        <TextField
          label={labels.at}
          type="datetime-local"
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            set.reset();
          }}
        />
      </div>
      {failure !== null && <ErrorMessage>{errorMessage(t, failure)}</ErrorMessage>}
      {set.isSuccess && <p className={styles.saved}>{labels.saved}</p>}
      <div className={styles.actions}>
        <Button type="button" disabled={chosen === null || busy} onClick={() => set.mutate(chosen)}>
          {labels.set}
        </Button>
        <Button
          type="button"
          variant="secondary"
          disabled={endsAt === null || busy}
          onClick={() => {
            setText('');
            set.mutate(null);
          }}
        >
          {labels.clear}
        </Button>
        {!confirming && (
          <Button type="button" variant="danger" disabled={busy} onClick={() => setConfirming(true)}>
            {labels.endNow}
          </Button>
        )}
      </div>
      {confirming && (
        <div className={styles.stack} role="alertdialog" aria-label={labels.endNow}>
          <p>{labels.confirmNow}</p>
          <div className={styles.actions}>
            <Button type="button" variant="danger" disabled={busy} onClick={() => endNow.mutate()}>
              {labels.confirmYes}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setConfirming(false)}>
              {t.common.cancel}
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

function EndedForm({ endedAt }: { endedAt: string | null }) {
  const { t, language } = useI18n();
  const labels = t.admin.worldEnd;
  const reopen = useReopenWorld();
  const [confirming, setConfirming] = useState(false);
  return (
    <>
      <p>{labels.ended(endedAt === null ? '' : formatDateTime(Date.parse(endedAt), language))}</p>
      {reopen.error !== null && <ErrorMessage>{errorMessage(t, reopen.error)}</ErrorMessage>}
      {!confirming ? (
        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={() => setConfirming(true)}>
            {labels.reopen}
          </Button>
        </div>
      ) : (
        <div className={styles.stack} role="alertdialog" aria-label={labels.reopen}>
          <p>{labels.confirmReopen}</p>
          <div className={styles.actions}>
            <Button type="button" variant="primary" disabled={reopen.isPending} onClick={() => reopen.mutate()}>
              {labels.confirmReopenYes}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setConfirming(false)}>
              {t.common.cancel}
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

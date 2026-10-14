import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useAdminWorldSettings, useOpenWorldNow, useSetWorldOpening } from '../../api/queries/admin';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { formatDateTime } from '../../game/format';
import { useI18n } from '../../i18n';
import { fromLocalInput, toLocalInput } from '../pregame/openingTime';
import styles from './AdminPage.module.css';

/**
 * The pre-game's opening time (M14g, 08-host-console §2): set it, take it back, or open the world
 * now. Nothing but a note once the world is open.
 */
export function OpeningCard() {
  const { t } = useI18n();
  const settings = useAdminWorldSettings();
  if (settings.data === undefined) {
    return null;
  }
  return (
    <Card title={t.admin.opening.title}>
      <div className={styles.stack}>
        {settings.data.phase === 'PREGAME' ? (
          // a new key = the field follows the stored time after a save
          <OpeningForm key={settings.data.opensAt ?? 'none'} opensAt={settings.data.opensAt ?? null} />
        ) : (
          <p>{t.admin.opening.open}</p>
        )}
      </div>
    </Card>
  );
}

function OpeningForm({ opensAt }: { opensAt: string | null }) {
  const { t, language } = useI18n();
  const labels = t.admin.opening;
  const world = useWorld();
  const set = useSetWorldOpening();
  const openNow = useOpenWorldNow();
  const [text, setText] = useState(toLocalInput(opensAt));
  const [confirming, setConfirming] = useState(false);
  const chosen = fromLocalInput(text);
  const busy = set.isPending || openNow.isPending;
  const failure = set.error ?? openNow.error;

  return (
    <>
      <p className={styles.hint}>{labels.hint}</p>
      <p>{opensAt === null ? labels.noTime : labels.status(formatDateTime(Date.parse(opensAt), language))}</p>
      {world.data !== undefined && <p className={styles.hint}>{labels.registered(world.data.registeredPlayers)}</p>}
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
      <p className={styles.hint}>{labels.pastHint}</p>
      {failure !== null && <ErrorMessage>{errorMessage(t, failure)}</ErrorMessage>}
      {set.isSuccess && <p className={styles.saved}>{labels.saved}</p>}
      <div className={styles.actions}>
        <Button type="button" disabled={chosen === null || busy} onClick={() => set.mutate(chosen)}>
          {labels.set}
        </Button>
        <Button
          type="button"
          variant="secondary"
          disabled={opensAt === null || busy}
          onClick={() => {
            setText('');
            set.mutate(null);
          }}
        >
          {labels.clear}
        </Button>
        {!confirming && (
          <Button type="button" variant="danger" disabled={busy} onClick={() => setConfirming(true)}>
            {labels.openNow}
          </Button>
        )}
      </div>
      {confirming && (
        <div className={styles.stack} role="alertdialog" aria-label={labels.openNow}>
          <p>{labels.confirmNow}</p>
          <div className={styles.actions}>
            <Button type="button" variant="danger" disabled={busy} onClick={() => openNow.mutate()}>
              {labels.confirmYes}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setConfirming(false)}>
              {labels.cancel}
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

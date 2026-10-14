import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useAdminWorldSettings, useSpawnBarbarian } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { useT } from '../../i18n';
import styles from './AdminPage.module.css';

/** POST /api/admin/barbarians: a barbarian village on exactly one field (not a cheat, always there). */
export function BarbarianCard() {
  const t = useT();
  const labels = t.admin.barbarian;
  const spawn = useSpawnBarbarian();
  const settings = useAdminWorldSettings();
  const [x, setX] = useState('');
  const [y, setY] = useState('');
  const field = { x: Number(x), y: Number(y) };
  const valid = x !== '' && y !== '' && Number.isInteger(field.x) && Number.isInteger(field.y);

  // there is no map before the world opens (M14g)
  if (settings.data?.phase === 'PREGAME') {
    return null;
  }
  return (
    <Card title={labels.title}>
      <form
        className={styles.stack}
        onSubmit={(event) => {
          event.preventDefault();
          if (valid) {
            spawn.mutate(field);
          }
        }}
      >
        <p className={styles.hint}>{labels.hint}</p>
        <div className={styles.fields}>
          <TextField
            label={labels.x}
            type="number"
            min={0}
            step={1}
            value={x}
            onChange={(event) => {
              setX(event.target.value);
              spawn.reset();
            }}
          />
          <TextField
            label={labels.y}
            type="number"
            min={0}
            step={1}
            value={y}
            onChange={(event) => {
              setY(event.target.value);
              spawn.reset();
            }}
          />
        </div>
        {spawn.isError && <ErrorMessage>{errorMessage(t, spawn.error)}</ErrorMessage>}
        {spawn.isSuccess && <p className={styles.saved}>{labels.spawned(spawn.data.x, spawn.data.y)}</p>}
        <div>
          <Button type="submit" disabled={!valid || spawn.isPending}>
            {labels.spawn}
          </Button>
        </div>
      </form>
    </Card>
  );
}

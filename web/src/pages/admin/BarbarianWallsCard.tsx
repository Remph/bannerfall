import { useState, type ChangeEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useAdminWorldSettings, useUpdateWorldSettings, type AdminWorldSettings } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { useT } from '../../i18n';
import styles from './AdminPage.module.css';
import { barbarianWallsRequest, barbarianWallsToForm, type BarbarianWallsForm } from './barbarianWallsForm';

/** The barbarians' wall settings (M29, 02-game-rules §11): their own card next to the world settings. */
export function BarbarianWallsCard() {
  const t = useT();
  const settings = useAdminWorldSettings();
  // here, not in the form: a successful save gives the form a new key, and "Saved" must survive that
  const save = useUpdateWorldSettings();
  return (
    <Card title={t.admin.barbarianWalls.title}>
      <div className={styles.stack}>
        {settings.isError && <ErrorMessage>{errorMessage(t, settings.error)}</ErrorMessage>}
        {settings.isPending && <p>{t.common.loading}</p>}
        {settings.data !== undefined && (
          // a new key = a fresh form whenever the stored settings change
          <WallsForm key={JSON.stringify(settings.data)} settings={settings.data} save={save} />
        )}
      </div>
    </Card>
  );
}

function WallsForm({
  settings,
  save,
}: {
  settings: AdminWorldSettings;
  save: ReturnType<typeof useUpdateWorldSettings>;
}) {
  const t = useT();
  const labels = t.admin.barbarianWalls;
  const [form, setForm] = useState<BarbarianWallsForm>(() => barbarianWallsToForm(settings));
  const request = barbarianWallsRequest(settings, form);

  function number(name: 'barbarianWallStartDays' | 'barbarianWallMaxLevel', max: string) {
    return {
      label: labels[name],
      type: 'number',
      step: '1',
      min: '0',
      max,
      value: form[name],
      onChange: (event: ChangeEvent<HTMLInputElement>) => {
        setForm({ ...form, [name]: event.target.value });
        save.reset();
      },
    };
  }

  function toggle(name: 'barbarianWallsEnabled' | 'barbarianWallsRebuild') {
    return (
      <label className={styles.check}>
        <input
          type="checkbox"
          checked={form[name]}
          onChange={(event) => {
            setForm({ ...form, [name]: event.target.checked });
            save.reset();
          }}
        />
        {labels[name]}
      </label>
    );
  }

  return (
    <form
      className={styles.form}
      onSubmit={(event) => {
        event.preventDefault();
        if (request !== null) {
          save.mutate(request);
        }
      }}
    >
      <p className={styles.hint}>{labels.hint}</p>
      {toggle('barbarianWallsEnabled')}
      <div className={styles.fields}>
        <TextField {...number('barbarianWallStartDays', '365')} />
        <TextField {...number('barbarianWallMaxLevel', '20')} />
      </div>
      {toggle('barbarianWallsRebuild')}
      {save.isError && <ErrorMessage>{errorMessage(t, save.error)}</ErrorMessage>}
      {save.isSuccess && <p className={styles.saved}>{labels.saved}</p>}
      <div>
        <Button type="submit" disabled={request === null || save.isPending}>
          {t.common.save}
        </Button>
      </div>
    </form>
  );
}

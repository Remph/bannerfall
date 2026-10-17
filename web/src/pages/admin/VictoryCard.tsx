import { useState, type ChangeEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useAdminWorldSettings, useUpdateWorldSettings, type AdminWorldSettings } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { TextField } from '../../components/TextField';
import { keepsNeeded } from '../../game/victory';
import { useT } from '../../i18n';
import styles from './AdminPage.module.css';
import { VICTORY_MODES, victoryRequest, victoryToForm, type VictoryForm } from './victoryForm';

/** How the world is won (v1.3, 02-game-rules §22): the mode and its numbers, a card of their own. */
export function VictoryCard() {
  const t = useT();
  const settings = useAdminWorldSettings();
  // here, not in the form: a successful save gives the form a new key, and "Saved" must survive that
  const save = useUpdateWorldSettings();
  return (
    <Card title={t.admin.victory.title}>
      <div className={styles.stack}>
        {settings.isError && <ErrorMessage>{errorMessage(t, settings.error)}</ErrorMessage>}
        {settings.isPending && <p>{t.common.loading}</p>}
        {settings.data !== undefined && (
          // a new key = a fresh form whenever the stored settings change
          <VictorySettingsForm key={JSON.stringify(settings.data)} settings={settings.data} save={save} />
        )}
      </div>
    </Card>
  );
}

type NumberField = Exclude<keyof VictoryForm, 'victoryMode'>;

function VictorySettingsForm({
  settings,
  save,
}: {
  settings: AdminWorldSettings;
  save: ReturnType<typeof useUpdateWorldSettings>;
}) {
  const t = useT();
  const labels = t.admin.victory;
  const [form, setForm] = useState<VictoryForm>(() => victoryToForm(settings));
  const request = victoryRequest(settings, form);

  function number(name: NumberField, min: string, max: string) {
    return {
      label: labels[name],
      type: 'number',
      step: '1',
      min,
      max,
      value: form[name],
      onChange: (event: ChangeEvent<HTMLInputElement>) => {
        setForm({ ...form, [name]: event.target.value });
        save.reset();
      },
    };
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
      <SegmentedControl
        label={labels.mode}
        options={VICTORY_MODES.map((mode) => ({ value: mode, label: labels.modes[mode] }))}
        value={form.victoryMode}
        onChange={(victoryMode) => {
          setForm({ ...form, victoryMode });
          save.reset();
        }}
      />
      <p className={styles.hint}>{labels.modeHints[form.victoryMode]}</p>
      <div className={styles.fields}>
        {form.victoryMode === 'DOMINATION' && <TextField {...number('dominationPercent', '10', '100')} />}
        {form.victoryMode === 'KEEPS' && (
          <>
            <TextField {...number('keepCount', '1', '12')} />
            <TextField {...number('keepPercent', '1', '100')} />
          </>
        )}
        {form.victoryMode !== 'NONE' && <TextField {...number('victoryHoldHours', '1', '720')} />}
      </div>
      {form.victoryMode === 'KEEPS' && request?.keepCount != null && request.keepPercent != null && (
        <p className={styles.hint}>
          {labels.keepsNeeded(keepsNeeded(request.keepCount, request.keepPercent), request.keepCount)}
        </p>
      )}
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

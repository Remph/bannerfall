import { useState, type ChangeEvent } from 'react';
import { errorMessage } from '../../api/errors';
import {
  npcDifficulties,
  useNpcCustom,
  useNpcSettings,
  useSetNpcCustom,
  useSetNpcDifficulty,
  type NpcDifficultyValues,
} from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { TextField } from '../../components/TextField';
import { useT } from '../../i18n';
import styles from './AdminPage.module.css';
import { customFromForm, customToForm, type CustomForm } from './customForm';

/** The difficulty of the NPC lords and, when CUSTOM is picked, its values (09 §8). */
export function NpcDifficultyCard() {
  const t = useT();
  const settings = useNpcSettings();
  const setDifficulty = useSetNpcDifficulty();
  const custom = useNpcCustom();

  return (
    <Card title={t.admin.difficulty.title}>
      <div className={styles.stack}>
        {settings.isError && <ErrorMessage>{errorMessage(t, settings.error)}</ErrorMessage>}
        {settings.data !== undefined && (
          <SegmentedControl
            label={t.admin.difficulty.title}
            options={npcDifficulties.map((value) => ({ value, label: t.admin.difficulty.names[value] }))}
            value={settings.data.difficulty}
            onChange={(difficulty) => setDifficulty.mutate(difficulty)}
          />
        )}
        {setDifficulty.isError && <ErrorMessage>{errorMessage(t, setDifficulty.error)}</ErrorMessage>}
        <p className={styles.hint}>{t.admin.difficulty.hint}</p>
        {settings.data?.difficulty === 'CUSTOM' && custom.data !== undefined && (
          // a new key = a fresh form whenever the stored values change
          <CustomValuesForm key={JSON.stringify(custom.data)} values={custom.data} />
        )}
      </div>
    </Card>
  );
}

function CustomValuesForm({ values }: { values: NpcDifficultyValues }) {
  const t = useT();
  const labels = t.admin.difficulty.custom;
  const save = useSetNpcCustom();
  const [form, setForm] = useState<CustomForm>(() => customToForm(values));
  const parsed = customFromForm(form);

  function text(field: keyof CustomForm) {
    return {
      value: form[field] as string,
      onChange: (event: ChangeEvent<HTMLInputElement>) => {
        setForm({ ...form, [field]: event.target.value });
        save.reset();
      },
    };
  }

  function toggle(field: 'quietHours' | 'spareLastVillage') {
    return {
      checked: form[field],
      onChange: (event: ChangeEvent<HTMLInputElement>) => {
        setForm({ ...form, [field]: event.target.checked });
        save.reset();
      },
    };
  }

  return (
    <form
      className={styles.form}
      onSubmit={(event) => {
        event.preventDefault();
        if (parsed !== null) {
          save.mutate(parsed);
        }
      }}
    >
      <h3>{labels.title}</h3>
      <p className={styles.hint}>{labels.hint}</p>
      <div className={styles.fields}>
        <TextField label={labels.productionFactor} type="number" step="0.05" min="0.05" {...text('productionFactor')} />
        <TextField label={labels.boostBelow} type="number" step="0.05" min="0" {...text('boostBelow')} />
        <TextField label={labels.boostFactor} type="number" step="0.05" min="0.05" {...text('boostFactor')} />
        <TextField label={labels.leadMargin} type="number" step="0.05" min="0.05" {...text('leadMargin')} />
        <TextField
          label={labels.attacksPerPlayerPerDay}
          type="number"
          step="1"
          min="0"
          {...text('attacksPerPlayerPerDay')}
        />
        <TextField
          label={labels.attacksPerHumanPerDay}
          type="number"
          step="1"
          min="0"
          {...text('attacksPerHumanPerDay')}
        />
        <TextField label={labels.buildQuietFrom} type="time" {...text('buildQuietFrom')} />
        <TextField label={labels.buildQuietTo} type="time" {...text('buildQuietTo')} />
      </div>
      <label className={styles.check}>
        <input type="checkbox" {...toggle('quietHours')} />
        {labels.quietHours}
      </label>
      <label className={styles.check}>
        <input type="checkbox" {...toggle('spareLastVillage')} />
        {labels.spareLastVillage}
      </label>
      {save.isError && <ErrorMessage>{errorMessage(t, save.error)}</ErrorMessage>}
      {save.isSuccess && <p className={styles.saved}>{labels.saved}</p>}
      <div>
        <Button type="submit" disabled={parsed === null || save.isPending}>
          {t.common.save}
        </Button>
      </div>
    </form>
  );
}

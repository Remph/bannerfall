import { useState, type ChangeEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useAdminWorldSettings, useUpdateWorldSettings, type AdminWorldSettings } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { TextField } from '../../components/TextField';
import { useT } from '../../i18n';
import { languageNames } from '../../i18n/language';
import styles from './AdminPage.module.css';
import { villageNameLanguages, worldFromForm, worldToForm, type WorldForm } from './worldForm';

/** The world settings of 02-game-rules §1, including the invite code (04-api *Admin*). */
export function WorldSettingsCard() {
  const t = useT();
  const settings = useAdminWorldSettings();
  // here, not in the form: a successful save gives the form a new key, and "Saved" must survive that
  const save = useUpdateWorldSettings();
  return (
    <Card title={t.admin.world.title}>
      <div className={styles.stack}>
        {settings.isError && <ErrorMessage>{errorMessage(t, settings.error)}</ErrorMessage>}
        {settings.isPending && <p>{t.common.loading}</p>}
        {settings.data !== undefined && (
          // a new key = a fresh form whenever the stored settings change
          <SettingsForm key={JSON.stringify(settings.data)} settings={settings.data} save={save} />
        )}
      </div>
    </Card>
  );
}

type FieldName = Exclude<
  keyof WorldForm,
  | 'barbarianGrowthEnabled'
  | 'guildsEnabled'
  | 'npcGuildsEnabled'
  | 'unitLevelsEnabled'
  | 'bannersEnabled'
  | 'standardTheftEnabled'
  | 'npcBannersEnabled'
  | 'villageNameLanguage'
  | 'nightBonusEnabled'
>;

function SettingsForm({
  settings,
  save,
}: {
  settings: AdminWorldSettings;
  save: ReturnType<typeof useUpdateWorldSettings>;
}) {
  const t = useT();
  const labels = t.admin.world;
  const [form, setForm] = useState<WorldForm>(() => worldToForm(settings));
  const pregame = settings.phase === 'PREGAME';
  const parsed = worldFromForm(form, pregame);

  function field(name: FieldName, props: { type?: string; step?: string; min?: string; max?: string } = {}) {
    return {
      label: labels[name],
      type: 'text',
      value: form[name],
      onChange: (event: ChangeEvent<HTMLInputElement>) => {
        setForm({ ...form, [name]: event.target.value });
        save.reset();
      },
      ...props,
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
      <p className={styles.hint}>{labels.hint}</p>
      <p className={styles.hint}>
        {pregame
          ? labels.pregameHint
          : labels.fixed(settings.mapSize, settings.barbarianInitialCount, settings.npcLordCount)}
      </p>
      <div className={styles.fields}>
        <TextField {...field('name')} maxLength={64} />
        <TextField {...field('inviteCode')} maxLength={64} />
        {pregame && (
          <>
            <TextField {...field('mapSize', { type: 'number', step: '10', min: '20', max: '500' })} />
            <TextField {...field('barbarianInitialCount', { type: 'number', step: '5', min: '0', max: '5000' })} />
            <TextField {...field('npcLordCount', { type: 'number', step: '1', min: '0', max: '50' })} />
          </>
        )}
        <TextField {...field('speed', { type: 'number', step: '0.1', min: '0.1', max: '100' })} />
        <TextField {...field('unitSpeed', { type: 'number', step: '0.1', min: '0.1', max: '100' })} />
        <TextField {...field('startResources', { type: 'number', step: '50', min: '0', max: '1000000' })} />
        <TextField {...field('luckRange', { type: 'number', step: '0.05', min: '0', max: '1' })} />
        <TextField {...field('beginnerProtectionHours', { type: 'number', step: '1', min: '0', max: '720' })} />
        <TextField {...field('nobleMaxDistance', { type: 'number', step: '1', min: '1', max: '1000' })} />
        <TextField {...field('loyaltyRegenPerHour', { type: 'number', step: '0.1', min: '0', max: '100' })} />
        <TextField {...field('buildQueueSize', { type: 'number', step: '1', min: '1', max: '20' })} />
        <TextField {...field('buildQueueMax', { type: 'number', step: '1', min: '1', max: '30' })} />
        <TextField {...field('recruitQueueSize', { type: 'number', step: '1', min: '1', max: '20' })} />
        <TextField {...field('cancelWindowSeconds', { type: 'number', step: '60', min: '0', max: '86400' })} />
        <TextField {...field('barbariansPerNewPlayer', { type: 'number', step: '1', min: '0', max: '20' })} />
        <TextField {...field('tradeMaxRatio', { type: 'number', step: '0.5', min: '1', max: '10' })} />
        <TextField {...field('npcThinkMinutes', { type: 'number', step: '1', min: '1', max: '1440' })} />
        <TextField {...field('guildMaxMembers', { type: 'number', step: '1', min: '2', max: '50' })} />
        <TextField {...field('npcGuildMaxMembers', { type: 'number', step: '1', min: '2', max: '50' })} />
        <TextField {...field('paladinVillagesPerSlot', { type: 'number', step: '1', min: '1', max: '100' })} />
        <TextField {...field('paladinMaxSlots', { type: 'number', step: '1', min: '0', max: '20' })} />
        <TextField {...field('bannerDropFactor', { type: 'number', step: '0.1', min: '0', max: '3' })} />
        <TextField {...field('marketExchangeRatio', { type: 'number', step: '0.1', min: '0', max: '10' })} />
      </div>
      <div className={styles.setting}>
        <span className={styles.settingLabel}>{labels.villageNameLanguage}</span>
        <SegmentedControl
          label={labels.villageNameLanguage}
          options={villageNameLanguages.map((value) => {
            const lang = value === 'DE' ? 'de' : 'en';
            return { value, label: languageNames[lang], lang };
          })}
          value={form.villageNameLanguage}
          onChange={(villageNameLanguage) => {
            setForm({ ...form, villageNameLanguage });
            save.reset();
          }}
        />
        <p className={styles.hint}>{labels.villageNameLanguageHint}</p>
      </div>
      <label className={styles.check}>
        <input
          type="checkbox"
          checked={form.barbarianGrowthEnabled}
          onChange={(event) => {
            setForm({ ...form, barbarianGrowthEnabled: event.target.checked });
            save.reset();
          }}
        />
        {labels.barbarianGrowthEnabled}
      </label>
      <label className={styles.check}>
        <input
          type="checkbox"
          checked={form.guildsEnabled}
          onChange={(event) => {
            setForm({ ...form, guildsEnabled: event.target.checked });
            save.reset();
          }}
        />
        {labels.guildsEnabled}
      </label>
      <div>
        <label className={styles.check}>
          <input
            type="checkbox"
            checked={form.npcGuildsEnabled}
            onChange={(event) => {
              setForm({ ...form, npcGuildsEnabled: event.target.checked });
              save.reset();
            }}
          />
          {labels.npcGuildsEnabled}
        </label>
        <p className={styles.hint}>
          {settings.npcGuildsEnabled && !form.npcGuildsEnabled ? labels.npcGuildsOff : labels.npcGuildsHint}
        </p>
      </div>
      <label className={styles.check}>
        <input
          type="checkbox"
          checked={form.unitLevelsEnabled}
          onChange={(event) => {
            setForm({ ...form, unitLevelsEnabled: event.target.checked });
            save.reset();
          }}
        />
        {labels.unitLevelsEnabled}
      </label>
      <label className={styles.check}>
        <input
          type="checkbox"
          checked={form.bannersEnabled}
          onChange={(event) => {
            setForm({ ...form, bannersEnabled: event.target.checked });
            save.reset();
          }}
        />
        {labels.bannersEnabled}
      </label>
      <label className={styles.check}>
        <input
          type="checkbox"
          checked={form.standardTheftEnabled}
          onChange={(event) => {
            setForm({ ...form, standardTheftEnabled: event.target.checked });
            save.reset();
          }}
        />
        {labels.standardTheftEnabled}
      </label>
      <div>
        <label className={styles.check}>
          <input
            type="checkbox"
            checked={form.npcBannersEnabled}
            onChange={(event) => {
              setForm({ ...form, npcBannersEnabled: event.target.checked });
              save.reset();
            }}
          />
          {labels.npcBannersEnabled}
        </label>
        <p className={styles.hint}>{labels.npcBannersHint}</p>
      </div>
      <div className={styles.form}>
        <label className={styles.check}>
          <input
            type="checkbox"
            checked={form.nightBonusEnabled}
            onChange={(event) => {
              setForm({ ...form, nightBonusEnabled: event.target.checked });
              save.reset();
            }}
          />
          {labels.nightBonusEnabled}
        </label>
        <div className={styles.fields}>
          <TextField {...field('nightBonusStartHour', { type: 'number', step: '1', min: '0', max: '23' })} />
          <TextField {...field('nightBonusEndHour', { type: 'number', step: '1', min: '0', max: '23' })} />
          <TextField {...field('nightBonusFactor', { type: 'number', step: '0.1', min: '1.1', max: '3' })} />
        </div>
        <p className={styles.hint}>{labels.nightBonusHint}</p>
      </div>
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

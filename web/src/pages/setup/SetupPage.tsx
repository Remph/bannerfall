import { useEffect, useRef, useState, type ChangeEvent, type ReactNode } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import {
  useAdminStatus,
  useAdminWorldSettings,
  useSetNpcDifficulty,
  useSetWorldEnd,
  useSetWorldOpening,
  useUpdateWorldSettings,
  type AdminWorldSettings,
  type NpcDifficulty,
} from '../../api/queries/admin';
import { useAuthOptions, useCurrentAccount } from '../../api/queries/auth';
import { useClaimSetup, useSetupDone } from '../../api/queries/setup';
import type { VictoryMode } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { TextField } from '../../components/TextField';
import { useI18n } from '../../i18n';
import { languageNames } from '../../i18n/language';
import { mapSpreads, villageNameLanguages, withSpread } from '../admin/worldForm';
import { forgetSetupCode, pendingSetupCode } from './setupCode';
import styles from './SetupPage.module.css';
import {
  applyPreset,
  localToInstant,
  newInviteCode,
  noblesFromDays,
  presetFacts,
  setupFromSettings,
  setupPresets,
  setupSteps,
  setupToRequest,
  type SetupForm,
  type SetupPreset,
  type SetupStep,
} from './setupPresets';

/**
 * /setup – the guided world setup (1.6): the host walks through the important world settings while
 * the world is in the pre-game. Opened with `?code=` from `bannerfall-admin setup`, it first makes
 * the logged-in account the admin.
 */
export function SetupPage() {
  const me = useCurrentAccount();
  const [params] = useSearchParams();
  const code = params.get('code') ?? pendingSetupCode();
  if (me.role !== 'ADMIN') {
    return code === null ? <Navigate to="/" replace /> : <ClaimSetup code={code} />;
  }
  return <AdminSetup />;
}

/** Sends the code once; the account is an admin afterwards and the page shows the wizard. */
function ClaimSetup({ code }: { code: string }) {
  const { t } = useI18n();
  const claim = useClaimSetup();
  const sent = useRef(false);
  useEffect(() => {
    // once, also under StrictMode's second effect run: the code works only once
    if (!sent.current) {
      sent.current = true;
      claim.mutate(code, { onSettled: forgetSetupCode });
    }
  }, [claim, code]);
  if (claim.isError) {
    return (
      <Card title={t.setup.title}>
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, claim.error)}</ErrorMessage>
          <Link to="/">{t.setup.toHome}</Link>
        </div>
      </Card>
    );
  }
  return <p>{t.setup.claiming}</p>;
}

function AdminSetup() {
  const { t } = useI18n();
  const settings = useAdminWorldSettings();
  const [, setParams] = useSearchParams();
  useEffect(() => {
    // an admin needs no code: drop a waiting one, and the code from the address
    if (pendingSetupCode() !== null) {
      forgetSetupCode();
    }
  }, []);
  useEffect(() => {
    setParams(
      (params) => {
        params.delete('code');
        return params;
      },
      { replace: true },
    );
  }, [setParams]);
  if (settings.isError) {
    return <ErrorMessage>{errorMessage(t, settings.error)}</ErrorMessage>;
  }
  if (settings.data === undefined) {
    return <p>{t.common.loading}</p>;
  }
  if (settings.data.phase !== 'PREGAME') {
    return (
      <Card title={t.setup.title}>
        <div className={styles.body}>
          <p className={styles.hint}>{t.setup.worldOpen}</p>
          <Link to="/admin?tab=world">{t.setup.toAdmin}</Link>
        </div>
      </Card>
    );
  }
  return <Wizard settings={settings.data} />;
}

function parsePreset(value: string | null): SetupPreset | null {
  return setupPresets.find((preset) => preset === value) ?? null;
}

function Wizard({ settings }: { settings: AdminWorldSettings }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const stepIndex = Math.max(0, setupSteps.indexOf(params.get('step') as SetupStep));
  const step: SetupStep = setupSteps[stepIndex] ?? 'start';
  // the stored settings when the wizard opened: what the presets start from
  const [base] = useState(() => setupFromSettings(settings));
  // a fresh start suggests the friends' round; coming back to a later step, or running the setup
  // again after it was finished or skipped, keeps what is stored until the host picks a preset
  const [preset, setPreset] = useState<SetupPreset | null>(
    () => parsePreset(params.get('preset')) ?? (stepIndex === 0 && !settings.setupDone ? 'friends' : null),
  );
  const [form, setForm] = useState<SetupForm>(() =>
    stepIndex === 0 && preset !== null ? applyPreset(base, preset) : base,
  );
  const lastSaved = useRef(JSON.stringify(setupToRequest(base)));
  const [savedDifficulty, setSavedDifficulty] = useState<NpcDifficulty>(settings.npcDifficulty);
  const [busy, setBusy] = useState(false);
  const [invalid, setInvalid] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const saveSettings = useUpdateWorldSettings();
  const saveDifficulty = useSetNpcDifficulty();
  const saveOpening = useSetWorldOpening();
  const saveEnd = useSetWorldEnd();
  const done = useSetupDone();

  function go(index: number) {
    const next: Record<string, string> = { step: setupSteps[index] ?? 'start' };
    if (preset !== null) {
      next.preset = preset;
    }
    setParams(next, { replace: true });
    window.scrollTo({ top: 0 });
  }

  /** Back to the pre-game page, at its top (the opening time and countdown), not where the wizard was scrolled to. */
  function leave() {
    void navigate('/');
    window.scrollTo({ top: 0 });
  }

  function pick(next: SetupPreset) {
    setPreset(next);
    setForm(applyPreset(base, next));
  }

  function update(patch: Partial<SetupForm>) {
    setForm({ ...form, ...patch });
    setInvalid(false);
  }

  /** Saves the settings (each step does, so nothing is lost on the way); false if that failed. */
  async function save(): Promise<boolean> {
    const request = setupToRequest(form);
    if (request === null) {
      setInvalid(true);
      return false;
    }
    setError(null);
    try {
      // an unchanged step saves nothing (and leaves no empty line in the audit log)
      const body = JSON.stringify(request);
      if (body !== lastSaved.current) {
        await saveSettings.mutateAsync(request);
        lastSaved.current = body;
      }
      if (form.npcDifficulty !== savedDifficulty) {
        await saveDifficulty.mutateAsync(form.npcDifficulty);
        setSavedDifficulty(form.npcDifficulty);
      }
      return true;
    } catch (failure) {
      setError(failure);
      return false;
    }
  }

  async function run(action: () => Promise<void>) {
    setBusy(true);
    try {
      await action();
    } finally {
      setBusy(false);
    }
  }

  const goTo = (index: number) =>
    run(async () => {
      if (await save()) {
        go(index);
      }
    });

  const finish = () =>
    run(async () => {
      if (!(await save())) {
        return;
      }
      try {
        const endsAt = localToInstant(form.endsAt);
        if (endsAt !== (settings.endsAt ?? null)) {
          await saveEnd.mutateAsync(endsAt);
        }
        const opensAt = localToInstant(form.opensAt);
        if (opensAt !== (settings.opensAt ?? null)) {
          await saveOpening.mutateAsync(opensAt);
        }
        await done.mutateAsync(true);
        leave();
      } catch (failure) {
        setError(failure);
      }
    });

  const skip = () =>
    run(async () => {
      try {
        await done.mutateAsync(false);
        leave();
      } catch (failure) {
        setError(failure);
      }
    });

  const last = stepIndex === setupSteps.length - 1;
  return (
    <div className={styles.page}>
      <nav className={styles.stepper} aria-label={t.setup.stepsLabel}>
        <p className={styles.kicker}>{t.setup.title}</p>
        <ol className={styles.stepList}>
          {setupSteps.map((value, index) => (
            <li key={value}>
              <button
                type="button"
                className={styles.stepItem}
                aria-current={index === stepIndex ? 'step' : undefined}
                data-done={index < stepIndex || undefined}
                disabled={busy}
                onClick={() => void goTo(index)}
              >
                <span className={styles.stepNumber} aria-hidden="true">
                  {index < stepIndex ? '✓' : index + 1}
                </span>
                {t.setup.steps[value]}
              </button>
            </li>
          ))}
        </ol>
      </nav>
      <section className={styles.panel} aria-labelledby="setup-step-title">
        <header className={styles.header}>
          <p className={styles.kicker}>
            {t.setup.title} · {t.setup.stepOf(stepIndex + 1, setupSteps.length)}
          </p>
          <div className={styles.progress} aria-hidden="true">
            {setupSteps.map((value, index) => (
              <span key={value} className={styles.progressPart} data-filled={index <= stepIndex || undefined} />
            ))}
          </div>
          <h1 id="setup-step-title" className={styles.title}>
            {t.setup.steps[step]}
          </h1>
          <p className={styles.intro}>{t.setup.intros[step]}</p>
        </header>
        <div className={styles.body}>
          {step === 'start' && <StartStep base={base} preset={preset} onPick={pick} />}
          {step === 'basics' && <BasicsStep form={form} update={update} />}
          {step === 'pace' && <PaceStep form={form} update={update} />}
          {step === 'map' && <MapStep form={form} update={update} stored={settings} />}
          {step === 'rules' && <RulesStep form={form} update={update} stored={settings} />}
          {step === 'victory' && <VictoryStep form={form} update={update} />}
          {step === 'opening' && (
            <OpeningStep form={form} preset={preset} update={update} onChange={(index) => void goTo(index)} />
          )}
          {invalid && <ErrorMessage>{t.setup.invalid}</ErrorMessage>}
          {error !== null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
        </div>
        <footer className={styles.actions}>
          {stepIndex > 0 ? (
            <Button variant="secondary" disabled={busy} onClick={() => go(stepIndex - 1)}>
              {t.setup.back}
            </Button>
          ) : (
            <button type="button" className={styles.quiet} disabled={busy} onClick={() => void skip()}>
              {t.setup.skip}
            </button>
          )}
          <Button disabled={busy} onClick={() => void (last ? finish() : goTo(stepIndex + 1))}>
            {last ? t.setup.finish : t.setup.next}
          </Button>
        </footer>
      </section>
    </div>
  );
}

interface StepProps {
  form: SetupForm;
  update: (patch: Partial<SetupForm>) => void;
}

type TextKey = {
  [K in keyof SetupForm]: SetupForm[K] extends string ? K : never;
}[keyof SetupForm];

function numberField(
  form: SetupForm,
  update: StepProps['update'],
  name: TextKey,
  label: string,
  props: { step?: string; min?: string; max?: string; hint?: string } = {},
) {
  return {
    label,
    type: 'number',
    inputMode: 'decimal' as const,
    value: form[name],
    onChange: (event: ChangeEvent<HTMLInputElement>) => update({ [name]: event.target.value }),
    ...props,
  };
}

function StartStep({
  base,
  preset,
  onPick,
}: {
  base: SetupForm;
  preset: SetupPreset | null;
  onPick: (preset: SetupPreset) => void;
}) {
  const { t } = useI18n();
  return (
    <div className={styles.presets} role="radiogroup" aria-label={t.setup.steps.start}>
      {setupPresets.map((value) => {
        const facts = presetFacts(base, value);
        return (
          <label key={value} className={styles.preset} data-picked={value === preset || undefined}>
            <input
              type="radio"
              name="preset"
              className={styles.presetRadio}
              checked={value === preset}
              onChange={() => onPick(value)}
            />
            <span className={styles.presetName}>{t.setup.presets[value].name}</span>
            <span className={styles.presetText}>{t.setup.presets[value].text}</span>
            <span className={styles.chips}>
              <span className={styles.chip}>{t.setup.facts.speed(facts.speed)}</span>
              <span className={styles.chip}>{t.setup.facts.protection(facts.protectionHours)}</span>
              <span className={styles.chip}>{t.setup.facts.lords(facts.lords)}</span>
            </span>
          </label>
        );
      })}
    </div>
  );
}

function BasicsStep({ form, update }: StepProps) {
  const { t } = useI18n();
  return (
    <>
      <TextField
        label={t.setup.name}
        value={form.name}
        maxLength={64}
        onChange={(event) => update({ name: event.target.value })}
      />
      <div className={styles.withAction}>
        <TextField
          label={t.setup.inviteCode}
          hint={t.setup.inviteHint}
          value={form.inviteCode}
          maxLength={64}
          onChange={(event) => update({ inviteCode: event.target.value })}
        />
        <Button variant="secondary" size="small" onClick={() => update({ inviteCode: newInviteCode() })}>
          {t.setup.newCode}
        </Button>
      </div>
      <div className={styles.setting}>
        <span className={styles.settingLabel}>{t.setup.villageNames}</span>
        <SegmentedControl
          label={t.setup.villageNames}
          options={villageNameLanguages.map((value) => {
            const lang = value === 'DE' ? 'de' : 'en';
            return { value, label: languageNames[lang], lang };
          })}
          value={form.villageNameLanguage}
          onChange={(value) => update({ villageNameLanguage: value })}
        />
      </div>
    </>
  );
}

function PaceStep({ form, update }: StepProps) {
  const { t } = useI18n();
  const nobles = noblesFromDays(form.speed);
  return (
    <>
      <div className={`${styles.fields} ${styles.wide}`}>
        <TextField {...numberField(form, update, 'speed', t.setup.speed, { step: '0.5', min: '0.1', max: '100' })} />
        <TextField
          {...numberField(form, update, 'unitSpeed', t.setup.unitSpeed, {
            step: '0.1',
            min: '0.1',
            max: '100',
            hint: t.setup.unitSpeedHint,
          })}
        />
      </div>
      {nobles !== null && <p className={styles.note}>{t.setup.paceHint(nobles[0], nobles[1])}</p>}
      <div className={`${styles.fields} ${styles.wide}`}>
        <TextField
          {...numberField(form, update, 'beginnerProtectionHours', t.setup.protection, {
            step: '1',
            min: '0',
            max: '720',
            hint: t.setup.protectionHint,
          })}
        />
        <TextField
          {...numberField(form, update, 'startResources', t.setup.startResources, {
            step: '50',
            min: '0',
            max: '1000000',
          })}
        />
      </div>
    </>
  );
}

function SwitchRow({
  label,
  hint,
  checked,
  onChange,
  children,
}: {
  label: string;
  hint: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  children?: ReactNode;
}) {
  return (
    <li>
      <label className={styles.switchRow}>
        <span className={styles.switchText}>
          <span className={styles.switchName}>{label}</span>
          <span className={styles.switchHint}>{hint}</span>
        </span>
        <input
          type="checkbox"
          role="switch"
          className={styles.switch}
          checked={checked}
          onChange={(event) => onChange(event.target.checked)}
        />
      </label>
      {checked && children !== undefined && <div className={styles.switchMore}>{children}</div>}
    </li>
  );
}

function MapStep({ form, update, stored }: StepProps & { stored: AdminWorldSettings }) {
  const { t } = useI18n();
  // CUSTOM is set up on the admin page; the wizard offers it only when it is already chosen
  const difficulties: readonly NpcDifficulty[] =
    stored.npcDifficulty === 'CUSTOM' ? ['EASY', 'NORMAL', 'HARD', 'CUSTOM'] : ['EASY', 'NORMAL', 'HARD'];
  return (
    <>
      <div className={styles.setting}>
        <span className={styles.settingLabel}>{t.setup.mapSpread}</span>
        <SegmentedControl
          label={t.setup.mapSpread}
          options={mapSpreads.map((value) => ({ value, label: t.worldInfo.mapSpreads[value] }))}
          value={form.mapSpread}
          onChange={(value) => update(withSpread(form, value))}
        />
        <p className={styles.hint}>
          {t.worldInfo.mapSpreadValue(
            t.worldInfo.mapSpreads[form.mapSpread],
            t.worldInfo.mapSpreadDistances[form.mapSpread],
          )}{' '}
          {t.setup.mapSpreadHint}
        </p>
      </div>
      <div className={`${styles.fields} ${styles.fieldsEnd}`}>
        <TextField {...numberField(form, update, 'mapSize', t.setup.mapSize, { step: '10', min: '20', max: '500' })} />
        <TextField
          {...numberField(form, update, 'nobleMaxDistance', t.setup.nobleMaxDistance, {
            step: '1',
            min: '1',
            max: '1000',
          })}
        />
      </div>
      <h2 className={styles.group}>{t.setup.barbarians}</h2>
      <div className={styles.fields}>
        <TextField
          {...numberField(form, update, 'barbarianInitialCount', t.setup.barbarianInitialCount, {
            step: '5',
            min: '0',
            max: '5000',
          })}
        />
        <TextField
          {...numberField(form, update, 'barbariansPerNewPlayer', t.setup.barbariansPerNewPlayer, {
            step: '1',
            min: '0',
            max: '20',
          })}
        />
      </div>
      <ul className={styles.switches}>
        <SwitchRow
          label={t.setup.barbarianGrowth}
          hint={t.setup.barbarianGrowthHint}
          checked={form.barbarianGrowthEnabled}
          onChange={(value) => update({ barbarianGrowthEnabled: value })}
        />
        <SwitchRow
          label={t.setup.barbarianWalls}
          hint={t.setup.barbarianWallsHint}
          checked={form.barbarianWallsEnabled}
          onChange={(value) => update({ barbarianWallsEnabled: value })}
        />
      </ul>
      <h2 className={styles.group}>{t.setup.lords}</h2>
      <div className={styles.fields}>
        <TextField
          {...numberField(form, update, 'npcLordCount', t.setup.npcLordCount, { step: '1', min: '0', max: '50' })}
        />
      </div>
      <div className={styles.setting}>
        <span className={styles.settingLabel}>{t.setup.difficulty}</span>
        <SegmentedControl
          label={t.setup.difficulty}
          options={difficulties.map((value) => ({ value, label: t.setup.difficulties[value] }))}
          value={form.npcDifficulty}
          onChange={(value) => update({ npcDifficulty: value })}
        />
      </div>
      <ul className={styles.switches}>
        <SwitchRow
          label={t.setup.npcGuilds}
          hint={t.setup.npcGuildsHint}
          checked={form.npcGuildsEnabled}
          onChange={(value) => update({ npcGuildsEnabled: value })}
        >
          <div className={styles.fields}>
            <TextField
              {...numberField(form, update, 'npcGuildMaxMembers', t.setup.npcGuildMaxMembers, {
                step: '1',
                min: '2',
                max: '50',
              })}
            />
          </div>
        </SwitchRow>
        <SwitchRow
          label={t.setup.npcBanners}
          hint={t.setup.npcBannersHint}
          checked={form.npcBannersEnabled}
          onChange={(value) => update({ npcBannersEnabled: value })}
        />
      </ul>
    </>
  );
}

/** The paladin's slots, the market's rate and the noble price step when the host switches them back on: the stored ones, or the defaults. */
const PALADIN_SLOTS_DEFAULT = '5';
const MARKET_RATIO_DEFAULT = '1.5';
const NOBLE_PRICE_STEP_DEFAULT = '0.25';

/** A noble price step (0.25) as the percent the hints show ("25"). */
function stepPercent(step: string): string {
  return String(Math.round(Number(step) * 100));
}

function RulesStep({ form, update, stored }: StepProps & { stored: AdminWorldSettings }) {
  const { t } = useI18n();
  const paladinOn = stored.paladinMaxSlots > 0 ? String(stored.paladinMaxSlots) : PALADIN_SLOTS_DEFAULT;
  const marketOn = stored.marketExchangeRatio > 0 ? String(stored.marketExchangeRatio) : MARKET_RATIO_DEFAULT;
  const noblePriceOn = stored.noblePriceStep > 0 ? String(stored.noblePriceStep) : NOBLE_PRICE_STEP_DEFAULT;
  const noblePriceRising = Number(form.noblePriceStep) > 0;
  return (
    <ul className={styles.switches}>
      <SwitchRow
        label={t.setup.guilds}
        hint={t.setup.guildsHint}
        checked={form.guildsEnabled}
        onChange={(value) => update({ guildsEnabled: value })}
      >
        <div className={styles.fields}>
          <TextField
            {...numberField(form, update, 'guildMaxMembers', t.setup.guildMaxMembers, {
              step: '1',
              min: '2',
              max: '50',
            })}
          />
        </div>
        <ul className={styles.switches}>
          <SwitchRow
            label={t.setup.guildStartTogether}
            hint={t.setup.guildStartTogetherHint}
            checked={form.guildStartTogether}
            onChange={(value) => update({ guildStartTogether: value })}
          />
        </ul>
      </SwitchRow>
      <SwitchRow
        label={t.setup.morale}
        hint={t.setup.moraleHint}
        checked={form.moraleEnabled}
        onChange={(value) => update({ moraleEnabled: value })}
      >
        <div className={styles.fields}>
          <TextField
            {...numberField(form, update, 'moraleFloor', t.setup.moraleFloor, {
              step: '5',
              min: '30',
              max: '100',
              hint: t.setup.moraleFloorHint,
            })}
          />
        </div>
      </SwitchRow>
      <SwitchRow
        label={t.setup.noblePrice}
        hint={t.setup.noblePriceHint(stepPercent(noblePriceRising ? form.noblePriceStep : noblePriceOn))}
        checked={noblePriceRising}
        onChange={(value) => update({ noblePriceStep: value ? noblePriceOn : '0' })}
      />
      <SwitchRow
        label={t.setup.unitLevels}
        hint={t.setup.unitLevelsHint}
        checked={form.unitLevelsEnabled}
        onChange={(value) => update({ unitLevelsEnabled: value })}
      />
      <SwitchRow
        label={t.setup.archers}
        hint={t.setup.archersHint}
        checked={form.archersEnabled}
        onChange={(value) => update({ archersEnabled: value })}
      />
      <SwitchRow
        label={t.setup.paladin}
        hint={t.setup.paladinHint}
        checked={Number(form.paladinMaxSlots) > 0}
        onChange={(value) => update({ paladinMaxSlots: value ? paladinOn : '0' })}
      />
      <SwitchRow
        label={t.setup.banners}
        hint={t.setup.bannersHint}
        checked={form.bannersEnabled}
        onChange={(value) => update({ bannersEnabled: value })}
      />
      <SwitchRow
        label={t.setup.standardTheft}
        hint={t.setup.standardTheftHint}
        checked={form.standardTheftEnabled}
        onChange={(value) => update({ standardTheftEnabled: value })}
      />
      <SwitchRow
        label={t.setup.nightBonus}
        hint={t.setup.nightBonusHint(
          form.nightBonusStartHour.padStart(2, '0'),
          form.nightBonusEndHour.padStart(2, '0'),
        )}
        checked={form.nightBonusEnabled}
        onChange={(value) => update({ nightBonusEnabled: value })}
      />
      <SwitchRow
        label={t.setup.market}
        hint={t.setup.marketHint}
        checked={Number(form.marketExchangeRatio) > 0}
        onChange={(value) => update({ marketExchangeRatio: value ? marketOn : '0' })}
      />
    </ul>
  );
}

function VictoryStep({ form, update }: StepProps) {
  const { t } = useI18n();
  const modes: readonly VictoryMode[] = ['DOMINATION', 'KEEPS', 'NONE'];
  return (
    <>
      <div className={styles.setting}>
        <span className={styles.settingLabel}>{t.setup.victoryMode}</span>
        <SegmentedControl
          label={t.setup.victoryMode}
          options={modes.map((value) => ({ value, label: t.setup.victoryModes[value] }))}
          value={form.victoryMode}
          onChange={(value) => update({ victoryMode: value })}
        />
        <p className={styles.hint}>{t.setup.victoryHints[form.victoryMode]}</p>
      </div>
      {form.victoryMode !== 'NONE' && (
        <div className={styles.fields}>
          {form.victoryMode === 'DOMINATION' ? (
            <TextField
              {...numberField(form, update, 'dominationPercent', t.setup.dominationPercent, {
                step: '5',
                min: '10',
                max: '100',
              })}
            />
          ) : (
            <TextField
              {...numberField(form, update, 'keepCount', t.setup.keepCount, { step: '1', min: '1', max: '12' })}
            />
          )}
          <TextField
            {...numberField(form, update, 'victoryHoldHours', t.setup.victoryHoldHours, {
              step: '1',
              min: '1',
              max: '720',
            })}
          />
        </div>
      )}
      <TextField
        label={t.setup.endsAt}
        hint={t.setup.endsAtHint}
        type="datetime-local"
        value={form.endsAt}
        onChange={(event) => update({ endsAt: event.target.value })}
      />
    </>
  );
}

function OpeningStep({
  form,
  preset,
  update,
  onChange,
}: StepProps & { preset: SetupPreset | null; onChange: (index: number) => void }) {
  const { t } = useI18n();
  const status = useAdminStatus();
  const auth = useAuthOptions();
  const onOff = (value: boolean) => (value ? t.setup.on : t.setup.off);
  const groups: { step: SetupStep; rows: [string, string][] }[] = [
    { step: 'start', rows: [[t.setup.steps.start, preset === null ? '–' : t.setup.presets[preset].name]] },
    {
      step: 'basics',
      rows: [
        [t.setup.name, form.name],
        [t.setup.inviteCode, form.inviteCode],
      ],
    },
    {
      step: 'pace',
      rows: [
        [t.setup.speed, form.speed],
        [t.setup.protection, form.beginnerProtectionHours],
      ],
    },
    {
      step: 'map',
      rows: [
        [t.setup.mapSpread, t.worldInfo.mapSpreads[form.mapSpread]],
        [t.setup.mapSize, `${form.mapSize} × ${form.mapSize}`],
        [t.setup.nobleMaxDistance, form.nobleMaxDistance],
        [t.setup.barbarians, form.barbarianInitialCount],
        [t.setup.lords, `${form.npcLordCount} · ${t.setup.difficulties[form.npcDifficulty]}`],
        [t.setup.npcGuilds, onOff(form.npcGuildsEnabled)],
      ],
    },
    {
      step: 'rules',
      rows: [
        [
          t.setup.guilds,
          form.guildsEnabled && form.guildStartTogether ? t.setup.guildsStartTogether : onOff(form.guildsEnabled),
        ],
        [t.setup.morale, form.moraleEnabled ? t.setup.moraleValue(form.moraleFloor) : t.setup.off],
        [
          t.setup.noblePriceShort,
          Number(form.noblePriceStep) > 0 ? t.setup.noblePriceValue(stepPercent(form.noblePriceStep)) : t.setup.off,
        ],
        [t.setup.archers, onOff(form.archersEnabled)],
        [t.setup.banners, onOff(form.bannersEnabled)],
        [t.setup.nightBonus, onOff(form.nightBonusEnabled)],
      ],
    },
    { step: 'victory', rows: [[t.setup.victoryMode, t.setup.victoryModes[form.victoryMode]]] },
  ];
  return (
    <>
      <TextField
        label={t.setup.opensAt}
        hint={t.setup.opensAtHint}
        type="datetime-local"
        value={form.opensAt}
        onChange={(event) => update({ opensAt: event.target.value })}
      />
      <h2 className={styles.group}>{t.setup.summary}</h2>
      <div className={styles.summary}>
        {groups.map((group) => (
          <section key={group.step} className={styles.summaryGroup}>
            <div className={styles.summaryHead}>
              <h3>{t.setup.steps[group.step]}</h3>
              <button type="button" className={styles.quiet} onClick={() => onChange(setupSteps.indexOf(group.step))}>
                {t.setup.change}
              </button>
            </div>
            <dl className={styles.facts}>
              {group.rows.map(([label, value]) => (
                <div key={label} className={styles.fact}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
          </section>
        ))}
        <section className={styles.summaryGroup}>
          <div className={styles.summaryHead}>
            <h3>{t.setup.server}</h3>
          </div>
          <p className={styles.hint}>{t.setup.serverHint}</p>
          <dl className={styles.facts}>
            <div className={styles.fact}>
              <dt>{t.setup.loginMode}</dt>
              <dd>{auth.data === undefined ? '–' : t.setup.loginModes[auth.data.mode]}</dd>
            </div>
            <div className={styles.fact}>
              <dt>{t.setup.push}</dt>
              <dd>{status.data === undefined ? '–' : onOff(status.data.push.enabled)}</dd>
            </div>
          </dl>
        </section>
      </div>
    </>
  );
}

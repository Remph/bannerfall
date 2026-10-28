import { useMemo, useRef, useState, type ChangeEvent, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import {
  useAdminWorldSettings,
  useSetWorldEnd,
  useUpdateWorldSettings,
  type AdminWorldSettings,
} from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { TextField } from '../../components/TextField';
import { useShowToast } from '../../components/toasts';
import { formatDateTime } from '../../game/format';
import { keepsNeeded } from '../../game/victory';
import { useI18n } from '../../i18n';
import { languageNames } from '../../i18n/language';
import styles from './WorldTab.module.css';
import { BarbarianCard } from './BarbarianCard';
import { EndedForm, EndNow } from './WorldEnd';
import { VICTORY_MODES, victoryRequest } from './victoryForm';
import { mapSpreads, villageNameLanguages, withSpread } from './worldForm';
import {
  changedKeys,
  changedRows,
  changedSections,
  endsAtRequest,
  invalidRows,
  numberSwitchOn,
  parseSection,
  switchedOnValue,
  worldRows,
  worldSections,
  worldTabRequest,
  worldTabToForm,
  type NumberSwitch,
  type WorldRow,
  type WorldSection,
  type WorldTabForm,
  type WorldTabKey,
} from './worldTabForm';

/**
 * The admin's World tab (1.6): the world settings in the setup wizard's five sections – a switch at
 * the top on phones, a list at the left on desktop – with one save bar for all of them. The opening
 * time stays above them and saves on its own, like "End now".
 */
export function WorldTab({ opening }: { opening: ReactNode }) {
  const { t } = useI18n();
  const settings = useAdminWorldSettings();
  return (
    <>
      {opening}
      {settings.isError && <ErrorMessage>{errorMessage(t, settings.error)}</ErrorMessage>}
      {settings.isPending && <p>{t.common.loading}</p>}
      {settings.data !== undefined && <WorldEditor settings={settings.data} />}
    </>
  );
}

type TextKey = {
  [K in WorldTabKey]: WorldTabForm[K] extends string ? K : never;
}[WorldTabKey];

interface FieldProps {
  step?: string;
  min?: string;
  max?: string;
  hint?: string;
  maxLength?: number;
}

/** What a section needs to draw its rows: the draft, how to change it, and which rows are changed or wrong. */
interface Editor {
  settings: AdminWorldSettings;
  form: WorldTabForm;
  update: (patch: Partial<WorldTabForm>) => void;
  changed: (row: WorldRow) => boolean;
  invalid: (row: WorldRow) => boolean;
  keyChanged: (key: WorldTabKey) => boolean;
  /** Marks the end date field as holding a half-typed time, which the browser reports as empty. */
  setEndIncomplete: (incomplete: boolean) => void;
}

function WorldEditor({ settings }: { settings: AdminWorldSettings }) {
  const { t } = useI18n();
  const labels = t.admin.worldTab;
  const showToast = useShowToast();
  const [params, setParams] = useSearchParams();
  const section = parseSection(params.get('section'));
  const heading = useRef<HTMLHeadingElement>(null);
  // only what the host changed; the rest follows the stored settings (also when another admin saves)
  const [draft, setDraft] = useState<Partial<WorldTabForm>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [endIncomplete, setEndIncomplete] = useState(false);
  const saveSettings = useUpdateWorldSettings();
  const saveEnd = useSetWorldEnd();

  const stored = useMemo(() => worldTabToForm(settings), [settings]);
  const form: WorldTabForm = { ...stored, ...draft };
  const keys = changedKeys(stored, form);
  const rows = changedRows(keys);
  const dirty = changedSections(rows);
  const checked = rows.length > 0 ? invalidRows(settings, form) : [];
  // a half-typed end date must not save as "no end date"
  const wrong: WorldRow[] =
    endIncomplete && rows.includes('endsAt') && !checked.includes('endsAt') ? [...checked, 'endsAt'] : checked;

  const editor: Editor = {
    settings,
    form,
    update: (patch) => {
      setDraft((current) => ({ ...current, ...patch }));
      setFailure(null);
    },
    changed: (row) => rows.includes(row),
    invalid: (row) => wrong.includes(row),
    keyChanged: (key) => keys.includes(key),
    setEndIncomplete,
  };

  function open(next: WorldSection) {
    setParams(
      (current) => {
        current.set('section', next);
        return current;
      },
      { replace: true },
    );
    // a section switched further down the page starts at its title
    const top = heading.current?.getBoundingClientRect().top ?? 0;
    if (top < 0) {
      heading.current?.scrollIntoView({ block: 'start' });
    }
  }

  function forget(saved: readonly WorldTabKey[]) {
    setDraft((current) =>
      Object.fromEntries(Object.entries(current).filter(([key]) => !saved.includes(key as WorldTabKey))),
    );
  }

  /** Saves the settings (one request), then the end date (its own); says which part failed if one did. */
  async function save() {
    const request = worldTabRequest(settings, form);
    const endsAt = endsAtRequest(form);
    if (request === null || endsAt === undefined || wrong.length > 0) {
      return;
    }
    const settingsKeys = keys.filter((key) => key !== 'endsAt');
    const endChanged = keys.includes('endsAt');
    setBusy(true);
    setFailure(null);
    const settingsError =
      settingsKeys.length === 0 ? null : await attempt(() => saveSettings.mutateAsync(request), settingsKeys);
    const endError = endChanged ? await attempt(() => saveEnd.mutateAsync(endsAt), ['endsAt']) : null;
    setBusy(false);
    if (settingsError === null && endError === null) {
      showToast({ text: labels.saved, tone: 'success' });
    } else if (settingsError !== null && (endError !== null || !endChanged)) {
      setFailure(labels.failed(errorMessage(t, settingsError)));
    } else if (settingsError !== null) {
      setFailure(labels.settingsFailed(errorMessage(t, settingsError)));
    } else if (endError !== null) {
      setFailure(
        settingsKeys.length === 0
          ? labels.failed(errorMessage(t, endError))
          : labels.endFailed(errorMessage(t, endError)),
      );
    }
  }

  /** Runs one request; on success the saved fields leave the draft. The error, or null. */
  async function attempt(request: () => Promise<unknown>, saved: readonly WorldTabKey[]): Promise<unknown> {
    try {
      await request();
      forget(saved);
      return null;
    } catch (error) {
      return error ?? new Error('failed');
    }
  }

  const summary = dirty
    .map((part) =>
      labels.where(
        labels.sections[part],
        rows
          .filter((row) => worldRows[row].section === part)
          .map((row) => labels.rows[row])
          .join(', '),
      ),
    )
    .join(' · ');

  return (
    <div className={styles.world}>
      <nav className={styles.sections} aria-label={labels.sectionsLabel}>
        <p className={styles.sectionsTitle}>{labels.sectionsLabel}</p>
        {worldSections.map((value) => (
          <button
            key={value}
            type="button"
            className={styles.section}
            aria-current={value === section ? 'true' : undefined}
            onClick={() => open(value)}
          >
            <span className={styles.short}>{labels.short[value]}</span>
            <span className={styles.full}>{labels.sections[value]}</span>
            {dirty.includes(value) && (
              <>
                <span className={styles.dot} aria-hidden="true" />
                <span className="sr-only">{labels.unsaved}</span>
              </>
            )}
          </button>
        ))}
      </nav>
      <div className={styles.content}>
        <h2 ref={heading} className={styles.title}>
          {labels.sections[section]}
        </h2>
        <p className={styles.intro}>{labels.intros[section]}</p>
        {section === 'basics' && <BasicsSection editor={editor} />}
        {section === 'pace' && <PaceSection editor={editor} />}
        {section === 'map' && <MapSection editor={editor} />}
        {section === 'rules' && <RulesSection editor={editor} />}
        {section === 'victory' && <VictorySection editor={editor} />}
        {(rows.length > 0 || failure !== null) && (
          <div className={styles.saveBar} role="region" aria-label={labels.changes(rows.length)}>
            {rows.length > 0 && (
              <div className={styles.what}>
                <strong>
                  <span className={styles.dot} aria-hidden="true" /> {labels.changes(rows.length)}
                </strong>
                <span className={styles.which}>{summary}</span>
              </div>
            )}
            {rows.length > 0 && (
              <div className={styles.barActions}>
                <Button
                  type="button"
                  variant="secondary"
                  size="small"
                  disabled={busy}
                  onClick={() => {
                    setDraft({});
                    setFailure(null);
                    setEndIncomplete(false);
                  }}
                >
                  {labels.discard}
                </Button>
                <Button type="button" size="small" disabled={busy || wrong.length > 0} onClick={() => void save()}>
                  {t.common.save}
                </Button>
              </div>
            )}
            {wrong.length > 0 && (
              <p className={styles.wrong}>{labels.invalid(wrong.map((row) => labels.rows[row]).join(', '))}</p>
            )}
            {failure !== null && <p className={styles.wrong}>{failure}</p>}
          </div>
        )}
      </div>
    </div>
  );
}

/** A number field of the draft, marked while it differs from the stored value or its row is wrong. */
function Field({
  editor,
  name,
  label,
  row,
  type = 'number',
  onEdit,
  ...props
}: FieldProps & {
  editor: Editor;
  name: TextKey;
  label: string;
  row: WorldRow;
  type?: string;
  onEdit?: (input: HTMLInputElement) => void;
}) {
  return (
    <div
      className={styles.field}
      data-changed={editor.keyChanged(name) || undefined}
      data-invalid={editor.invalid(row) || undefined}
    >
      <TextField
        label={label}
        type={type}
        inputMode={type === 'number' ? 'decimal' : undefined}
        value={editor.form[name]}
        onChange={(event: ChangeEvent<HTMLInputElement>) => {
          editor.update({ [name]: event.target.value });
          onEdit?.(event.target);
        }}
        {...props}
      />
    </div>
  );
}

/** One switch with its hint, and its numbers under it while it is on. */
function SwitchRow({
  editor,
  row,
  hint,
  checked,
  onChange,
  label,
  children,
}: {
  editor: Editor;
  row: WorldRow;
  hint?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: string;
  children?: ReactNode;
}) {
  const { t } = useI18n();
  // a number switch reads its number: typing "0.3" passes through "0" (off), which must not fold
  // the field away under the cursor, so the numbers stay while the focus is in them
  const [typing, setTyping] = useState(false);
  return (
    <div className={styles.row} data-changed={editor.changed(row) || undefined}>
      <label className={styles.switchRow}>
        <span className={styles.switchText}>
          <span className={styles.switchName}>{label ?? t.admin.worldTab.rows[row]}</span>
          {hint !== undefined && <span className={styles.hint}>{hint}</span>}
        </span>
        <input
          type="checkbox"
          role="switch"
          className={styles.switch}
          checked={checked}
          onChange={(event) => onChange(event.target.checked)}
        />
      </label>
      {(checked || typing) && children !== undefined && (
        <div
          className={styles.nested}
          onFocus={() => setTyping(true)}
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) {
              setTyping(false);
            }
          }}
        >
          {children}
        </div>
      )}
    </div>
  );
}

/** A boolean setting as a switch row. */
function Toggle({
  editor,
  row,
  name,
  hint,
  label,
  children,
}: {
  editor: Editor;
  row: WorldRow;
  name: {
    [K in WorldTabKey]: WorldTabForm[K] extends boolean ? K : never;
  }[WorldTabKey];
  hint?: string;
  label?: string;
  children?: ReactNode;
}) {
  return (
    <SwitchRow
      editor={editor}
      row={row}
      hint={hint}
      label={label}
      checked={editor.form[name]}
      onChange={(value) => editor.update({ [name]: value })}
    >
      {children}
    </SwitchRow>
  );
}

/** A number that is off at 0 (paladin slots, market rate, noble price step, automation) as a switch row. */
function NumberToggle({
  editor,
  row,
  name,
  hint,
  children,
}: {
  editor: Editor;
  row: WorldRow;
  name: NumberSwitch;
  hint: string;
  children?: ReactNode;
}) {
  return (
    <SwitchRow
      editor={editor}
      row={row}
      hint={hint}
      checked={numberSwitchOn(editor.form[name])}
      onChange={(value) => editor.update({ [name]: value ? switchedOnValue(editor.settings, name) : '0' })}
    >
      {children}
    </SwitchRow>
  );
}

/** Fields side by side inside a row or a card. */
function Fields({ wide = false, children }: { wide?: boolean; children: ReactNode }) {
  return <div className={wide ? `${styles.fields} ${styles.wide}` : styles.fields}>{children}</div>;
}

/** Plain fields in a card, padded like the switch rows. */
function Plain({ children }: { children: ReactNode }) {
  return <div className={styles.plain}>{children}</div>;
}

/** Rarely changed numbers, folded away; the summary names them and marks changes inside. */
function More({ editor, rows, children }: { editor: Editor; rows: readonly WorldRow[]; children: ReactNode }) {
  const { t } = useI18n();
  const labels = t.admin.worldTab;
  const changed = rows.some((row) => editor.changed(row));
  return (
    <details className={styles.more}>
      <summary className={styles.moreSummary}>
        <span>
          {labels.more}
          <span className={styles.moreNames}> · {rows.map((row) => labels.rows[row]).join(', ')}</span>
        </span>
        {changed && <span className={styles.dot} aria-hidden="true" />}
      </summary>
      <div className={styles.moreBody}>{children}</div>
    </details>
  );
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card title={title}>
      <div className={styles.rows}>{children}</div>
    </Card>
  );
}

/** Two columns of cards on desktop, one after the other on phones. */
function Columns({ left, right }: { left: ReactNode; right: ReactNode }) {
  return (
    <div className={styles.columns}>
      <div className={styles.column}>{left}</div>
      <div className={styles.column}>{right}</div>
    </div>
  );
}

function BasicsSection({ editor }: { editor: Editor }) {
  const { t } = useI18n();
  const labels = t.admin.worldTab;
  const pregame = editor.settings.phase === 'PREGAME';
  return (
    <Group title={labels.groups.world}>
      <Plain>
        {pregame && (
          <p>
            <Link to="/setup">{t.admin.world.setupLink}</Link>
          </p>
        )}
        <Fields>
          <Field editor={editor} row="name" name="name" type="text" label={labels.rows.name} maxLength={64} />
          <Field
            editor={editor}
            row="inviteCode"
            name="inviteCode"
            type="text"
            label={labels.rows.inviteCode}
            hint={t.setup.inviteHint}
            maxLength={64}
          />
        </Fields>
        <div className={styles.setting} data-changed={editor.changed('villageNameLanguage') || undefined}>
          <span className={styles.settingLabel}>{labels.rows.villageNameLanguage}</span>
          <SegmentedControl
            label={labels.rows.villageNameLanguage}
            options={villageNameLanguages.map((value) => {
              const lang = value === 'DE' ? 'de' : 'en';
              return { value, label: languageNames[lang], lang };
            })}
            value={editor.form.villageNameLanguage}
            onChange={(villageNameLanguage) => editor.update({ villageNameLanguage })}
          />
          <p className={styles.hint}>{t.admin.world.villageNameLanguageHint}</p>
        </div>
      </Plain>
      <Toggle editor={editor} row="tutorial" name="tutorialEnabled" hint={t.admin.world.tutorialHint} />
    </Group>
  );
}

function PaceSection({ editor }: { editor: Editor }) {
  const { t } = useI18n();
  const labels = t.admin.worldTab;
  const world = t.admin.world;
  return (
    <Group title={labels.groups.pace}>
      <Plain>
        <Fields>
          <Field editor={editor} row="speed" name="speed" label={world.speed} step="0.1" min="0.1" max="100" />
          <Field
            editor={editor}
            row="unitSpeed"
            name="unitSpeed"
            label={world.unitSpeed}
            hint={t.setup.unitSpeedHint}
            step="0.1"
            min="0.1"
            max="100"
          />
          <Field
            editor={editor}
            row="beginnerProtectionHours"
            name="beginnerProtectionHours"
            label={world.beginnerProtectionHours}
            hint={t.setup.protectionHint}
            step="1"
            min="0"
            max="720"
          />
          <Field
            editor={editor}
            row="startResources"
            name="startResources"
            label={world.startResources}
            step="50"
            min="0"
            max="1000000"
          />
        </Fields>
      </Plain>
      <More editor={editor} rows={['buildQueue', 'recruitQueueSize', 'cancelWindowSeconds']}>
        <Fields>
          <Field
            editor={editor}
            row="buildQueue"
            name="buildQueueSize"
            label={world.buildQueueSize}
            step="1"
            min="1"
            max="20"
          />
          <Field
            editor={editor}
            row="buildQueue"
            name="buildQueueMax"
            label={world.buildQueueMax}
            step="1"
            min="1"
            max="30"
          />
          <Field
            editor={editor}
            row="recruitQueueSize"
            name="recruitQueueSize"
            label={world.recruitQueueSize}
            step="1"
            min="1"
            max="20"
          />
          <Field
            editor={editor}
            row="cancelWindowSeconds"
            name="cancelWindowSeconds"
            label={world.cancelWindowSeconds}
            step="60"
            min="0"
            max="86400"
          />
        </Fields>
      </More>
    </Group>
  );
}

function MapSection({ editor }: { editor: Editor }) {
  const { t } = useI18n();
  const labels = t.admin.worldTab;
  const world = t.admin.world;
  const { settings, form } = editor;
  const pregame = settings.phase === 'PREGAME';
  const mapCard = (
    <Group title={labels.groups.map}>
      <Plain>
        {pregame ? (
          <>
            <p className={styles.hint}>{world.pregameHint}</p>
            <div className={styles.setting} data-changed={editor.changed('mapSpread') || undefined}>
              <span className={styles.settingLabel}>{world.mapSpread}</span>
              <SegmentedControl
                label={world.mapSpread}
                options={mapSpreads.map((value) => ({ value, label: t.worldInfo.mapSpreads[value] }))}
                value={form.mapSpread}
                onChange={(mapSpread) => editor.update(withSpread(form, mapSpread))}
              />
              <p className={styles.hint}>
                {t.worldInfo.mapSpreadValue(
                  t.worldInfo.mapSpreads[form.mapSpread],
                  t.worldInfo.mapSpreadDistances[form.mapSpread],
                )}{' '}
                {world.mapSpreadHint}
              </p>
            </div>
          </>
        ) : (
          <p className={styles.facts}>
            {world.fixed(
              settings.mapSize,
              t.worldInfo.mapSpreads[settings.mapSpread],
              settings.barbarianInitialCount,
              settings.npcLordCount,
            )}
          </p>
        )}
        <Fields>
          {pregame && (
            <Field editor={editor} row="mapSize" name="mapSize" label={world.mapSize} step="10" min="20" max="500" />
          )}
          <Field
            editor={editor}
            row="nobleMaxDistance"
            name="nobleMaxDistance"
            label={world.nobleMaxDistance}
            step="1"
            min="1"
            max="1000"
          />
        </Fields>
      </Plain>
    </Group>
  );
  const barbarians = (
    <Group title={labels.groups.barbarians}>
      <Plain>
        <Fields>
          {pregame && (
            <Field
              editor={editor}
              row="barbarianInitialCount"
              name="barbarianInitialCount"
              label={world.barbarianInitialCount}
              step="5"
              min="0"
              max="5000"
            />
          )}
          <Field
            editor={editor}
            row="barbariansPerNewPlayer"
            name="barbariansPerNewPlayer"
            label={world.barbariansPerNewPlayer}
            step="1"
            min="0"
            max="20"
          />
        </Fields>
      </Plain>
      <Toggle editor={editor} row="barbarianGrowth" name="barbarianGrowthEnabled" hint={t.setup.barbarianGrowthHint} />
      <Toggle editor={editor} row="barbarianWalls" name="barbarianWallsEnabled" hint={t.setup.barbarianWallsHint}>
        <p className={styles.hint}>{t.admin.barbarianWalls.hint}</p>
        <Fields>
          <Field
            editor={editor}
            row="barbarianWalls"
            name="barbarianWallStartDays"
            label={labels.fields.barbarianWallStartDays}
            step="1"
            min="0"
            max="365"
          />
          <Field
            editor={editor}
            row="barbarianWalls"
            name="barbarianWallMaxLevel"
            label={labels.fields.barbarianWallMaxLevel}
            step="1"
            min="0"
            max="20"
          />
        </Fields>
        <label className={styles.check}>
          <input
            type="checkbox"
            role="switch"
            className={styles.switch}
            checked={form.barbarianWallsRebuild}
            onChange={(event) => editor.update({ barbarianWallsRebuild: event.target.checked })}
          />
          {t.admin.barbarianWalls.barbarianWallsRebuild}
        </label>
      </Toggle>
    </Group>
  );
  const lords = (
    <Group title={labels.groups.lords}>
      {pregame && (
        <Plain>
          <Fields>
            <Field
              editor={editor}
              row="npcLordCount"
              name="npcLordCount"
              label={world.npcLordCount}
              step="1"
              min="0"
              max="50"
            />
          </Fields>
        </Plain>
      )}
      <Toggle
        editor={editor}
        row="npcGuilds"
        name="npcGuildsEnabled"
        hint={settings.npcGuildsEnabled && !form.npcGuildsEnabled ? world.npcGuildsOff : t.setup.npcGuildsHint}
      >
        <Fields>
          <Field
            editor={editor}
            row="npcGuilds"
            name="npcGuildMaxMembers"
            label={labels.fields.npcGuildMaxMembers}
            step="1"
            min="2"
            max="50"
          />
        </Fields>
        <p className={styles.hint}>{world.npcGuildsHint}</p>
      </Toggle>
      <Toggle editor={editor} row="npcLearning" name="npcLearningEnabled" hint={labels.npcLearningHint}>
        <p className={styles.hint}>{world.npcLearningHint}</p>
      </Toggle>
      <Toggle editor={editor} row="npcBanners" name="npcBannersEnabled" hint={t.setup.npcBannersHint}>
        <p className={styles.hint}>{world.npcBannersHint}</p>
      </Toggle>
      <Toggle editor={editor} row="npcShipments" name="npcShipmentsEnabled" hint={labels.npcShipmentsHint}>
        <p className={styles.hint}>{world.npcShipmentsHint}</p>
      </Toggle>
      <More editor={editor} rows={['npcThinkMinutes']}>
        <Fields>
          <Field
            editor={editor}
            row="npcThinkMinutes"
            name="npcThinkMinutes"
            label={world.npcThinkMinutes}
            step="1"
            min="1"
            max="1440"
          />
        </Fields>
      </More>
      <Link to="/admin?tab=lords" className={styles.link}>
        {labels.lordsLink}
      </Link>
    </Group>
  );
  return (
    <Columns
      left={
        <>
          {mapCard}
          {barbarians}
          {/* an action of its own, not a setting: there is no map before the opening */}
          {!pregame && <BarbarianCard />}
        </>
      }
      right={lords}
    />
  );
}

const PERCENT = 100;
/** Hours as the night bonus hint shows them ("07"). */
const HOUR_DIGITS = 2;

/** A noble price step (0.25) as the percent the hints show ("25"). */
function stepPercent(step: string): string {
  return String(Math.round(Number(step) * PERCENT));
}

function RulesSection({ editor }: { editor: Editor }) {
  const { t } = useI18n();
  const labels = t.admin.worldTab;
  const fields = labels.fields;
  const world = t.admin.world;
  const { settings, form } = editor;
  const noblePercent = stepPercent(
    numberSwitchOn(form.noblePriceStep) ? form.noblePriceStep : switchedOnValue(settings, 'noblePriceStep'),
  );
  const guilds = (
    <Group title={labels.groups.guilds}>
      <Toggle editor={editor} row="guilds" name="guildsEnabled" hint={t.setup.guildsHint}>
        <Fields>
          <Field
            editor={editor}
            row="guilds"
            name="guildMaxMembers"
            label={fields.guildMaxMembers}
            step="1"
            min="2"
            max="50"
          />
        </Fields>
      </Toggle>
      <Toggle editor={editor} row="guildStartTogether" name="guildStartTogether" hint={world.guildStartTogetherHint} />
      <Toggle editor={editor} row="guildBanners" name="guildBannersEnabled" hint={world.guildBannersHint} />
    </Group>
  );
  const combat = (
    <Group title={labels.groups.combat}>
      <Toggle editor={editor} row="morale" name="moraleEnabled" hint={t.setup.moraleHint}>
        <Fields>
          <Field
            editor={editor}
            row="morale"
            name="moraleFloor"
            label={fields.moraleFloor}
            hint={labels.fieldHints.moraleFloor}
            step="1"
            min="30"
            max="100"
          />
        </Fields>
        <p className={styles.hint}>{world.moraleHint}</p>
      </Toggle>
      <Toggle
        editor={editor}
        row="nightBonus"
        name="nightBonusEnabled"
        hint={t.setup.nightBonusHint(
          form.nightBonusStartHour.padStart(HOUR_DIGITS, '0'),
          form.nightBonusEndHour.padStart(HOUR_DIGITS, '0'),
        )}
      >
        <Fields>
          <Field
            editor={editor}
            row="nightBonus"
            name="nightBonusStartHour"
            label={fields.nightBonusStartHour}
            step="1"
            min="0"
            max="23"
          />
          <Field
            editor={editor}
            row="nightBonus"
            name="nightBonusEndHour"
            label={fields.nightBonusEndHour}
            step="1"
            min="0"
            max="23"
          />
          <Field
            editor={editor}
            row="nightBonus"
            name="nightBonusFactor"
            label={fields.nightBonusFactor}
            hint={labels.fieldHints.nightBonusFactor}
            step="0.1"
            min="1.1"
            max="3"
          />
        </Fields>
        <p className={styles.hint}>{world.nightBonusHint}</p>
      </Toggle>
      <Toggle editor={editor} row="unitLevels" name="unitLevelsEnabled" hint={t.setup.unitLevelsHint} />
      <Toggle
        editor={editor}
        row="archers"
        name="archersEnabled"
        hint={settings.archersEnabled && !form.archersEnabled ? world.archersOff : t.setup.archersHint}
      >
        <p className={styles.hint}>{world.archersHint}</p>
      </Toggle>
      <NumberToggle editor={editor} row="paladin" name="paladinMaxSlots" hint={labels.paladinHint}>
        <Fields>
          <Field
            editor={editor}
            row="paladin"
            name="paladinMaxSlots"
            label={fields.paladinMaxSlots}
            step="1"
            min="0"
            max="20"
          />
          <Field
            editor={editor}
            row="paladin"
            name="paladinVillagesPerSlot"
            label={fields.paladinVillagesPerSlot}
            step="1"
            min="1"
            max="100"
          />
        </Fields>
      </NumberToggle>
      <More editor={editor} rows={['luckRange']}>
        <Fields>
          <Field editor={editor} row="luckRange" name="luckRange" label={world.luckRange} step="0.05" min="0" max="1" />
        </Fields>
      </More>
    </Group>
  );
  const bannersNobles = (
    <Group title={labels.groups.bannersNobles}>
      <Toggle editor={editor} row="banners" name="bannersEnabled" hint={t.setup.bannersHint}>
        <Fields>
          <Field
            editor={editor}
            row="banners"
            name="bannerDropFactor"
            label={fields.bannerDropFactor}
            hint={labels.fieldHints.bannerDropFactor}
            step="0.1"
            min="0"
            max="3"
          />
        </Fields>
      </Toggle>
      <Toggle editor={editor} row="standardTheft" name="standardTheftEnabled" hint={t.setup.standardTheftHint} />
      <NumberToggle editor={editor} row="noblePrice" name="noblePriceStep" hint={t.setup.noblePriceHint(noblePercent)}>
        <Fields>
          <Field
            editor={editor}
            row="noblePrice"
            name="noblePriceStep"
            label={fields.noblePriceStep}
            hint={labels.fieldHints.noblePriceStep}
            step="0.05"
            min="0"
            max="1"
          />
        </Fields>
      </NumberToggle>
      <More editor={editor} rows={['loyaltyRegenPerHour']}>
        <Fields>
          <Field
            editor={editor}
            row="loyaltyRegenPerHour"
            name="loyaltyRegenPerHour"
            label={world.loyaltyRegenPerHour}
            step="0.1"
            min="0"
            max="100"
          />
        </Fields>
      </More>
    </Group>
  );
  const economy = (
    <Group title={labels.groups.economy}>
      <NumberToggle editor={editor} row="market" name="marketExchangeRatio" hint={t.setup.marketHint}>
        <Fields>
          <Field
            editor={editor}
            row="market"
            name="marketExchangeRatio"
            label={fields.marketExchangeRatio}
            hint={labels.fieldHints.marketExchangeRatio}
            step="0.1"
            min="0"
            max="10"
          />
        </Fields>
      </NumberToggle>
      <Plain>
        <Fields>
          <Field
            editor={editor}
            row="tradeMaxRatio"
            name="tradeMaxRatio"
            label={world.tradeMaxRatio}
            step="0.5"
            min="1"
            max="10"
          />
        </Fields>
      </Plain>
      <NumberToggle editor={editor} row="automation" name="automationVillages" hint={labels.automationHint}>
        <Fields>
          <Field
            editor={editor}
            row="automation"
            name="automationVillages"
            label={fields.automationVillages}
            step="1"
            min="0"
            max="100"
          />
        </Fields>
      </NumberToggle>
    </Group>
  );
  return (
    <Columns
      left={
        <>
          {guilds}
          {combat}
        </>
      }
      right={
        <>
          {bannersNobles}
          {economy}
        </>
      }
    />
  );
}

function VictorySection({ editor }: { editor: Editor }) {
  const { t, language } = useI18n();
  const labels = t.admin.worldTab;
  const victory = t.admin.victory;
  const end = t.admin.worldEnd;
  const { settings, form } = editor;
  const request = victoryRequest(settings, form);
  const victoryCard = (
    <Card title={labels.groups.victory}>
      <div className={styles.plain} data-changed={editor.changed('victory') || undefined}>
        <p className={styles.hint}>{victory.hint}</p>
        <SegmentedControl
          label={victory.mode}
          options={VICTORY_MODES.map((mode) => ({ value: mode, label: victory.modes[mode] }))}
          value={form.victoryMode}
          onChange={(victoryMode) => editor.update({ victoryMode })}
        />
        <p className={styles.hint}>{victory.modeHints[form.victoryMode]}</p>
        <Fields>
          {form.victoryMode === 'DOMINATION' && (
            <Field
              editor={editor}
              row="victory"
              name="dominationPercent"
              label={victory.dominationPercent}
              step="1"
              min="10"
              max="100"
            />
          )}
          {form.victoryMode === 'KEEPS' && (
            <>
              <Field
                editor={editor}
                row="victory"
                name="keepCount"
                label={victory.keepCount}
                step="1"
                min="1"
                max="12"
              />
              <Field
                editor={editor}
                row="victory"
                name="keepPercent"
                label={victory.keepPercent}
                step="1"
                min="1"
                max="100"
              />
            </>
          )}
          {form.victoryMode !== 'NONE' && (
            <Field
              editor={editor}
              row="victory"
              name="victoryHoldHours"
              label={victory.victoryHoldHours}
              step="1"
              min="1"
              max="720"
            />
          )}
        </Fields>
        {form.victoryMode === 'KEEPS' && request?.keepCount != null && request.keepPercent != null && (
          <p className={styles.hint}>
            {victory.keepsNeeded(keepsNeeded(request.keepCount, request.keepPercent), request.keepCount)}
          </p>
        )}
      </div>
    </Card>
  );
  const endCard = (
    <Card title={labels.groups.end}>
      <div className={styles.plain}>
        {settings.phase === 'ENDED' ? (
          <EndedForm endedAt={settings.endedAt ?? null} />
        ) : (
          <>
            <p className={styles.hint}>{end.hint}</p>
            <p>
              {settings.endsAt == null ? end.noDate : end.status(formatDateTime(Date.parse(settings.endsAt), language))}
            </p>
            <Fields wide>
              <Field
                editor={editor}
                row="endsAt"
                name="endsAt"
                type="datetime-local"
                label={end.at}
                hint={labels.endsAtHint}
                onEdit={(input) => editor.setEndIncomplete(input.validity.badInput)}
              />
            </Fields>
            {form.endsAt !== '' && (
              <div className={styles.endActions}>
                <Button
                  type="button"
                  variant="secondary"
                  size="small"
                  onClick={() => {
                    editor.update({ endsAt: '' });
                    editor.setEndIncomplete(false);
                  }}
                >
                  {end.clear}
                </Button>
              </div>
            )}
            <EndNow />
            <p className={styles.hint}>{labels.endNowHint}</p>
          </>
        )}
      </div>
    </Card>
  );
  return <Columns left={victoryCard} right={endCard} />;
}

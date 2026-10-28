import { useQueryClient } from '@tanstack/react-query';
import { useId, useRef, useState } from 'react';
import { Navigate, useParams, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useBanners, type Banners } from '../../api/queries/banners';
import { useBuildings } from '../../api/queries/buildings';
import { fetchReport, useRecentReports, useReport } from '../../api/queries/reports';
import { useSimulate } from '../../api/queries/simulator';
import { useSmithy } from '../../api/queries/smithy';
import { useTroops } from '../../api/queries/troops';
import { useVillage } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { TextField } from '../../components/TextField';
import { TutorialHint } from '../../components/TutorialHint';
import { useTutorialAck } from '../../components/useTutorialAck';
import { effectOfTier, effectiveTier, percentPoints } from '../../game/banners';
import { formatFactor, formatShare, formatWhen } from '../../game/format';
import {
  buildingTypes,
  isStandard,
  standardTypes,
  unitTypesFor,
  type BuildingType,
  type StandardType,
  type UnitType,
} from '../../game/gameData';
import { formatNightHours } from '../../game/nightBonus';
import { useGameNow } from '../../game/serverTime';
import { levelChoices, ownLevels, type UnitLevelMap } from '../../game/unitLevels';
import {
  MAX_BANNER_LEVEL,
  MAX_SIMULATED_LEVEL,
  MAX_SIMULATED_UNITS,
  buildingMaxLevels,
  canSimulate,
  emptyForm,
  filledTypes,
  hasUnit,
  reportToForm,
  SIMULATOR_REPORT_LOOKBACK,
  simulatableReports,
  simulatedMaxLevel,
  sumUnits,
  toRequest,
  toUnitMap,
  withBannerTier,
  withCatapultTarget,
  type BannerTierField,
  type SimulatorForm,
} from '../../game/simulator';
import { parseCount } from '../../game/unitCount';
import type { UnitMap } from '../../game/troops';
import { useI18n } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { parseReportId } from '../reports/reportPaths';
import styles from './Simulator.module.css';
import { rallyPath } from './rallyPaths';
import { SimulatorResult } from './SimulatorResult';
import { villageLabel } from './villageLabel';

/**
 * /v/:villageId/rally/simulator[?report=] – the attack simulator (M23, 02-game-rules §20): an
 * attack on paper with numbers typed in, taken from this village or from a battle report. The
 * server runs the real battle code on them; nothing is sent or changed.
 */
export function SimulatorPage() {
  const villageId = parseVillageId(useParams().villageId);
  const reportId = parseReportId(useSearchParams()[0].get('report') ?? undefined);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village (or report) starts with a fresh form
  return reportId === undefined ? (
    <Simulator key={villageId} villageId={villageId} initial={{}} />
  ) : (
    <WithReport key={`${villageId}-${reportId}`} villageId={villageId} reportId={reportId} />
  );
}

/** `?report=`: wait for the report, then open the form filled in from it (an unusable one is ignored). */
function WithReport({ villageId, reportId }: { villageId: number; reportId: number }) {
  const t = useI18n().t;
  const report = useReport(reportId);
  // the report's banner percents become tiers with the effect table (M32c)
  const world = useWorld();
  const banners = useBanners(world.data?.bannersEnabled === true);
  if (report.isPending || world.isPending || banners.isLoading) {
    return <p>{t.simulator.reportLoading}</p>;
  }
  const payload = report.data?.payload;
  return (
    <Simulator
      villageId={villageId}
      initial={payload?.type === 'BATTLE' ? reportToForm(payload, banners.data?.effects) : {}}
      initialNote={payload?.type === 'BATTLE' ? noteFor(payload) : null}
    />
  );
}

type ReportNote = 'taken' | 'spy' | 'partly';

/** What to tell the player after a report was taken. */
function noteFor(battle: Parameters<typeof reportToForm>[0]): ReportNote {
  if (battle.outcome === 'SCOUTS_ONLY') {
    return 'spy';
  }
  return battle.defenderSide === null ? 'partly' : 'taken';
}

interface SimulatorProps {
  villageId: number;
  initial: Partial<SimulatorForm>;
  initialNote?: ReportNote | null;
}

function Simulator({ villageId, initial, initialNote = null }: SimulatorProps) {
  const { t, language } = useI18n();
  const s = t.simulator;
  // the tutorial's "try the simulator" step (1.7) is done by opening it
  useTutorialAck('SIMULATOR');
  const village = useVillage(villageId);
  const troops = useTroops(villageId);
  // M30: with unit levels the units get a level field; the smithy tells the player's own levels
  // and how many levels each unit has. The paladin's and the thief's levels (Hall training, 1.4)
  // come with it in every world: the smithy lists their top level also with unit levels off.
  const world = useWorld().data;
  const levelsOn = world?.unitLevelsEnabled === true;
  // M32c: with banners on, the War and Bulwark banners and the Banner Hall are inputs
  const bannersOn = world?.bannersEnabled === true;
  // 1.7: the guild banner's level only where the world has guild banners (they need guilds)
  const guildBannersOn = world?.guildsEnabled === true && world.guildBannersEnabled;
  const banners = useBanners(bannersOn);
  const own = ownBanner(banners.data, villageId);
  // 1.4: the paladin standards, in worlds with banners and paladins
  const standardsOn = bannersOn && (world?.paladinMaxSlots ?? 0) > 0;
  // 1.4: the switch only where the world has the night bonus (null = off)
  const night = world?.nightBonus;
  // 1.6: the morale field only where the world has morale (null = off)
  const moraleFloor = world?.moraleFloor;
  const ownStd = ownStandard(banners.data, villageId);
  const smithy = useSmithy(villageId);
  const maxLevels = maxLevelsOf(smithy.data?.units);
  const myLevels = smithy.data === undefined ? {} : ownLevels(smithy.data.units);
  // each building's highest level (game data, the same in every village): the level fields stop
  // there, since the server refuses a higher one
  const buildingMax = buildingMaxLevels(useBuildings(villageId).data);
  const queryClient = useQueryClient();
  const [form, setForm] = useState<SimulatorForm>(() => ({ ...emptyForm, ...initial }));
  // 1.6 UI review: once a side is filled in (own troops, this village, a report), a phone lists only
  // the units it got, the rest behind "show all units"; null shows every unit
  const [shown, setShown] = useState<FoldedSides>(() =>
    initialNote === null
      ? { attacker: null, defender: null }
      : { attacker: filledTypes(initial.attacker), defender: filledTypes(initial.defender) },
  );
  const [note, setNote] = useState<ReportNote | null>(initialNote);
  const [missingUnits, setMissingUnits] = useState(false);
  const [reportError, setReportError] = useState<Error | null>(null);
  const simulate = useSimulate();
  const results = useRef<HTMLDivElement>(null);

  function patch(change: Partial<SimulatorForm>) {
    setForm((previous) => ({ ...previous, ...change }));
  }

  function setBanner(field: BannerTierField, tier: number) {
    setForm((previous) => withBannerTier(previous, field, tier));
  }

  function setCount(side: 'attacker' | 'defender', type: UnitType, count: number) {
    setForm((previous) => ({ ...previous, [side]: { ...previous[side], [type]: count } }));
    setMissingUnits(false);
  }

  function takeReport(reportId: number) {
    setReportError(null);
    fetchReport(queryClient, reportId).then(
      (report) => {
        if (report.payload?.type === 'BATTLE') {
          const filled = reportToForm(report.payload, banners.data?.effects);
          patch(filled);
          setShown({ attacker: filledTypes(filled.attacker), defender: filledTypes(filled.defender) });
          setNote(noteFor(report.payload));
          setMissingUnits(false);
        }
      },
      (error: unknown) => setReportError(error instanceof Error ? error : new Error(String(error))),
    );
  }

  function setLevel(side: 'attacker' | 'defender', type: UnitType, level: number) {
    const key = side === 'attacker' ? 'attackerLevels' : 'defenderLevels';
    setForm((previous) => ({ ...previous, [key]: { ...previous[key], [type]: level } }));
  }

  function takeVillageAsDefender() {
    if (village.data === undefined || troops.data === undefined) {
      return;
    }
    const defender = sumUnits(
      toUnitMap(troops.data.home),
      ...troops.data.supportsHere.map((stack) => toUnitMap(stack.units)),
    );
    setShown((previous) => ({ ...previous, defender: filledTypes(defender) }));
    patch({
      defender,
      // the supporters' own levels are not known here: the player's own stand in for them
      defenderLevels: myLevels,
      wall: village.data.buildings.WALL ?? 0,
      catapultTargetLevel:
        form.catapultTarget === 'WALL' ? form.catapultTargetLevel : (village.data.buildings[form.catapultTarget] ?? 0),
      loyalty: village.data.loyalty,
      bulwarkTier: own?.type === 'BULWARK' ? own.tier : 0,
      oathTier: own?.type === 'OATH' ? own.tier : 0,
      bannerHall: village.data.buildings.BANNER_HALL ?? 0,
      defenderStandard: ownStd?.type ?? null,
      defenderStandardTier: ownStd?.tier ?? 0,
    });
  }

  function run() {
    if (!canSimulate(form)) {
      setMissingUnits(true);
      return;
    }
    simulate.mutate(toRequest(form), {
      onSuccess: () => requestAnimationFrame(() => results.current?.scrollIntoView({ block: 'start' })),
    });
  }

  const catapults = hasUnit(form.attacker, 'CATAPULT');
  const nobles = hasUnit(form.attacker, 'NOBLE');
  const levelTarget = catapults && form.catapultTarget !== 'WALL';
  const targetTier = Math.max(form.bulwarkTier, form.oathTier);

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <BackLink to={rallyPath(villageId)}>{s.back}</BackLink>
        <h1>{s.title}</h1>
        <p className={styles.intro}>{s.intro}</p>
        <TutorialHint steps={['SIMULATOR']} />
        {levelsOn && <p className={styles.intro}>{s.levelsHint}</p>}
        {!levelsOn && Object.keys(maxLevels).length > 0 && <p className={styles.intro}>{s.hallLevelsHint}</p>}
      </header>
      <Card title={s.reportTitle}>
        <div className={styles.body}>
          <ReportPicker onPick={takeReport} />
          {note !== null && (
            <p className={styles.notice}>
              {note === 'taken' ? s.reportTaken : note === 'spy' ? s.reportTakenSpy : s.reportTakenPartly}
            </p>
          )}
          {reportError !== null && <ErrorMessage>{errorMessage(t, reportError)}</ErrorMessage>}
        </div>
      </Card>
      <div className={styles.sides}>
        <Card title={s.attackerTitle}>
          <div className={styles.body}>
            <div className={styles.quick}>
              <Button
                type="button"
                variant="secondary"
                size="small"
                disabled={troops.data === undefined}
                onClick={() => {
                  if (troops.data === undefined) {
                    return;
                  }
                  const attacker = toUnitMap(troops.data.home);
                  setShown((previous) => ({ ...previous, attacker: filledTypes(attacker) }));
                  patch({
                    attacker,
                    attackerLevels: myLevels,
                    // the War or Plunder banner flying here, at the tier its hall lets it work
                    warTier: own?.type === 'WAR' ? own.effectiveTier : 0,
                    plunderTier: own?.type === 'PLUNDER' ? own.effectiveTier : 0,
                    // the village's standard at its own tier: the paladin's level caps it
                    attackerStandard: ownStd?.type ?? null,
                    attackerStandardTier: ownStd?.tier ?? 0,
                  });
                }}
              >
                {s.myUnits}
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="small"
                onClick={() => {
                  setShown((previous) => ({ ...previous, attacker: null }));
                  patch({
                    attacker: {},
                    attackerLevels: {},
                    warTier: 0,
                    plunderTier: 0,
                    attackerStandard: null,
                    attackerStandardTier: 0,
                    morale: 100,
                  });
                }}
              >
                {s.clear}
              </Button>
            </div>
            <UnitFields
              counts={form.attacker}
              label={s.attackerCount}
              onChange={(type, count) => setCount('attacker', type, count)}
              levels={form.attackerLevels}
              maxLevels={maxLevels}
              onLevel={(type, level) => setLevel('attacker', type, level)}
              shown={shown.attacker}
              onShowAll={() => setShown((previous) => ({ ...previous, attacker: null }))}
            />
            {catapults && (
              <BuildingSelect
                label={s.catapultTarget}
                value={form.catapultTarget}
                onChange={(catapultTarget) => setForm((previous) => withCatapultTarget(previous, catapultTarget))}
              />
            )}
            {bannersOn && (
              <>
                <BannerTierSelect
                  label={s.warBanner}
                  type="WAR"
                  effects={banners.data?.effects}
                  value={form.warTier}
                  onChange={(tier) => setBanner('warTier', tier)}
                />
                <BannerTierSelect
                  label={s.plunderBanner}
                  type="PLUNDER"
                  effects={banners.data?.effects}
                  value={form.plunderTier}
                  onChange={(tier) => setBanner('plunderTier', tier)}
                />
              </>
            )}
            {standardsOn && (
              <StandardSelect
                label={s.attackerStandard}
                effects={banners.data?.effects}
                type={form.attackerStandard}
                tier={form.attackerStandardTier}
                onChange={(attackerStandard, attackerStandardTier) => patch({ attackerStandard, attackerStandardTier })}
              />
            )}
            {moraleFloor != null && (
              <LevelField
                label={s.morale}
                hint={s.moraleHint(formatShare(moraleFloor, language))}
                max={100}
                placeholder="100"
                value={form.morale}
                onChange={(morale) => patch({ morale })}
              />
            )}
          </div>
        </Card>
        <Card title={s.defenderTitle}>
          <div className={styles.body}>
            <div className={styles.quick}>
              <Button
                type="button"
                variant="secondary"
                size="small"
                disabled={troops.data === undefined || village.data === undefined}
                onClick={takeVillageAsDefender}
              >
                {s.thisVillage}
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="small"
                onClick={() => {
                  setShown((previous) => ({ ...previous, defender: null }));
                  patch({
                    defender: {},
                    defenderLevels: {},
                    bulwarkTier: 0,
                    oathTier: 0,
                    bannerHall: 0,
                    defenderStandard: null,
                    defenderStandardTier: 0,
                    guildBannerLevel: 0,
                  });
                }}
              >
                {s.clear}
              </Button>
            </div>
            <UnitFields
              counts={form.defender}
              label={s.defenderCount}
              onChange={(type, count) => setCount('defender', type, count)}
              levels={form.defenderLevels}
              maxLevels={maxLevels}
              onLevel={(type, level) => setLevel('defender', type, level)}
              shown={shown.defender}
              onShowAll={() => setShown((previous) => ({ ...previous, defender: null }))}
            />
            <div className={styles.fields}>
              <LevelField
                label={s.wall}
                max={simulatedMaxLevel(buildingMax, 'WALL')}
                value={form.wall}
                onChange={(wall) => patch({ wall })}
              />
              {levelTarget && (
                <LevelField
                  label={s.catapultLevel(t.buildings[form.catapultTarget])}
                  max={simulatedMaxLevel(buildingMax, form.catapultTarget)}
                  value={form.catapultTargetLevel}
                  onChange={(catapultTargetLevel) => patch({ catapultTargetLevel })}
                />
              )}
              {nobles && (
                <LevelField
                  label={s.loyalty}
                  hint={s.loyaltyHint}
                  max={100}
                  value={form.loyalty}
                  onChange={(loyalty) => patch({ loyalty })}
                />
              )}
              {bannersOn && (
                <LevelField
                  label={s.bannerHall}
                  hint={s.bannerHallHint}
                  max={simulatedMaxLevel(buildingMax, 'BANNER_HALL')}
                  value={form.bannerHall}
                  onChange={(bannerHall) => patch({ bannerHall })}
                />
              )}
              {guildBannersOn && (
                <LevelField
                  label={s.guildBannerLevel}
                  hint={s.guildBannerLevelHint}
                  max={MAX_BANNER_LEVEL}
                  value={form.guildBannerLevel}
                  onChange={(guildBannerLevel) => patch({ guildBannerLevel })}
                />
              )}
            </div>
            {bannersOn && (
              <>
                <BannerTierSelect
                  label={s.bulwarkBanner}
                  type="BULWARK"
                  effects={banners.data?.effects}
                  value={form.bulwarkTier}
                  onChange={(tier) => setBanner('bulwarkTier', tier)}
                />
                <BannerTierSelect
                  label={s.oathBanner}
                  type="OATH"
                  effects={banners.data?.effects}
                  value={form.oathTier}
                  onChange={(tier) => setBanner('oathTier', tier)}
                />
                {/* the village's one banner: Bulwark or Oath, whichever is set */}
                {targetTier > effectiveTier(targetTier, form.bannerHall) && (
                  <p className={styles.muted}>{s.bulwarkCapped(effectiveTier(targetTier, form.bannerHall))}</p>
                )}
              </>
            )}
            {standardsOn && (
              <>
                <StandardSelect
                  label={s.defenderStandard}
                  effects={banners.data?.effects}
                  type={form.defenderStandard}
                  tier={form.defenderStandardTier}
                  onChange={(defenderStandard, defenderStandardTier) =>
                    patch({ defenderStandard, defenderStandardTier })
                  }
                />
                <p className={styles.muted}>{s.standardHint}</p>
              </>
            )}
            {night != null && (
              <div>
                <label className={styles.check}>
                  <input
                    type="checkbox"
                    checked={form.night}
                    onChange={(event) => patch({ night: event.target.checked })}
                  />
                  <Icon name="moon" />
                  {s.night(formatFactor(night.factor, language))}
                </label>
                <p className={styles.muted}>{s.nightHint(formatNightHours(night))}</p>
              </div>
            )}
          </div>
        </Card>
      </div>
      <div className={styles.footer}>
        {missingUnits && <p role="alert">{s.needUnits}</p>}
        <Button type="button" disabled={simulate.isPending} onClick={run}>
          {simulate.isPending ? s.simulating : s.simulate}
        </Button>
      </div>
      {simulate.isError && <ErrorMessage>{errorMessage(t, simulate.error)}</ErrorMessage>}
      <div ref={results}>
        {simulate.data !== undefined && <SimulatorResult simulation={simulate.data} request={simulate.variables} />}
      </div>
    </div>
  );
}

/** Per side the units a fill-in gave (shown on phones, the rest folded); null for all of them. */
interface FoldedSides {
  attacker: readonly UnitType[] | null;
  defender: readonly UnitType[] | null;
}

/**
 * One row per unit type: icon, name and a count field. With `shown`, a phone lists only those units
 * and a button for the rest (1.6 UI review: 13 units a side with archers made the page very long);
 * wider screens show every row anyway.
 */
function UnitFields({
  counts,
  label,
  onChange,
  levels,
  maxLevels,
  onLevel,
  shown,
  onShowAll,
}: {
  counts: UnitMap;
  label: (unit: string) => string;
  onChange: (type: UnitType, count: number) => void;
  levels: UnitLevelMap;
  /** The top level of every unit that has levels; empty without unit levels (no level fields). */
  maxLevels: UnitLevelMap;
  onLevel: (type: UnitType, level: number) => void;
  shown: readonly UnitType[] | null;
  onShowAll: () => void;
}) {
  const t = useI18n().t;
  // the archers only in a world with them (1.6), or when the numbers (say from a report) hold some
  const types = unitTypesFor(useWorld().data, counts);
  const folded = shown === null ? 0 : types.filter((type) => !shown.includes(type)).length;
  return (
    <>
      <ul className={styles.unitList}>
        {types.map((type) => (
          <li
            key={type}
            className={
              shown !== null && !shown.includes(type) ? `${styles.unitRow} ${styles.unitRowFolded}` : styles.unitRow
            }
          >
            <span className={styles.unitName}>
              <span className={styles.unitIcon}>
                <Icon name={type} />
              </span>
              {t.units[type]}
            </span>
            <span className={styles.unitControls}>
              {(maxLevels[type] ?? 1) > 1 && (
                <select
                  className={styles.level}
                  aria-label={t.simulator.levelLabel(t.units[type])}
                  value={levels[type] ?? 1}
                  onChange={(event) => onLevel(type, Number(event.target.value))}
                >
                  {levelChoices(maxLevels[type] ?? 1).map((level) => (
                    <option key={level} value={level}>
                      {t.smithy.level(level)}
                    </option>
                  ))}
                </select>
              )}
              <input
                className={styles.count}
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                autoComplete="off"
                placeholder="0"
                aria-label={label(t.units[type])}
                value={(counts[type] ?? 0) === 0 ? '' : String(counts[type])}
                onChange={(event) => onChange(type, parseCount(event.target.value, MAX_SIMULATED_UNITS))}
              />
            </span>
          </li>
        ))}
      </ul>
      {folded > 0 && (
        <button type="button" className={styles.showAll} onClick={onShowAll}>
          {t.simulator.showAllUnits(folded)}
        </button>
      )}
    </>
  );
}

function LevelField({
  label,
  hint,
  value,
  max = MAX_SIMULATED_LEVEL,
  placeholder = '0',
  onChange,
}: {
  label: string;
  hint?: string;
  value: number;
  max?: number;
  /** What an empty field means. */
  placeholder?: string;
  onChange: (value: number) => void;
}) {
  return (
    <TextField
      label={label}
      hint={hint}
      inputMode="numeric"
      pattern="[0-9]*"
      autoComplete="off"
      placeholder={placeholder}
      value={value === 0 ? '' : String(value)}
      onChange={(event) => onChange(parseCount(event.target.value, max))}
    />
  );
}

function BuildingSelect({
  label,
  value,
  onChange,
}: {
  label: string;
  value: BuildingType;
  onChange: (value: BuildingType) => void;
}) {
  const t = useI18n().t;
  const id = useId();
  return (
    <div className={styles.select}>
      <label htmlFor={id}>{label}</label>
      <select
        id={id}
        value={value}
        // find() turns the plain string back into a BuildingType (no unchecked cast)
        onChange={(event) => onChange(buildingTypes.find((type) => type === event.target.value) ?? 'HEADQUARTERS')}
      >
        {buildingTypes.map((type) => (
          <option key={type} value={type}>
            {t.buildings[type]}
          </option>
        ))}
      </select>
    </div>
  );
}

/**
 * None, tier I, II or III of a banner, with the percent each tier gives when the table is known (the
 * Oath takes off, the others add).
 */
function BannerTierSelect({
  label,
  type,
  effects,
  value,
  onChange,
}: {
  label: string;
  type: 'WAR' | 'BULWARK' | 'PLUNDER' | 'OATH';
  effects: Banners['effects'] | undefined;
  value: number;
  onChange: (tier: number) => void;
}) {
  const { t, language } = useI18n();
  const id = useId();
  const tiers = Array.from({ length: MAX_BANNER_LEVEL }, (_, index) => index + 1);
  return (
    <div className={styles.select}>
      <label htmlFor={id}>{label}</label>
      <select id={id} value={value} onChange={(event) => onChange(Number(event.target.value))}>
        <option value={0}>{t.simulator.noBanner}</option>
        {tiers.map((tier) => (
          <option key={tier} value={tier}>
            {effects === undefined
              ? t.banners.tier(tier)
              : t.simulator.bannerTierOption(
                  tier,
                  `${type === 'OATH' ? '−' : '+'}${formatShare(percentPoints(effectOfTier(effects, type, tier)), language)}`,
                )}
          </option>
        ))}
      </select>
    </div>
  );
}

/**
 * A paladin standard (1.4, 02-game-rules §21.18): its kind and its own tier. The server caps the tier
 * by that side's paladin level and, for the defenders, puts it to sleep without a Banner Hall.
 */
function StandardSelect({
  label,
  effects,
  type,
  tier,
  onChange,
}: {
  label: string;
  effects: Banners['effects'] | undefined;
  type: StandardType | null;
  tier: number;
  onChange: (type: StandardType | null, tier: number) => void;
}) {
  const { t, language } = useI18n();
  const id = useId();
  const tierId = useId();
  const tiers = Array.from({ length: MAX_BANNER_LEVEL }, (_, index) => index + 1);
  return (
    <div className={styles.fields}>
      <div className={styles.select}>
        <label htmlFor={id}>{label}</label>
        <select
          id={id}
          value={type ?? ''}
          onChange={(event) => {
            const next = event.target.value === '' ? null : (event.target.value as StandardType);
            onChange(next, next === null ? 0 : Math.max(1, tier));
          }}
        >
          <option value="">{t.simulator.noBanner}</option>
          {standardTypes.map((option) => (
            <option key={option} value={option}>
              {t.banners.types[option]}
            </option>
          ))}
        </select>
      </div>
      {type !== null && (
        <div className={styles.select}>
          <label htmlFor={tierId}>{t.simulator.standardTier}</label>
          <select id={tierId} value={tier} onChange={(event) => onChange(type, Number(event.target.value))}>
            {tiers.map((option) => (
              <option key={option} value={option}>
                {effects === undefined
                  ? t.banners.tier(option)
                  : t.simulator.bannerTierOption(
                      option,
                      `+${formatShare(percentPoints(effectOfTier(effects, type, option)), language)}`,
                    )}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  );
}

/** The paladin standard equipped in the player's village `villageId`, at its own tier. */
function ownStandard(banners: Banners | undefined, villageId: number) {
  const row = banners?.villages.find((entry) => entry.id === villageId);
  const standard = banners?.banners.find((entry) => entry.id === row?.standardId);
  return standard === undefined || !isStandard(standard.type)
    ? undefined
    : { type: standard.type, tier: standard.tier };
}

/** The banner flying in the player's village `villageId`, with the tier it works at there. */
function ownBanner(banners: Banners | undefined, villageId: number) {
  const row = banners?.villages.find((entry) => entry.id === villageId);
  const banner = banners?.banners.find((entry) => entry.id === row?.bannerId);
  return row === undefined || banner === undefined
    ? undefined
    : { type: banner.type, tier: banner.tier, effectiveTier: row.effectiveTier };
}

/** The newest battle reports (attacks, defences, scouting) to take numbers from. */
function ReportPicker({ onPick }: { onPick: (reportId: number) => void }) {
  const { t, language } = useI18n();
  const reports = useRecentReports(SIMULATOR_REPORT_LOOKBACK);
  const now = useGameNow();
  const id = 'simulator-report';
  const items = simulatableReports(reports.data?.items ?? []);
  if (reports.isPending) {
    return <p className={styles.muted}>{t.simulator.reportsLoading}</p>;
  }
  if (reports.isError) {
    return <ErrorMessage>{errorMessage(t, reports.error)}</ErrorMessage>;
  }
  if (items.length === 0) {
    return <p className={styles.muted}>{t.simulator.noReports}</p>;
  }
  return (
    <div className={styles.select}>
      <label htmlFor={id}>{t.simulator.reportLabel}</label>
      <select
        id={id}
        value=""
        onChange={(event) => {
          const reportId = parseReportId(event.target.value);
          if (reportId !== undefined) {
            onPick(reportId);
          }
        }}
      >
        <option value="">{t.simulator.reportPlaceholder}</option>
        {items.map((item) => (
          <option key={item.id} value={item.id}>
            {item.summary === null
              ? `${t.reports.kinds[item.kind]}`
              : t.simulator.reportOption(
                  t.reports.kinds[item.kind],
                  villageLabel(t, item.summary.from),
                  villageLabel(t, item.summary.to),
                  formatWhen(Date.parse(item.happenedAt), now, language),
                )}
          </option>
        ))}
      </select>
    </div>
  );
}

/** The top level of each unit that has more than one: what the level fields offer. */
function maxLevelsOf(units: readonly { unit: UnitType; maxLevel: number }[] | undefined): UnitLevelMap {
  const map: UnitLevelMap = {};
  for (const { unit, maxLevel } of units ?? []) {
    if (maxLevel > 1) {
      map[unit] = maxLevel;
    }
  }
  return map;
}

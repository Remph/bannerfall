import { useState, type ReactNode } from 'react';
import { Link, Navigate, NavLink, useParams } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import {
  useEnqueueRecruit,
  useEnqueueRecruitAll,
  useRecruitment,
  useRefreshWhenUnitsDone,
  type RecruitBuilding,
  type RecruitUnit,
} from '../../api/queries/recruitment';
import { usePaladin } from '../../api/queries/paladin';
import { useRefreshWhenMovementsArrive, useTroops } from '../../api/queries/troops';
import { useVillage, type Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { CostList } from '../../components/CostList';
import { DevCheats } from '../../components/DevCheats';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { UnitCountInput } from '../../components/UnitCountInput';
import { formatNumber, formatUpcoming } from '../../game/format';
import { recruitingBuildings, type RecruitingBuilding, type UnitType } from '../../game/gameData';
import {
  MAX_RECRUIT_COUNT,
  maxAffordable,
  sharedMax,
  timesCount,
  totalOf,
  type TypedRow,
} from '../../game/recruitment';
import { freePaladinSlots, cappedBySlots } from '../../game/paladin';
import { projectResources, whenAffordable, type ResourceAmounts } from '../../game/resources';
import { useServerNow } from '../../game/serverTime';
import { unitsAway, type UnitMap } from '../../game/troops';
import { useI18n, useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import { DismissQueueCard } from './DismissQueueCard';
import { DismissUnit } from './DismissUnit';
import { PaladinSlots } from './PaladinSlots';
import { RecruitQueueCard } from './RecruitQueueCard';
import styles from './RecruitPage.module.css';
import { parseRecruitBuilding, recruitPath } from './recruitPaths';

/**
 * /v/:villageId/recruit/:building – train units in the Barracks, Stable, Workshop, Academy or, with
 * banners on, the Banner Hall (05-frontend §3).
 */
export function RecruitPage() {
  const params = useParams();
  const villageId = parseVillageId(params.villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  const building = parseRecruitBuilding(params.building);
  if (building === undefined) {
    // `/v/1/recruit` (the section link) and unknown names open the barracks
    return <Navigate to={recruitPath(villageId, 'BARRACKS')} replace />;
  }
  // `key`: another village or building starts with empty count fields
  return <Recruit key={`${villageId}/${building}`} villageId={villageId} building={building} />;
}

function Recruit({ villageId, building }: { villageId: number; building: RecruitingBuilding }) {
  const t = useT();
  const village = useVillage(villageId);
  const recruitment = useRecruitment(villageId);
  const world = useWorld();
  const queueSize = world.data?.recruitQueueSize;
  // New units show up at home by themselves while this screen is open.
  useRefreshWhenUnitsDone(villageId, recruitment.data, recruitment.dataUpdatedAt, recruitment.errorUpdatedAt);
  // The units away, for each unit's total; an arrival refreshes them together with the units at home.
  const troops = useTroops(villageId);
  useRefreshWhenMovementsArrive(villageId, troops.data, troops.dataUpdatedAt, troops.errorUpdatedAt);
  const away = troops.data === undefined ? undefined : unitsAway(troops.data);

  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (recruitment.isError) {
    return <VillageError error={recruitment.error} onRetry={() => void recruitment.refetch()} />;
  }
  if (village.isPending || recruitment.isPending) {
    return <p>{t.common.loading}</p>;
  }

  const info = recruitment.data.find((entry) => entry.building === building);
  if (info === undefined) {
    // M32b: the server leaves out the Banner Hall while banners are switched off
    return <Navigate to={recruitPath(villageId, 'BARRACKS')} replace />;
  }
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.recruit.title}</h1>
        <p className={styles.meta}>{village.data.name}</p>
      </header>
      <BuildingTabs villageId={villageId} buildings={recruitment.data} />
      <div className={styles.layout}>
        <div className={styles.queue}>
          {info.level === 0 && <NotBuilt villageId={villageId} building={building} />}
          {/* catapults can raze the building to 0 while orders run: the server keeps training them
              and lets them be cancelled, so the queue stays as long as it has orders */}
          {(info.level > 0 || info.queue.length > 0) && (
            <RecruitQueueCard villageId={villageId} info={info} queueSize={queueSize} />
          )}
          <DismissQueueCard
            villageId={villageId}
            info={info}
            queueSize={queueSize}
            locked={village.data.incomingAttacks > 0}
          />
        </div>
        <div className={styles.units}>
          <UnitList village={village.data} info={info} queueSize={queueSize} away={away} />
        </div>
        {import.meta.env.DEV && (
          <div className={styles.dev}>
            <DevCheats villageId={villageId} />
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * The recruiting buildings the server lists as tabs (links, so every building has its own URL): the
 * Barracks, Stable, Workshop and Academy, and the Banner Hall while banners are on (M32b). `replace`:
 * switching tabs doesn't fill the browser history, "back" leaves the screen.
 */
function BuildingTabs(props: { villageId: number; buildings: readonly RecruitBuilding[] }) {
  const { villageId, buildings } = props;
  const t = useT();
  return (
    <nav className={styles.tabs} aria-label={t.recruit.buildingsLabel}>
      {recruitingBuildings.map((type) => {
        const info = buildings.find((entry) => entry.building === type);
        if (info === undefined) {
          return null;
        }
        const level = info.level;
        const orders = info.queue.length;
        return (
          <NavLink
            key={type}
            to={recruitPath(villageId, type)}
            replace
            className={level === 0 ? `${styles.tab} ${styles.tabEmpty}` : styles.tab}
          >
            <span className={styles.tabIcon}>
              <Icon name={type} />
            </span>
            <span className={styles.tabName}>
              {type === 'BANNER_HALL' ? t.recruit.bannerHallTab : t.buildings[type]}
            </span>
            <span className={styles.tabLevel}>{level === 0 ? t.village.notBuilt : t.village.level(level)}</span>
            {orders > 0 && (
              <span className={styles.tabBadge}>
                <span aria-hidden="true">{orders}</span>
                <span className="sr-only">{t.recruit.ordersInQueue(orders)}</span>
              </span>
            )}
          </NavLink>
        );
      })}
    </nav>
  );
}

/** Instead of the queue while the building doesn't exist: where to build it. */
function NotBuilt({ villageId, building }: { villageId: number; building: RecruitingBuilding }) {
  const t = useT();
  return (
    <p className={styles.notBuilt}>
      <span>{t.recruit.notBuilt(t.buildings[building])}</span>
      <Link className={styles.link} to={{ pathname: `/v/${villageId}/hq`, hash: building }}>
        {t.recruit.toTownHall}
      </Link>
    </p>
  );
}

interface UnitListProps {
  village: Village;
  info: RecruitBuilding;
  /** Orders per building (world setting); undefined while it is loading. */
  queueSize: number | undefined;
  /** The village's own units away (supporting elsewhere or on the way); undefined while loading. */
  away: UnitMap | undefined;
}

/**
 * The building's units in unit order; units whose requirements aren't met say what they need. The
 * typed counts live here, not in the rows: resources and population are shared, so every row's
 * maximum is what is left after the other rows (M27), and "recruit all" sends every filled-in row.
 */
function UnitList({ village, info, queueSize, away }: UnitListProps) {
  const t = useT();
  const now = useServerNow();
  const enqueue = useEnqueueRecruit(village.id);
  const enqueueAll = useEnqueueRecruitAll(village.id);
  // M31: a paladin needs a free slot, on top of resources and population
  const paladin = usePaladin(info.units.some((unit) => unit.unit === 'PALADIN'));
  const freeSlots = paladin.data === undefined ? undefined : freePaladinSlots(paladin.data);
  const [counts, setCounts] = useState<Partial<Record<UnitType, number>>>({});
  const queueFull = queueSize !== undefined && info.queue.length >= queueSize;
  const dismissQueueFull = queueSize !== undefined && info.dismissQueue.length >= queueSize;
  const stock = projectResources(village, now);
  const freePop = village.population.capacity - village.population.used;
  const pending = enqueue.isPending || enqueueAll.isPending;
  const pendingUnit = enqueue.isPending ? enqueue.variables?.unit : undefined;
  const failedUnit = enqueue.isError ? enqueue.variables?.unit : undefined;
  // a refused "recruit all" names the order that failed: its row shows the error
  const failedAllUnit =
    enqueueAll.error instanceof ApiError && enqueueAll.error.unit !== null ? enqueueAll.error.unit : undefined;

  const available = info.units.filter((unit) => unit.available);
  const filled = available.map((unit) => ({ unit, count: counts[unit.unit] ?? 0 })).filter((row) => row.count > 0);
  const typed = (except: UnitType): TypedRow[] =>
    filled
      .filter((row) => row.unit.unit !== except)
      .map((row) => ({ cost: row.unit.cost, pop: row.unit.pop, count: row.count }));
  const setCount = (unit: UnitType, count: number) => setCounts((current) => ({ ...current, [unit]: count }));
  // at home plus away; unknown until the troops are loaded
  const ownTotal = (unit: UnitType) =>
    away === undefined ? undefined : (village.unitsHome[unit] ?? 0) + (away[unit] ?? 0);

  return (
    <Card title={t.recruit.unitsTitle(t.buildings[info.building])}>
      {queueFull && <p className={styles.notice}>{t.recruit.queueFull}</p>}
      <ul className={styles.list}>
        {info.units.map((unit) =>
          unit.available ? (
            <UnitRow
              key={unit.unit}
              unit={unit}
              village={village}
              stock={stock}
              freePop={freePop}
              others={typed(unit.unit)}
              total={ownTotal(unit.unit)}
              count={counts[unit.unit] ?? 0}
              onCount={(count) => setCount(unit.unit, count)}
              slotLimit={unit.unit === 'PALADIN' ? freeSlots : undefined}
              queueFull={queueFull}
              now={now}
              pending={pending}
              recruiting={pendingUnit === unit.unit}
              error={failedUnit === unit.unit ? enqueue.error : failedAllUnit === unit.unit ? enqueueAll.error : null}
              dismissQueueFull={dismissQueueFull}
              onRecruit={(count) =>
                enqueue.mutate(
                  { unit: unit.unit, count },
                  { onSuccess: () => setCount(unit.unit, 0), onSettled: () => enqueueAll.reset() },
                )
              }
            />
          ) : (
            <LockedUnitRow
              key={unit.unit}
              villageId={village.id}
              unit={unit}
              atHome={village.unitsHome[unit.unit] ?? 0}
              total={ownTotal(unit.unit)}
              dismissQueueFull={dismissQueueFull}
            />
          ),
        )}
      </ul>
      {available.length > 0 && !queueFull && (
        <RecruitAll
          rows={filled}
          stock={stock}
          freePop={freePop}
          freePlaces={queueSize === undefined ? undefined : queueSize - info.queue.length}
          freeSlots={freeSlots}
          pending={pending}
          recruiting={enqueueAll.isPending}
          // an error that names no order (e.g. no connection) is shown here
          error={enqueueAll.isError && failedAllUnit === undefined ? enqueueAll.error : null}
          onRecruit={() =>
            enqueueAll.mutate(
              filled.map((row) => ({ unit: row.unit.unit, count: row.count })),
              { onSuccess: () => setCounts({}), onSettled: () => enqueue.reset() },
            )
          }
        />
      )}
    </Card>
  );
}

interface RecruitAllProps {
  /** The filled-in rows, in unit order (the order the server queues them in). */
  rows: readonly { unit: RecruitUnit; count: number }[];
  stock: ResourceAmounts;
  freePop: number;
  /** Free places in the building's queue; undefined while the world settings load. */
  freePlaces: number | undefined;
  /** Free paladin slots (the barracks' tab); undefined elsewhere and while they load. */
  freeSlots: number | undefined;
  pending: boolean;
  recruiting: boolean;
  error: Error | null;
  onRecruit: () => void;
}

/**
 * Below the rows: what all filled-in rows cost together and the "recruit all" button (M27). The
 * button needs every row to fit (then the total fits: each row's maximum is what the others leave)
 * and a free place in the queue per row; the server checks all of it again.
 */
function RecruitAll(props: RecruitAllProps) {
  const { rows, stock, freePop, freePlaces, freeSlots, pending, recruiting, error, onRecruit } = props;
  const { t, language } = useI18n();
  const total = totalOf(rows.map((row) => ({ cost: row.unit.cost, pop: row.unit.pop, count: row.count })));
  const seconds = rows.reduce((sum, row) => sum + row.unit.secondsPerUnit * row.count, 0);
  const fits = rows.every((row) => {
    const others = rows
      .filter((other) => other !== row)
      .map((other) => ({ cost: other.unit.cost, pop: other.unit.pop, count: other.count }));
    const slotLimit = row.unit.unit === 'PALADIN' ? freeSlots : undefined;
    return row.count <= cappedBySlots(sharedMax(stock, freePop, row.unit, others), slotLimit);
  });
  const tooFewPlaces = freePlaces !== undefined && rows.length > freePlaces;
  return (
    <div className={styles.all}>
      {rows.length > 0 && (
        <CostList
          label={t.recruit.allTotal(rows.length)}
          timeLabel={t.recruit.time}
          showLabel
          cost={total.cost}
          pop={total.pop}
          seconds={seconds}
          stock={stock}
          freePop={freePop}
        />
      )}
      <div className={styles.allAction}>
        {tooFewPlaces && <p className={styles.hint}>{t.recruit.queuePlaces(freePlaces)}</p>}
        <Button type="button" disabled={rows.length === 0 || !fits || tooFewPlaces || pending} onClick={onRecruit}>
          {recruiting
            ? t.recruit.recruiting
            : rows.length > 1
              ? `${t.recruit.recruitAll} (${formatNumber(rows.length, language)})`
              : t.recruit.recruitAll}
        </Button>
      </div>
      {error !== null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
    </div>
  );
}

interface UnitRowProps {
  unit: RecruitUnit;
  village: Village;
  stock: ResourceAmounts;
  freePop: number;
  /** The other filled-in rows: what they cost is not available to this one (M27). */
  others: readonly TypedRow[];
  /** All of the village's own units of this type, at home and away; undefined while loading. */
  total: number | undefined;
  count: number;
  onCount: (count: number) => void;
  /** Paladin only: the free slots, which cap the count like resources do. */
  slotLimit: number | undefined;
  queueFull: boolean;
  dismissQueueFull: boolean;
  now: number;
  /** Some order is being sent right now (all buttons wait). */
  pending: boolean;
  /** This unit's order is being sent right now. */
  recruiting: boolean;
  error: Error | null;
  /** Sends the order (the field is emptied once the server accepted it). */
  onRecruit: (count: number) => void;
}

/** A unit that can be trained: costs per unit, the count field, the total for that count, the button. */
function UnitRow(props: UnitRowProps) {
  const {
    unit,
    village,
    stock,
    freePop,
    others,
    total,
    count,
    onCount,
    slotLimit,
    queueFull,
    dismissQueueFull,
    now,
    pending,
    recruiting,
    error,
    onRecruit,
  } = props;
  const { t, language } = useI18n();
  // + and "max" stop at what the other filled-in rows leave (M27). The button sends this row
  // alone, so it only needs what this row costs by itself (the server checks the same).
  const max = cappedBySlots(sharedMax(stock, freePop, unit, others), slotLimit);
  const aloneMax = cappedBySlots(maxAffordable(stock, unit.cost, freePop, unit.pop), slotLimit);
  const canRecruit = !queueFull && count >= 1 && count <= aloneMax;

  // Why it can't be recruited (for the typed count, or for one unit while the field is empty): the
  // same order of reasons as in the Town Hall. When the row alone fits but not together with the
  // other rows, that is said instead ("recruit all" waits for it). A full queue is said once above
  // the list.
  const wanted = Math.max(count, 1);
  let hint: string | null = null;
  if (!queueFull && slotLimit !== undefined && wanted > slotLimit) {
    hint = t.paladin.freeSlots(slotLimit);
  } else if (!queueFull && wanted > aloneMax) {
    const affordable = whenAffordable(village, timesCount(unit.cost, wanted), now);
    if (affordable.kind === 'warehouse') {
      hint = t.townHall.warehouseTooSmall;
    } else if (affordable.kind === 'never') {
      hint = t.townHall.notProduced;
    } else if (wanted * unit.pop > freePop) {
      hint = t.townHall.farmTooSmall;
    } else if (affordable.kind === 'later') {
      hint = t.townHall.enoughAt(formatUpcoming(affordable.atMs, now, language));
    }
  } else if (!queueFull && wanted > max) {
    // also on an empty field, where it explains why + and "max" do nothing
    hint = t.recruit.sharedByOthers;
  }

  return (
    <li className={styles.row}>
      <div className={styles.info}>
        <UnitHead
          unit={unit}
          atHome={village.unitsHome[unit.unit] ?? 0}
          total={total}
          dismiss={
            <DismissUnit
              villageId={village.id}
              unit={unit}
              atHome={village.unitsHome[unit.unit] ?? 0}
              queueFull={dismissQueueFull}
            />
          }
        />
        {unit.unit === 'PALADIN' && <PaladinSlots />}
        <CostList
          label={t.recruit.perUnit}
          timeLabel={t.recruit.time}
          cost={unit.cost}
          pop={unit.pop}
          seconds={unit.secondsPerUnit}
          stock={stock}
          freePop={freePop}
        />
        {count >= 1 && (
          <CostList
            label={t.recruit.total(formatNumber(count, language))}
            timeLabel={t.recruit.time}
            showLabel
            cost={timesCount(unit.cost, count)}
            pop={unit.pop * count}
            seconds={unit.secondsPerUnit * count}
            stock={stock}
            freePop={freePop}
          />
        )}
      </div>
      <div className={styles.controls}>
        <UnitCountInput
          value={count}
          onChange={onCount}
          max={max}
          limit={MAX_RECRUIT_COUNT}
          label={t.recruit.countLabel(t.units[unit.unit])}
          disabled={queueFull}
        />
        <div className={styles.action}>
          {hint !== null && <p className={styles.hint}>{hint}</p>}
          <Button type="button" size="small" disabled={!canRecruit || pending} onClick={() => onRecruit(count)}>
            {recruiting ? t.recruit.recruiting : t.recruit.recruit}
          </Button>
        </div>
      </div>
      {error !== null && (
        <div className={styles.rowError}>
          <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
        </div>
      )}
    </li>
  );
}

/** A unit whose requirements aren't met: what it costs and what it needs (current levels count). */
function LockedUnitRow(props: {
  villageId: number;
  unit: RecruitUnit;
  atHome: number;
  total: number | undefined;
  dismissQueueFull: boolean;
}) {
  const { villageId, unit, atHome, total, dismissQueueFull } = props;
  const t = useT();
  return (
    <li className={`${styles.row} ${styles.locked}`}>
      <div className={styles.info}>
        <UnitHead
          unit={unit}
          atHome={atHome}
          total={total}
          dismiss={<DismissUnit villageId={villageId} unit={unit} atHome={atHome} queueFull={dismissQueueFull} />}
        />
        {unit.unit === 'PALADIN' && <PaladinSlots />}
        <CostList
          label={t.recruit.perUnit}
          timeLabel={t.recruit.time}
          cost={unit.cost}
          pop={unit.pop}
          seconds={unit.secondsPerUnit}
        />
        <p className={styles.needs}>
          {t.townHall.needs(
            unit.missing
              .map((requirement) => t.townHall.requirement(t.buildings[requirement.building], requirement.level))
              .join(', '),
          )}
        </p>
      </div>
    </li>
  );
}

/** Icon, name and how many of them are at home, with the village's total once the troops are loaded. */
function UnitHead(props: { unit: RecruitUnit; atHome: number; total: number | undefined; dismiss: ReactNode }) {
  const { unit, atHome, total, dismiss } = props;
  const { t, language } = useI18n();
  return (
    <div className={styles.unitHead}>
      <span className={styles.icon}>
        <Icon name={unit.unit} />
      </span>
      <h3 className={styles.name}>{t.units[unit.unit]}</h3>
      <span className={styles.atHome}>
        {total === undefined
          ? t.recruit.atHome(formatNumber(atHome, language))
          : t.recruit.atHomeOfTotal(formatNumber(atHome, language), formatNumber(total, language))}
      </span>
      {dismiss}
    </div>
  );
}

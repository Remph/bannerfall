import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router';
import { ErrorMessage } from '../../components/ErrorMessage';
import { errorMessage } from '../../api/errors';
import {
  useCancelResearch,
  useRefreshWhenResearchEnds,
  useSmithy,
  useStartResearch,
  type NextResearch,
  type ResearchOrder,
  type Smithy,
  type SmithyUnit,
} from '../../api/queries/smithy';
import { useVillage, type Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { Countdown } from '../../components/Countdown';
import { CostList } from '../../components/CostList';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { formatDecimal, formatNumber, formatUpcoming } from '../../game/format';
import type { UnitType } from '../../game/gameData';
import { projectResources, whenAffordable, type ResourceAmounts } from '../../game/resources';
import { useServerNow } from '../../game/serverTime';
import { researchBlock, statsAt } from '../../game/unitLevels';
import { useI18n } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import styles from './Smithy.module.css';

/**
 * /v/:villageId/smithy – what every unit can do and, if the world has unit levels, the research
 * (M30, 02-game-rules §5.4). Reached from the village overview only; it has no tab of its own. The
 * values are shown with unit levels off too: the player sees what the units can do either way.
 */
export function SmithyPage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village gets a fresh page
  return <SmithyScreen key={villageId} villageId={villageId} />;
}

function SmithyScreen({ villageId }: { villageId: number }) {
  const { t } = useI18n();
  const village = useVillage(villageId);
  const smithy = useSmithy(villageId);
  useRefreshWhenResearchEnds(villageId, smithy.data, smithy.dataUpdatedAt, smithy.errorUpdatedAt);
  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (smithy.isError) {
    return <VillageError error={smithy.error} onRetry={() => void smithy.refetch()} />;
  }
  if (village.isPending || smithy.isPending) {
    return <p>{t.common.loading}</p>;
  }
  return <SmithyView village={village.data} smithy={smithy.data} />;
}

function SmithyView({ village, smithy }: { village: Village; smithy: Smithy }) {
  const { t } = useI18n();
  const s = t.smithy;
  const start = useStartResearch(village.id);
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <Link className={styles.back} to={`/v/${village.id}`}>
          {s.back}
        </Link>
        <h1>{s.title}</h1>
        <p className={styles.hint}>{smithy.smithyLevel === 0 ? s.notBuilt : t.village.level(smithy.smithyLevel)}</p>
        {smithy.smithyLevel === 0 && (
          <p>
            <Link className={styles.link} to={{ pathname: `/v/${village.id}/hq`, hash: 'SMITHY' }}>
              {s.toTownHall}
            </Link>
          </p>
        )}
        <p className={styles.hint}>{smithy.levelsEnabled ? s.levelsOn : s.levelsOff}</p>
      </header>
      {smithy.levelsEnabled && <ResearchCard villageId={village.id} order={smithy.running} />}
      <Card title={s.unitsTitle}>
        <ul className={styles.list}>
          {smithy.units.map((unit) => (
            <UnitRow
              key={unit.unit}
              unit={unit}
              village={village}
              levelsEnabled={smithy.levelsEnabled}
              starting={start.isPending && start.variables === unit.unit}
              anyStarting={start.isPending}
              error={start.isError && start.variables === unit.unit ? start.error : null}
              onStart={() => start.mutate(unit.unit)}
            />
          ))}
        </ul>
      </Card>
    </div>
  );
}

/** The research that runs in this village (its smithy does one at a time), with a cancel button. */
function ResearchCard({ villageId, order }: { villageId: number; order: ResearchOrder | null }) {
  const { t } = useI18n();
  const s = t.smithy;
  const now = useServerNow();
  const cancel = useCancelResearch(villageId);
  const [confirming, setConfirming] = useState<ResearchOrder | null>(null);
  const what = (entry: ResearchOrder) => s.researching(t.units[entry.unit], entry.level);

  return (
    <Card title={s.researchTitle}>
      <div className={styles.body}>
        {order === null ? (
          <p className={styles.hint}>{s.nothingRunning}</p>
        ) : (
          <div className={styles.running}>
            <span className={styles.icon}>
              <Icon name={order.unit} />
            </span>
            <span className={styles.runningText}>
              <strong>{what(order)}</strong>
              <span className={styles.hint}>
                {s.readyIn} <Countdown until={order.endAt} />
              </span>
            </span>
            <IconButton
              icon="close"
              variant="danger"
              label={s.cancel(what(order))}
              // a research whose end has passed is done on the server
              disabled={cancel.isPending || Date.parse(order.endAt) <= now}
              onClick={() => {
                cancel.reset();
                setConfirming(order);
              }}
            />
          </div>
        )}
      </div>
      {/* Closes by itself when the research ends meanwhile: there is nothing left to cancel. */}
      <ConfirmSheet
        open={confirming !== null && Date.parse(confirming.endAt) > now}
        title={s.cancelTitle}
        text={confirming === null ? '' : `${s.cancelText(what(confirming))} ${s.cancelRefund}`}
        confirmLabel={s.confirmCancel}
        danger
        pending={cancel.isPending}
        error={cancel.error}
        onConfirm={() => confirming !== null && cancel.mutate(confirming.id, { onSuccess: () => setConfirming(null) })}
        onClose={() => setConfirming(null)}
      />
    </Card>
  );
}

interface UnitRowProps {
  unit: SmithyUnit;
  village: Village;
  levelsEnabled: boolean;
  /** This unit's research is being sent right now. */
  starting: boolean;
  /** Some research is being sent right now (all buttons wait). */
  anyStarting: boolean;
  error: Error | null;
  onStart: () => void;
}

/** One unit: its values at its level, the values of every level, and the next research. */
function UnitRow({ unit, village, levelsEnabled, starting, anyStarting, error, onStart }: UnitRowProps) {
  const { t, language } = useI18n();
  const s = t.smithy;
  // units without research steps show nothing about levels, also with unit levels on
  const leveled = levelsEnabled && unit.maxLevel > 1;
  return (
    <li className={styles.row}>
      <div className={styles.unitHead}>
        <span className={styles.icon}>
          <Icon name={unit.unit} />
        </span>
        <h3 className={styles.name}>{t.units[unit.unit]}</h3>
        {leveled && <span className={styles.badge}>{s.levelOf(unit.level, unit.maxLevel)}</span>}
      </div>
      <StatsTable unit={unit} leveled={leveled} />
      <dl className={styles.meta}>
        <div>
          <dt>{s.minutesPerField}</dt>
          <dd>{formatDecimal(unit.minutesPerField, language)}</dd>
        </div>
        <div>
          <dt>{s.carry}</dt>
          <dd>{formatNumber(unit.carry, language)}</dd>
        </div>
      </dl>
      {leveled && unit.unit === 'SCOUT' && <p className={styles.hint}>{s.scoutHint}</p>}
      {unit.unit === 'PALADIN' && <p className={styles.hint}>{s.paladinHint}</p>}
      {leveled && (
        <ResearchBlock unit={unit} village={village} starting={starting} anyStarting={anyStarting} onStart={onStart} />
      )}
      {error !== null && (
        <div className={styles.rowError}>
          <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
        </div>
      )}
    </li>
  );
}

/**
 * Attack and the two defence values. With unit levels one row per level, the unit's current level
 * marked; otherwise (levels off, or a unit without levels) one row with the values it has.
 */
function StatsTable({ unit, leveled }: { unit: SmithyUnit; leveled: boolean }) {
  const { t, language } = useI18n();
  const s = t.smithy;
  const rows = leveled
    ? unit.stats.map((stats, index) => ({ level: index + 1, stats }))
    : [{ level: null, stats: statsAt(unit.stats, unit.level) }];
  return (
    <table className={styles.levels}>
      <caption className="sr-only">{s.statsLabel(t.units[unit.unit])}</caption>
      <thead>
        <tr>
          <td />
          <th scope="col">
            <abbr title={s.attack}>{s.attack}</abbr>
          </th>
          <th scope="col">
            <abbr title={s.defenseInfantry}>{s.defenseInfantryShort}</abbr>
          </th>
          <th scope="col">
            <abbr title={s.defenseCavalry}>{s.defenseCavalryShort}</abbr>
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map(({ level, stats }) => (
          <tr key={level ?? 0} className={level === unit.level ? styles.current : undefined}>
            <th scope="row">
              {level === null ? <span className="sr-only">{s.level(unit.level)}</span> : s.level(level)}
            </th>
            <td>{formatNumber(stats.attack, language)}</td>
            <td>{formatNumber(stats.defenseInfantry, language)}</td>
            <td>{formatNumber(stats.defenseCavalry, language)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

interface ResearchBlockProps {
  unit: SmithyUnit;
  village: Village;
  starting: boolean;
  anyStarting: boolean;
  onStart: () => void;
}

/** Below the values: the research that is running, the top level, or the next research. */
function ResearchBlock({ unit, village, starting, anyStarting, onStart }: ResearchBlockProps) {
  const { t } = useI18n();
  const s = t.smithy;
  if (unit.researching !== null) {
    const here = unit.researching.villageId === village.id;
    return (
      <p className={styles.research}>
        <strong>{s.researching(t.units[unit.unit], unit.researching.level)}</strong>{' '}
        <span className={styles.hint}>
          {here ? null : `${s.elsewhere} `}
          {s.readyIn} <Countdown until={unit.researching.endAt} />
        </span>
      </p>
    );
  }
  if (unit.next === null) {
    return <p className={styles.research}>{s.maxed}</p>;
  }
  return (
    <NextResearchBlock
      unitType={unit.unit}
      next={unit.next}
      village={village}
      starting={starting}
      anyStarting={anyStarting}
      onStart={onStart}
    />
  );
}

/** What the next level costs and needs, why it can't be started yet, and the button. */
function NextResearchBlock(props: {
  unitType: UnitType;
  next: NextResearch;
  village: Village;
  starting: boolean;
  anyStarting: boolean;
  onStart: () => void;
}) {
  const { next, village, starting, anyStarting, onStart } = props;
  const { t, language } = useI18n();
  const s = t.smithy;
  const now = useServerNow();
  const stock: ResourceAmounts = projectResources(village, now);
  const affordable = (['wood', 'clay', 'iron'] as const).every((type) => Math.floor(stock[type]) >= next.cost[type]);
  const block = researchBlock({ missing: next.missing, villageBusy: next.villageBusy, affordable });

  let hint: string | null = null;
  if (block === 'requirements') {
    hint = t.townHall.needs(
      next.missing
        .map((requirement) => t.townHall.requirement(t.buildings[requirement.building], requirement.level))
        .join(', '),
    );
  } else if (block === 'busy') {
    hint = s.busy;
  } else if (block === 'resources') {
    const when = whenAffordable(village, next.cost, now);
    if (when.kind === 'warehouse') {
      hint = t.townHall.warehouseTooSmall;
    } else if (when.kind === 'never') {
      hint = t.townHall.notProduced;
    } else if (when.kind === 'later') {
      hint = t.townHall.enoughAt(formatUpcoming(when.atMs, now, language));
    }
  }
  return (
    <div className={styles.research}>
      <strong>{s.next(next.level)}</strong>
      <CostList
        label={s.costsLabel}
        timeLabel={s.time}
        cost={next.cost}
        seconds={next.durationSeconds}
        stock={stock}
        className={styles.costs}
      />
      <div className={styles.action}>
        {hint !== null && <p className={styles.hint}>{hint}</p>}
        <Button type="button" size="small" disabled={block !== null || anyStarting} onClick={onStart}>
          {starting ? s.starting : s.research}
        </Button>
      </div>
    </div>
  );
}

import { useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCancelResearch, type NextResearch, type ResearchOrder, type SmithyUnit } from '../../api/queries/smithy';
import type { Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { CostList } from '../../components/CostList';
import { Countdown } from '../../components/Countdown';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { formatDecimal, formatNumber, formatShare, formatUpcoming } from '../../game/format';
import type { UnitType } from '../../game/gameData';
import { projectResources, whenAffordable, type ResourceAmounts } from '../../game/resources';
import { useGameNow } from '../../game/serverTime';
import { researchBlock, statsAt } from '../../game/unitLevels';
import { useI18n } from '../../i18n';
import type { Dictionary } from '../../i18n/en';
import { recruitPath } from '../recruit/recruitPaths';
import styles from './Smithy.module.css';

/**
 * The parts of a research page (M30), shared by the smithy page and the Banner Hall's training
 * (Hall training, 1.4): the running research with its cancel button, and one row per unit with its
 * values and the next level. `place` is the building whose page shows them: it picks the texts
 * ("Research" or "Train") and offers the next level only for the units researched there.
 */
export type ResearchPlace = 'SMITHY' | 'BANNER_HALL';

/** The smithy's texts, with the hall's own words for its training in their place. */
function researchTexts(t: Dictionary, place: ResearchPlace) {
  return place === 'SMITHY' ? t.smithy : { ...t.smithy, ...t.hallTraining };
}

/** The research that runs in this village's building (one at a time), with a cancel button. */
export function ResearchCard(props: { villageId: number; order: ResearchOrder | null; place: ResearchPlace }) {
  const { villageId, order, place } = props;
  const { t } = useI18n();
  const s = researchTexts(t, place);
  const now = useGameNow();
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
  place: ResearchPlace;
  /** The world setting: off, the smithy's own units show no levels. */
  levelsEnabled: boolean;
  /** The banner thief's chance per surviving thief at each level; shown instead of his values. */
  theftChances?: readonly number[] | null;
  /** This unit's research is being sent right now. */
  starting: boolean;
  /** Some research is being sent right now (all buttons wait). */
  anyStarting: boolean;
  error: Error | null;
  onStart: () => void;
}

/** One unit: its values at its level, the values of every level, and the next research. */
export function UnitRow(props: UnitRowProps) {
  const { unit, village, place, levelsEnabled, theftChances, starting, anyStarting, error, onStart } = props;
  const { t, language } = useI18n();
  const s = researchTexts(t, place);
  // units without research steps show nothing about levels; the hall's levels count in every world
  const leveled = unit.maxLevel > 1 && (levelsEnabled || unit.trainedIn === 'BANNER_HALL');
  const elsewhere = unit.trainedIn !== place;
  return (
    <li className={styles.row}>
      <div className={styles.unitHead}>
        <span className={styles.icon}>
          <Icon name={unit.unit} />
        </span>
        <h3 className={styles.name}>{t.units[unit.unit]}</h3>
        {leveled && <span className={styles.badge}>{s.levelOf(unit.level, unit.maxLevel)}</span>}
      </div>
      {theftChances != null && unit.unit === 'BANNER_THIEF' ? (
        <ChanceTable unit={unit} chances={theftChances} />
      ) : (
        // the thief's levels keep his values: one row, not three equal ones (the hall shows his chances)
        <StatsTable unit={unit} leveled={leveled && unit.unit !== 'BANNER_THIEF'} />
      )}
      {place === 'SMITHY' && (
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
      )}
      {leveled && unit.unit === 'SCOUT' && <p className={styles.hint}>{s.scoutHint}</p>}
      {place === 'SMITHY' && unit.unit === 'PALADIN' && <p className={styles.hint}>{s.paladinHint}</p>}
      {place === 'BANNER_HALL' && unit.unit === 'PALADIN' && (
        <p className={styles.hint}>{t.hallTraining.paladinLevels}</p>
      )}
      {place === 'BANNER_HALL' && unit.unit === 'BANNER_THIEF' && (
        <p className={styles.hint}>{t.hallTraining.thiefLevels}</p>
      )}
      {leveled && elsewhere && (
        <p className={styles.research}>
          <span className={styles.hint}>{t.hallTraining.inTheHall}</span>{' '}
          <Link className={styles.link} to={recruitPath(village.id, 'BANNER_HALL')}>
            {t.hallTraining.toHall}
          </Link>
        </p>
      )}
      {leveled && !elsewhere && (
        <ResearchBlock
          unit={unit}
          village={village}
          place={place}
          starting={starting}
          anyStarting={anyStarting}
          onStart={onStart}
        />
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

/** The banner thief's levels change no values, only his chance to steal (02-game-rules §21.6.2). */
function ChanceTable({ unit, chances }: { unit: SmithyUnit; chances: readonly number[] }) {
  const { t, language } = useI18n();
  return (
    <table className={styles.levels}>
      <caption className="sr-only">{t.hallTraining.chanceLabel}</caption>
      <thead>
        <tr>
          <td />
          <th scope="col">{t.hallTraining.chancePerThief}</th>
        </tr>
      </thead>
      <tbody>
        {chances.map((chance, index) => (
          <tr key={index} className={index + 1 === unit.level ? styles.current : undefined}>
            <th scope="row">{t.smithy.level(index + 1)}</th>
            <td>{formatShare(chance * 100, language)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

interface ResearchBlockProps {
  unit: SmithyUnit;
  village: Village;
  place: ResearchPlace;
  starting: boolean;
  anyStarting: boolean;
  onStart: () => void;
}

/** Below the values: the research that is running, the top level, or the next research. */
function ResearchBlock({ unit, village, place, starting, anyStarting, onStart }: ResearchBlockProps) {
  const { t } = useI18n();
  const s = researchTexts(t, place);
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
      place={place}
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
  place: ResearchPlace;
  starting: boolean;
  anyStarting: boolean;
  onStart: () => void;
}) {
  const { next, village, place, starting, anyStarting, onStart } = props;
  const { t, language } = useI18n();
  const s = researchTexts(t, place);
  const now = useGameNow();
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

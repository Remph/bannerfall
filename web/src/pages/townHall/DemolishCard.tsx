import { useState, type ReactNode } from 'react';
import { errorMessage } from '../../api/errors';
import { useDemolish, type BuildingInfo, type Demolition } from '../../api/queries/buildings';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { formatDuration } from '../../game/format';
import { useT } from '../../i18n';
import type { Dictionary } from '../../i18n/en';
import { BuildingHead } from './BuildingHead';
import styles from './TownHallPage.module.css';

interface DemolishCardProps {
  villageId: number;
  /** The village's buildings in the UI's order; the ones never built are left out. */
  buildings: BuildingInfo[];
  queueFull: boolean;
}

/**
 * The demolish side of the Town Hall's switch (1.6, 02-game-rules §4.5): every built building with
 * what its next demolition takes, frees and costs, and the button for it. A demolition can't be
 * undone once it is done, so it never happens on a single tap: a sheet names the levels, the time
 * and what it locks first. Phones show one slim line per building (time and a "−1" button); the freed
 * population and the points are in the sheet.
 */
export function DemolishCard({ villageId, buildings, queueFull }: DemolishCardProps) {
  const t = useT();
  const demolish = useDemolish(villageId);
  const [confirming, setConfirming] = useState<BuildingInfo | null>(null);
  const shown = buildings.filter((info) => info.level > 0 || info.queuedLevel > 0);

  function close() {
    setConfirming(null);
    demolish.reset();
  }

  return (
    <Card title={t.townHall.demolish.title}>
      <p className={styles.notice}>{t.townHall.demolish.intro}</p>
      {queueFull && <p className={styles.notice}>{t.townHall.queueFull}</p>}
      <ul className={styles.list}>
        {shown.map((info) => (
          <li key={info.building} id={info.building} className={`${styles.row} ${styles.demolishRow}`}>
            <BuildingHead info={info} infoButton={null} />
            {info.demolish === null ? (
              <p className={styles.maxed}>
                {info.building === 'RALLY_POINT'
                  ? t.townHall.demolish.rallyPoint
                  : t.townHall.demolish.lowest(info.queuedLevel)}
              </p>
            ) : info.demolish.blocker === 'BUILD_ORDER_CONFLICT' ? (
              // its facts would be those of the level the queued upgrade adds: only say why not
              <p className={styles.maxed}>{t.townHall.demolish.conflict}</p>
            ) : (
              <DemolishLine
                info={info}
                demolition={info.demolish}
                disabled={queueFull || demolish.isPending}
                onDemolish={() => setConfirming(info)}
              />
            )}
          </li>
        ))}
      </ul>
      <Sheet
        open={confirming !== null}
        onClose={close}
        title={confirming === null ? '' : t.townHall.demolish.confirmTitle(t.buildings[confirming.building])}
      >
        {confirming !== null && confirming.demolish !== null && (
          <DemolishConfirm info={confirming} demolition={confirming.demolish}>
            {demolish.isError && <ErrorMessage>{errorMessage(t, demolish.error)}</ErrorMessage>}
            <div className={styles.confirmActions}>
              <Button type="button" variant="secondary" onClick={close}>
                {t.townHall.demolish.keep}
              </Button>
              {/* solid red (1.6 UI review): the one irreversible step here; `danger` only turns red on hover */}
              <Button
                type="button"
                variant="attack"
                disabled={demolish.isPending}
                onClick={() => demolish.mutate(confirming.building, { onSuccess: close })}
              >
                {demolish.isPending ? t.townHall.demolish.queueing : t.townHall.demolish.confirm}
              </Button>
            </div>
          </DemolishConfirm>
        )}
      </Sheet>
    </Card>
  );
}

/** Time, freed population and points of the next demolition, why it can't be queued, and the button. */
function DemolishLine(props: {
  info: BuildingInfo;
  demolition: Demolition;
  disabled: boolean;
  onDemolish: () => void;
}) {
  const { info, demolition, disabled, onDemolish } = props;
  const t = useT();
  const blocker = blockerText(t, demolition);
  return (
    <div className={styles.demolishLine}>
      <ul className={styles.demolishFacts} aria-label={t.townHall.demolish.factsLabel}>
        <li>
          <Icon name="clock" />
          <span className="sr-only">{t.townHall.demolish.time}</span>
          {formatDuration(demolition.durationSeconds)}
        </li>
        {demolition.pop > 0 && (
          <li className={styles.demolishExtra}>
            <Icon name="population" />
            <span aria-hidden="true">{t.townHall.demolish.frees(demolition.pop)}</span>
            <span className="sr-only">{t.townHall.demolish.freesLong(demolition.pop)}</span>
          </li>
        )}
        {demolition.points > 0 && (
          <li className={styles.demolishExtra}>{t.townHall.demolish.points(demolition.points)}</li>
        )}
      </ul>
      {blocker !== null && <p className={styles.hint}>{blocker}</p>}
      <Button
        type="button"
        size="small"
        variant="secondary"
        disabled={disabled || blocker !== null}
        aria-label={t.townHall.demolish.buttonLabel(t.buildings[info.building], demolition.level)}
        onClick={onDemolish}
      >
        <span className={styles.demolishLong}>{t.townHall.demolish.button(demolition.level)}</span>
        <span className={styles.demolishShort} aria-hidden="true">
          {t.townHall.demolish.buttonShort}
        </span>
      </Button>
    </div>
  );
}

/** Why the server would refuse the demolition right now (a full queue is said once above the list). */
function blockerText(t: Dictionary, demolition: Demolition): string | null {
  const blocker = demolition.blocker;
  if (blocker === null || blocker === undefined) {
    return null;
  }
  if (blocker === 'NOT_ENOUGH_POPULATION') {
    return t.townHall.demolish.farmTooSmall;
  }
  return t.errors[blocker];
}

/** The sheet's text: the levels, the time, what it frees, and what it locks afterwards. */
function DemolishConfirm({
  info,
  demolition,
  children,
}: {
  info: BuildingInfo;
  demolition: Demolition;
  children: ReactNode;
}) {
  const t = useT();
  const locks = [
    ...demolition.locksBuildings.map((building) => t.townHall.demolish.locksBuilding(t.buildings[building])),
    ...demolition.locksUnits.map((unit) => t.townHall.demolish.locksUnit(t.units[unit])),
  ];
  return (
    <div className={styles.confirm}>
      <p>
        <strong>{t.townHall.demolish.confirmLevels(info.queuedLevel, demolition.level)}</strong>
        {t.townHall.demolish.confirmText(formatDuration(demolition.durationSeconds))}
        {demolition.pop > 0 && t.townHall.demolish.confirmFrees(demolition.pop)}
        {demolition.points > 0 && t.townHall.demolish.confirmPoints(demolition.points)}
      </p>
      {info.building === 'WAREHOUSE' && <p className={styles.notice}>{t.townHall.demolish.warehouse}</p>}
      {locks.length > 0 && (
        <div className={styles.notice}>
          <strong>{t.townHall.demolish.locksTitle}</strong>
          <ul className={styles.demolishLocks}>
            {locks.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
      )}
      {children}
    </div>
  );
}

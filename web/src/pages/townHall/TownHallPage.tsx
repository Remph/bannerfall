import { useEffect, useState } from 'react';
import { Navigate, useLocation, useParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useBuildings, useEnqueueBuild, type BuildingInfo, type NextLevel } from '../../api/queries/buildings';
import { useVillage, type Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { CostList } from '../../components/CostList';
import { DevCheats } from '../../components/DevCheats';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { bannerHallAvailable } from '../../game/banners';
import { byBuildingOrder } from '../../game/gameData';
import { projectResources, type ResourceAmounts } from '../../game/resources';
import { useGameNow } from '../../game/serverTime';
import { upgradeBlocker } from '../../game/upgradePreview';
import { useI18n, useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { AutomationRow } from '../automation/AutomationRow';
import { VillageError } from '../village/VillageError';
import { BuildingHead } from './BuildingHead';
import { BuildQueueCard } from './BuildQueueCard';
import { DemolishCard } from './DemolishCard';
import { PreviewRow } from './PreviewRow';
import { blockerText } from './blockerText';
import type { PreviewContext } from './UpgradePreview';
import styles from './TownHallPage.module.css';

/**
 * /v/:villageId/hq – the Town Hall: upgrade or (1.6) demolish buildings, manage the build queue
 * (05-frontend §3).
 */
export function TownHallPage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  return <TownHall key={villageId} villageId={villageId} />;
}

function TownHall({ villageId }: { villageId: number }) {
  const t = useT();
  const village = useVillage(villageId);
  const buildings = useBuildings(villageId);
  const world = useWorld().data;
  const queueSize = world?.buildQueueSize;
  const queueMax = world?.buildQueueMax;
  const now = useGameNow();
  // the switch at the top of the buildings (1.6): upgrading or demolishing
  const [mode, setMode] = useState<'build' | 'demolish'>('build');
  useScrollToBuilding(buildings.isSuccess);

  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (buildings.isError) {
    return <VillageError error={buildings.error} onRetry={() => void buildings.refetch()} />;
  }
  if (village.isPending || buildings.isPending) {
    return <p>{t.common.loading}</p>;
  }

  const hqLevel = village.data.buildings.HEADQUARTERS ?? 0;
  const queueFull = queueMax !== undefined && village.data.buildQueue.length >= queueMax;
  const preview: PreviewContext = {
    village: village.data,
    stock: projectResources(village.data, now),
    freePop: village.data.population.capacity - village.data.population.used,
    queueFull,
    now,
    // until the world is loaded the server has the last word (it refuses with BANNERS_DISABLED)
    hallAvailable: world === undefined ? true : bannerHallAvailable(world),
  };
  // the server lists them in its enum order; shown in the UI's (the Banner Hall after the Workshop)
  const sorted = [...buildings.data].sort((a, b) => byBuildingOrder(a.building, b.building));
  const available = sorted.filter((info) => info.requirementsMet);
  const locked = sorted.filter((info) => !info.requirementsMet);
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <BackLink to={`/v/${village.data.id}`}>{t.townHall.back}</BackLink>
        <h1>{t.townHall.title}</h1>
        <p className={styles.meta}>
          {village.data.name} · {t.village.level(hqLevel)}
        </p>
      </header>
      <div className={styles.layout}>
        <div className={styles.queue}>
          <AutomationRow villageId={village.data.id} />
          <BuildQueueCard village={village.data} queueSize={queueSize} queueMax={queueMax} />
        </div>
        <div className={styles.buildings}>
          <SegmentedControl
            label={t.townHall.modeLabel}
            options={[
              { value: 'build', label: t.townHall.modes.build },
              { value: 'demolish', label: t.townHall.modes.demolish },
            ]}
            value={mode}
            onChange={setMode}
          />
          {mode === 'build' ? (
            <>
              <AvailableBuildings preview={preview} buildings={available} />
              {locked.length > 0 && <LockedBuildings buildings={locked} preview={preview} />}
            </>
          ) : (
            <DemolishCard villageId={villageId} buildings={sorted} queueFull={queueFull} />
          )}
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
 * The village overview links to `/v/1/hq#WOODCUTTER`: once the list is there, scroll that building
 * into view. (The browser can't do it by itself: the element doesn't exist yet when the page opens.)
 */
function useScrollToBuilding(ready: boolean) {
  const { hash } = useLocation();
  useEffect(() => {
    if (ready && hash.length > 1) {
      document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'center' });
    }
  }, [ready, hash]);
}

interface AvailableProps {
  preview: PreviewContext;
  buildings: BuildingInfo[];
}

/** Buildings whose requirements are met: what they cost next and the button to queue it. */
function AvailableBuildings({ preview, buildings }: AvailableProps) {
  const t = useT();
  const { village, stock, freePop, queueFull, now, hallAvailable } = preview;
  const enqueue = useEnqueueBuild(village.id);
  const target = useLocation().hash.slice(1);
  // The server prices every next level at the next free place, so they all share the surcharge.
  const surcharge = queueFull ? 0 : (buildings.find((info) => info.next !== null)?.next?.surchargePercent ?? 0);

  return (
    <Card title={t.townHall.buildingsTitle}>
      {queueFull && <p className={styles.notice}>{t.townHall.queueFull}</p>}
      {surcharge > 0 && <p className={styles.notice}>{t.townHall.surchargeNote(surcharge)}</p>}
      <ul className={styles.list}>
        {buildings.map((info) => (
          <PreviewRow key={info.building} info={info} context={preview} className={rowClass(info, target)}>
            {(infoButton) => (
              <>
                <BuildingHead info={info} infoButton={infoButton} />
                {info.next === null ? (
                  <p className={styles.maxed}>{t.townHall.maxLevel}</p>
                ) : (
                  <NextLevelRow
                    info={info}
                    next={info.next}
                    village={village}
                    stock={stock}
                    freePop={freePop}
                    queueFull={queueFull}
                    now={now}
                    hallAvailable={hallAvailable}
                    pending={enqueue.isPending}
                    queueing={enqueue.isPending && enqueue.variables === info.building}
                    error={enqueue.isError && enqueue.variables === info.building ? enqueue.error : null}
                    onUpgrade={() => enqueue.mutate(info.building)}
                  />
                )}
              </>
            )}
          </PreviewRow>
        ))}
      </ul>
    </Card>
  );
}

interface NextLevelRowProps {
  info: BuildingInfo;
  next: NextLevel;
  village: Village;
  stock: ResourceAmounts;
  freePop: number;
  queueFull: boolean;
  now: number;
  hallAvailable: boolean;
  /** Some upgrade is being queued right now (all buttons wait). */
  pending: boolean;
  /** This building's upgrade is being queued right now. */
  queueing: boolean;
  error: Error | null;
  onUpgrade: () => void;
}

/** Costs, population and build time of the next level, why it can't be queued yet, and the button. */
function NextLevelRow(props: NextLevelRowProps) {
  const { info, next, village, stock, freePop, queueFull, now, hallAvailable, pending, queueing, error, onUpgrade } =
    props;
  const { t, language } = useI18n();
  const blocker = upgradeBlocker(info, village, freePop, queueFull, now, hallAvailable);
  const canQueue = blocker === null;

  // A Banner Hall in a world without banners (D11): no costs and no button that could only fail, just why.
  if (blocker?.kind === 'bannersDisabled') {
    return <p className={styles.maxed}>{blockerText(t, language, now, blocker)}</p>;
  }

  // Why it can't be queued, the most useful reason first (upgradeBlocker). A full queue is said once
  // above the list instead of under every button.
  const hint = blocker === null || blocker.kind === 'queueFull' ? null : blockerText(t, language, now, blocker);

  return (
    <>
      <CostList
        className={styles.costs}
        label={t.townHall.costsLabel}
        timeLabel={t.townHall.buildTime}
        cost={next.cost}
        pop={next.pop}
        seconds={next.durationSeconds}
        stock={stock}
        freePop={freePop}
      />
      <div className={styles.action}>
        {hint !== null && <p className={styles.hint}>{hint}</p>}
        <Button type="button" size="small" disabled={!canQueue || pending} onClick={onUpgrade}>
          {queueing ? t.townHall.queueing : next.level === 1 ? t.townHall.build : t.townHall.upgrade(next.level)}
          {!queueing && !queueFull && next.surchargePercent > 0 && (
            <span className={styles.surcharge}>{t.townHall.surcharge(next.surchargePercent)}</span>
          )}
        </Button>
      </div>
      {error !== null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
    </>
  );
}

/** The row the village overview linked to (`#WOODCUTTER`) is highlighted. */
function rowClass(info: BuildingInfo, target: string): string | undefined {
  return info.building === target ? `${styles.row} ${styles.target}` : styles.row;
}

/** Buildings whose requirements aren't met yet, with what is missing (queued levels count). */
function LockedBuildings({ buildings, preview }: { buildings: BuildingInfo[]; preview: PreviewContext }) {
  const t = useT();
  const target = useLocation().hash.slice(1);
  return (
    <Card title={t.townHall.lockedTitle}>
      <ul className={styles.list}>
        {buildings.map((info) => (
          <PreviewRow
            key={info.building}
            info={info}
            context={preview}
            className={`${rowClass(info, target)} ${styles.locked}`}
          >
            {(infoButton) => (
              <>
                <BuildingHead info={info} infoButton={infoButton} />
                <p className={styles.needs}>
                  {t.townHall.needs(
                    info.missing
                      .map((requirement) =>
                        t.townHall.requirement(t.buildings[requirement.building], requirement.level),
                      )
                      .join(', '),
                  )}
                </p>
              </>
            )}
          </PreviewRow>
        ))}
      </ul>
    </Card>
  );
}

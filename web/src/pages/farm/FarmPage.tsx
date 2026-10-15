import { useEffect, useMemo } from 'react';
import { Navigate, useLocation, useParams, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useFarmTargets, useSendAllFarm } from '../../api/queries/farm';
import { useVillage } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import {
  FARM_LIST_MAX,
  bannerRollBackAt,
  farmSendEndsProtection,
  nextBannerRollBackIn,
  sendAllNotes,
} from '../../game/farm';
import { serverNow } from '../../game/serverTime';
import { useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import { targetFromSearch } from '../rally/rallyPaths';
import { AddTargetCard } from './AddTargetCard';
import { SendAllBar } from './SendAllBar';
import { unitsFromState } from './farmPaths';
import styles from './Farm.module.css';
import { TargetRow } from './TargetRow';

/**
 * /v/:villageId/farm – the farm list: targets with the usual troops, one tap sends one attack, "Send all"
 * the list from the top (02-game-rules §18, M39).
 */
export function FarmPage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village starts with its own form
  return <Farm key={villageId} villageId={villageId} />;
}

function Farm({ villageId }: { villageId: number }) {
  const t = useT();
  const me = useCurrentAccount();
  const village = useVillage(villageId);
  const targets = useFarmTargets(villageId);
  const sendAll = useSendAllFarm(villageId);
  const world = useWorld().data;
  // the banner button only where banners can drop (02-game-rules §21.7)
  const bannerDrops = world !== undefined && world.bannersEnabled && world.bannerDropFactor > 0;
  const targetList = targets.data;
  const refetchTargets = targets.refetch;
  // a "Banner chance used" pill goes when its time is up: the list is fetched again then (a second
  // later, so the server's clock is past it too)
  useEffect(() => {
    const backIn =
      targetList === undefined
        ? undefined
        : nextBannerRollBackIn(
            targetList.map((target) => target.nextBannerRollAt),
            serverNow(),
          );
    if (backIn === undefined) {
      return;
    }
    const timer = setTimeout(() => void refetchTargets(), backIn + 1000);
    return () => clearTimeout(timer);
  }, [targetList, refetchTargets]);
  // one object per note and run: a row hides its note once it has sent by itself since (TargetRow)
  const notes = useMemo(() => (sendAll.data === undefined ? undefined : sendAllNotes(sendAll.data)), [sendAll.data]);
  const [params] = useSearchParams();
  const initialTarget = targetFromSearch(params);
  // from an attack report's "Add to farm list": the units it sent (M38)
  const initialUnits = unitsFromState(useLocation().state);

  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (village.isPending) {
    return <p>{t.common.loading}</p>;
  }
  const home = village.data.unitsHome;
  const add = (
    <AddTargetCard villageId={villageId} home={home} initialTarget={initialTarget} initialUnits={initialUnits} />
  );
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.farm.title}</h1>
        <p className={styles.hint}>{t.farm.explainer}</p>
      </header>
      {/* coming from "Add to farm list" on the map or a report: the form first */}
      {initialTarget !== '' && add}
      <Card title={t.farm.listTitle(village.data.name)}>
        {targets.isError ? (
          <div className={styles.body}>
            <ErrorMessage>{errorMessage(t, targets.error)}</ErrorMessage>
          </div>
        ) : targets.isPending ? (
          <div className={styles.body}>
            <p>{t.common.loading}</p>
          </div>
        ) : targets.data.length === 0 ? (
          <div className={styles.body}>
            <p className={styles.empty}>{t.farm.empty}</p>
          </div>
        ) : (
          <>
            <SendAllBar
              targets={targets.data}
              // serverNow(), not ticking: as for the rows, a protection ending days later
              leavesPlayersOut={targets.data.some((target) =>
                farmSendEndsProtection(Date.parse(me.protectedUntil), target.target.playerId, serverNow()),
              )}
              bannerChances={bannerDrops ? targets.data.filter((target) => target.bannerChance).length : undefined}
              pending={sendAll.isPending}
              result={sendAll.data}
              error={sendAll.error}
              onSend={(bannerChanceOnly) => sendAll.mutate(bannerChanceOnly)}
            />
            <ul className={styles.list}>
              {targets.data.map((target) => (
                <li key={target.id}>
                  <TargetRow
                    villageId={villageId}
                    target={target}
                    home={home}
                    note={notes?.get(target.id)}
                    sendingAll={sendAll.isPending}
                  />
                </li>
              ))}
            </ul>
            <div className={styles.body}>
              <p className={styles.hint}>{t.farm.count(targets.data.length, FARM_LIST_MAX)}</p>
              {targets.data.some((target) => bannerRollBackAt(target.nextBannerRollAt, serverNow()) !== undefined) && (
                <p className={styles.hint}>{t.farm.bannerRollHint}</p>
              )}
            </div>
          </>
        )}
      </Card>
      {initialTarget === '' && add}
    </div>
  );
}

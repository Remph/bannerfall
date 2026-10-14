import { useEffect } from 'react';
import { Link, Navigate, useLocation, useParams } from 'react-router';
import { useRefreshWhenMovementsArrive, useTroops } from '../../api/queries/troops';
import { useVillage } from '../../api/queries/villages';
import { DevCheats } from '../../components/DevCheats';
import { formatCoords } from '../../game/format';
import { useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import { IncomingCard, OutgoingCard } from './MovementCards';
import styles from './RallyPage.module.css';
import { simulatorPath } from './rallyPaths';
import { SendCard } from './SendCard';
import { TroopsCard } from './TroopsCard';

/**
 * /v/:villageId/rally?x=&y= – the rally point (05-frontend §3): send attacks and supports, the
 * movements on the way, and the troops of and in the village. Phone: one column; desktop: sending
 * and troops on the left, the movements on the right.
 */
export function RallyPage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village starts with an empty form
  return <Rally key={villageId} villageId={villageId} />;
}

function Rally({ villageId }: { villageId: number }) {
  const t = useT();
  const village = useVillage(villageId);
  const troops = useTroops(villageId);
  // arrivals show up by themselves while the screen is open
  useRefreshWhenMovementsArrive(villageId, troops.data, troops.dataUpdatedAt, troops.errorUpdatedAt);
  // `#incoming` (the overview's attack notice): scroll to that card once the data is there
  const { hash } = useLocation();
  const ready = village.isSuccess && troops.isSuccess;
  useEffect(() => {
    if (ready && hash.length > 1) {
      document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'center' });
    }
  }, [ready, hash]);

  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (troops.isError) {
    return <VillageError error={troops.error} onRetry={() => void troops.refetch()} />;
  }
  if (village.isPending || troops.isPending) {
    return <p>{t.common.loading}</p>;
  }

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.rally.title}</h1>
        <p className={styles.meta}>
          {village.data.name} {formatCoords(village.data.x, village.data.y)}
        </p>
        <Link className={styles.simulatorLink} to={simulatorPath(villageId)}>
          {t.simulator.title}
        </Link>
      </header>
      <div className={styles.layout}>
        <div className={styles.send}>
          <SendCard village={village.data} home={troops.data.home} />
        </div>
        <div className={styles.moves}>
          <OutgoingCard villageId={villageId} movements={troops.data.outgoing} />
          <IncomingCard villageId={villageId} incoming={troops.data.incoming} />
        </div>
        <div className={styles.troops}>
          <TroopsCard villageId={villageId} troops={troops.data} />
        </div>
        {import.meta.env.DEV && (
          <div className={styles.dev}>
            <DevCheats villageId={villageId} movements />
          </div>
        )}
      </div>
    </div>
  );
}

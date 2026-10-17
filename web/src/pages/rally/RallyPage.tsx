import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useParams, useSearchParams } from 'react-router';
import { useRefreshWhenMovementsArrive, useTroops, type Troops } from '../../api/queries/troops';
import { useVillage, type Village } from '../../api/queries/villages';
import { DevCheats } from '../../components/DevCheats';
import { SegmentedControl } from '../../components/SegmentedControl';
import { formatCoords } from '../../game/format';
import { useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import { IncomingCard, OutgoingCard } from './MovementCards';
import styles from './RallyPage.module.css';
import { initialRallyPart, opensMoves, simulatorPath, type RallyPart } from './rallyPaths';
import { SendCard } from './SendCard';
import { TroopsCard } from './TroopsCard';

/**
 * /v/:villageId/rally?x=&y= – the rally point (05-frontend §3): send attacks and supports, the
 * movements on the way, and the troops of and in the village. Phones and tablets show one part at a
 * time behind a switch (1.3); desktop: sending and troops on the left, the movements on the right
 * (what comes at the village first).
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
  // `#incoming` (the overview's attack notice) or `#outgoing` (the map's line panel): scroll to that
  // card once the data is there, and again on every new visit (`key`), e.g. the attack badge tapped
  // while the page is open
  const { hash, key } = useLocation();
  const ready = village.isSuccess && troops.isSuccess;
  useEffect(() => {
    if (ready && hash.length > 1) {
      document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'center' });
    }
  }, [ready, hash, key]);

  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (troops.isError) {
    return <VillageError error={troops.error} onRetry={() => void troops.refetch()} />;
  }
  if (village.isPending || troops.isPending) {
    return <p>{t.common.loading}</p>;
  }

  return <RallyParts village={village.data} troops={troops.data} />;
}

/**
 * The page once its data is there. The switch only shows below the desktop width; the hidden parts
 * stay mounted (CSS), so a half-filled send form survives a look at the movements. It opens on the
 * movements while an attack is coming in (or for `#incoming` and `#outgoing`), and on the form when
 * the address names a target; after that only the player switches, except that a new visit to
 * `#incoming` or `#outgoing` (the attack badge or a notification while the page is open, a line on
 * the map) shows the movements again.
 */
function RallyParts({ village, troops }: { village: Village; troops: Troops }) {
  const t = useT();
  const [search] = useSearchParams();
  const { hash, key } = useLocation();
  const [part, setPart] = useState<RallyPart>(() => initialRallyPart(troops.incoming, search, hash));
  // set while rendering, so the card is visible before Rally's effect scrolls to it
  const [visit, setVisit] = useState(key);
  if (visit !== key) {
    setVisit(key);
    if (opensMoves(hash)) {
      setPart('moves');
    }
  }
  const moving = troops.outgoing.length + troops.incoming.length;
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.rally.title}</h1>
        <p className={styles.meta}>
          {village.name} {formatCoords(village.x, village.y)}
        </p>
        <Link className={styles.simulatorLink} to={simulatorPath(village.id)}>
          {t.simulator.title}
        </Link>
      </header>
      <div className={styles.switchRow}>
        <SegmentedControl
          label={t.rally.partsLabel}
          options={[
            { value: 'send', label: t.rally.partSend },
            { value: 'moves', label: moving === 0 ? t.rally.partMoves : `${t.rally.partMoves} ${moving}` },
            { value: 'troops', label: t.rally.partTroops },
          ]}
          value={part}
          onChange={setPart}
        />
      </div>
      <div className={styles.layout} data-part={part}>
        <div className={styles.send}>
          <SendCard village={village} home={troops.home} />
        </div>
        <div className={styles.moves}>
          {/* what comes at the village first, also above a long list of own farm runs (1.3) */}
          <IncomingCard villageId={village.id} incoming={troops.incoming} />
          <OutgoingCard villageId={village.id} movements={troops.outgoing} />
        </div>
        <div className={styles.troops}>
          <TroopsCard villageId={village.id} troops={troops} />
        </div>
        {import.meta.env.DEV && (
          <div className={styles.dev}>
            <DevCheats villageId={village.id} movements />
          </div>
        )}
      </div>
    </div>
  );
}

import { Link, Navigate, useParams } from 'react-router';
import type { Village } from '../../api/queries/villages';
import { useVillage } from '../../api/queries/villages';
import { BackLink } from '../../components/BackLink';
import { BuildOrderLine, BuildOrderList } from '../../components/BuildOrderLine';
import { Card } from '../../components/Card';
import { Icon } from '../../components/Icon';
import { formatDuration, formatNumber, formatUpcoming } from '../../game/format';
import { isFull, projectResources, resourceTypes, whenFull, type ResourceType } from '../../game/resources';
import { secondsUntil, useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import styles from './Warehouse.module.css';

/**
 * /v/:villageId/warehouse – what the warehouse holds and when each resource will be full (M22). Reached from the village overview and from wood, clay and iron in the resource bar
 * (M35); it has no tab of its own.
 */
export function WarehousePage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village gets a fresh page
  return <Warehouse key={villageId} villageId={villageId} />;
}

function Warehouse({ villageId }: { villageId: number }) {
  const { t, language } = useI18n();
  const village = useVillage(villageId);
  if (village.isPending) {
    return <p>{t.common.loading}</p>;
  }
  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  const data = village.data;
  const level = data.buildings.WAREHOUSE ?? 0;
  const upgrades = data.buildQueue.filter((order) => order.building === 'WAREHOUSE');
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <BackLink to={`/v/${data.id}`}>{t.warehouse.back}</BackLink>
        <h1>{t.warehouse.title}</h1>
        <p className={styles.hint}>
          {t.warehouse.holds(t.village.level(level), formatNumber(data.storageCapacity, language))}
        </p>
      </header>
      <Card title={t.warehouse.fullTitle}>
        <ul className={styles.list}>
          {resourceTypes.map((type) => (
            <ResourceRow key={type} village={data} type={type} />
          ))}
        </ul>
        <p className={`${styles.note} ${styles.hint}`}>{t.warehouse.assumption}</p>
      </Card>
      <Card title={t.warehouse.upgradeTitle}>
        <div className={styles.body}>
          <p className={styles.hint}>{t.warehouse.upgradeText}</p>
          {upgrades.length > 0 && (
            <BuildOrderList>
              {upgrades.map((order) => (
                <BuildOrderLine key={order.id} order={order} />
              ))}
            </BuildOrderList>
          )}
          <p>
            <Link className={styles.link} to={{ pathname: `/v/${data.id}/hq`, hash: 'WAREHOUSE' }}>
              {t.warehouse.toTownHall}
            </Link>
          </p>
        </div>
      </Card>
    </div>
  );
}

/** One resource: stock and fill bar, production, and a ticking "full in …". */
function ResourceRow({ village, type }: { village: Village; type: ResourceType }) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const capacity = village.storageCapacity;
  const amount = projectResources(village, now)[type];
  const full = isFull(amount, capacity);
  const fullAt = whenFull(village, type, now);
  const percent = Math.min(100, Math.max(0, (amount / capacity) * 100));
  const rate = formatNumber(village.production[type], language);

  let status;
  if (fullAt.kind === 'full') {
    status = <strong className={styles.warn}>{t.warehouse.isFull}</strong>;
  } else if (fullAt.kind === 'never') {
    status = <span className={styles.hint}>{t.warehouse.notProduced}</span>;
  } else {
    status = (
      <>
        <strong>{t.warehouse.fullIn(formatDuration(secondsUntil(fullAt.atMs, now)))}</strong>
        <span className={styles.hint}>{t.warehouse.fullAt(formatUpcoming(fullAt.atMs, now, language))}</span>
      </>
    );
  }
  return (
    <li className={full ? `${styles.row} ${styles.isFull}` : styles.row}>
      <span className={styles.icon}>
        <Icon name={type} />
      </span>
      <span className={styles.main}>
        <span className={styles.top}>
          <span className={styles.name}>{t.resources[type]}</span>
          <span className={styles.amount}>
            {formatNumber(amount, language)} / {formatNumber(capacity, language)}
          </span>
        </span>
        <span className={styles.track} role="presentation">
          <span className={styles.bar} style={{ width: `${percent}%` }} />
        </span>
        <span className={styles.status}>
          <span className={styles.hint}>{t.resources.perHourLong(rate)}</span>
          <span className={styles.when}>{status}</span>
        </span>
      </span>
    </li>
  );
}

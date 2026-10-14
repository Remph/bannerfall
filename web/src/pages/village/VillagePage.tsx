import type { ReactNode } from 'react';
import { Link, Navigate, useParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useRecruitment, useRefreshWhenUnitsDone } from '../../api/queries/recruitment';
import { useVillage, type Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { BuildOrderLine, BuildOrderList } from '../../components/BuildOrderLine';
import { Banner } from '../../components/Banner';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { RecruitOrderLine, RecruitOrderList } from '../../components/RecruitOrderLine';
import { formatCoords, formatDateTime, formatNumber } from '../../game/format';
import { buildingTypes, unitTypes } from '../../game/gameData';
import { useServerNow } from '../../game/serverTime';
import { useI18n, useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { bannersPath } from '../banners/bannerPaths';
import { warehousePath } from '../warehouse/warehousePaths';
import { buildingTarget } from './buildingTarget';
import { VillageError } from './VillageError';
import { VillageName } from './VillageName';
import styles from './VillagePage.module.css';

/** /v/:villageId – the village overview (05-frontend §3). */
export function VillagePage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village gets a fresh component (e.g. an open rename form doesn't carry over).
  return <VillageOverview key={villageId} villageId={villageId} />;
}

function VillageOverview({ villageId }: { villageId: number }) {
  const { t, language } = useI18n();
  const village = useVillage(villageId);

  if (village.isPending) {
    return <p>{t.common.loading}</p>;
  }
  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }

  const data = village.data;
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <VillageName village={data} />
        <p className={styles.meta}>
          {formatCoords(data.x, data.y)} · {t.village.points(formatNumber(data.points, language))}
        </p>
        <ProtectionNotice />
        <IncomingAttacksNotice village={data} />
      </header>
      <div className={styles.columns}>
        <Buildings village={data} />
        <div className={styles.side}>
          <QueueSummary village={data} />
          <VillageFacts village={data} />
          <TroopsAtHome village={data} />
          <TrainingSummary villageId={data.id} />
        </div>
      </div>
    </div>
  );
}

/** All buildings in the canonical order. Each one links to its own screen or its place in the Town Hall (`buildingTarget`). */
function Buildings({ village }: { village: Village }) {
  const t = useT();
  // a world without banners has no use for the Banner Hall: left out unless one stands there
  const bannersOn = useWorld().data?.bannersEnabled ?? true;
  return (
    <Card title={t.village.buildingsTitle}>
      <ul className={styles.buildings}>
        {buildingTypes.map((type) => {
          const level = village.buildings[type] ?? 0;
          if (type === 'BANNER_HALL' && !bannersOn && level === 0) {
            return null;
          }
          return (
            <li key={type} className={level === 0 ? `${styles.building} ${styles.notBuilt}` : styles.building}>
              <Link className={styles.buildingLink} to={buildingTarget(village.id, type, level)}>
                <span className={styles.buildingIcon}>
                  <Icon name={type} />
                </span>
                <span className={styles.buildingName}>{t.buildings[type]}</span>
                {level === 0 ? (
                  <span className={styles.badgeEmpty}>{t.village.notBuilt}</span>
                ) : (
                  <span className={styles.badge}>{t.village.level(level)}</span>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

/** What is being built, with the countdown of the running order; the controls are in the Town Hall. */
function QueueSummary({ village }: { village: Village }) {
  const t = useT();
  return (
    <Card title={t.buildQueue.title}>
      {village.buildQueue.length === 0 ? (
        <p className={styles.empty}>{t.buildQueue.empty}</p>
      ) : (
        <BuildOrderList>
          {village.buildQueue.map((order) => (
            <BuildOrderLine key={order.id} order={order} />
          ))}
        </BuildOrderList>
      )}
      <p className={styles.cardFooter}>
        <Link className={styles.link} to={`/v/${village.id}/hq`}>
          {t.buildQueue.toTownHall}
        </Link>
      </p>
    </Card>
  );
}

function VillageFacts({ village }: { village: Village }) {
  const { t, language } = useI18n();
  const { used, capacity } = village.population;
  return (
    <Card title={t.village.infoTitle}>
      <dl className={styles.facts}>
        <div>
          <dt>{t.village.population}</dt>
          <dd>
            {formatNumber(used, language)} / {formatNumber(capacity, language)}
          </dd>
        </div>
        <div>
          <dt>{t.village.storage}</dt>
          <dd>
            <Link className={styles.link} to={warehousePath(village.id)}>
              {formatNumber(village.storageCapacity, language)}
            </Link>
          </dd>
        </div>
        <div>
          <dt>{t.village.hiding}</dt>
          <dd>{formatNumber(village.hidingCapacity, language)}</dd>
        </div>
        <div>
          <dt>{t.village.loyaltyLabel}</dt>
          <dd>{village.loyalty}</dd>
        </div>
        {village.banner != null && (
          <div>
            <dt>{t.village.banner}</dt>
            <dd>
              {/* only the owner gets this field from the server (02-game-rules §21.7) */}
              <Link
                className={`${styles.link} ${styles.bannerFact}`}
                to={bannersPath(village.id)}
                title={t.banners.flyingTitle(
                  t.banners.types[village.banner.type],
                  village.banner.tier,
                  village.banner.effectiveTier,
                )}
              >
                <Banner
                  type={village.banner.type}
                  tier={village.banner.tier}
                  size={20}
                  dormant={village.banner.effectiveTier === 0}
                />
                {t.banners.types[village.banner.type]}
              </Link>
            </dd>
          </div>
        )}
      </dl>
    </Card>
  );
}

function TroopsAtHome({ village }: { village: Village }) {
  const { t, language } = useI18n();
  const present = unitTypes.filter((type) => (village.unitsHome[type] ?? 0) > 0);
  return (
    <Card title={t.village.troopsTitle}>
      {present.length === 0 ? (
        <p className={styles.empty}>{t.village.noTroops}</p>
      ) : (
        <dl className={styles.facts}>
          {present.map((type) => (
            <div key={type}>
              <dt className={styles.unit}>
                <span className={styles.unitIcon}>
                  <Icon name={type} />
                </span>
                {t.units[type]}
              </dt>
              <dd>{formatNumber(village.unitsHome[type] ?? 0, language)}</dd>
            </div>
          ))}
        </dl>
      )}
    </Card>
  );
}

/**
 * The running recruit order of each building, with countdowns; the queues and controls are on the
 * recruit screen. While this card is on screen, finished units show up at home by themselves.
 */
function TrainingSummary({ villageId }: { villageId: number }) {
  const t = useT();
  const recruitment = useRecruitment(villageId);
  useRefreshWhenUnitsDone(villageId, recruitment.data, recruitment.dataUpdatedAt, recruitment.errorUpdatedAt);
  // each building trains one order at a time: the first of its queue
  const running = (recruitment.data ?? []).flatMap((info) => info.queue.slice(0, 1));
  const waiting = (recruitment.data ?? []).reduce((sum, info) => sum + Math.max(0, info.queue.length - 1), 0);

  let content: ReactNode;
  if (recruitment.isPending) {
    content = <p className={styles.empty}>{t.common.loading}</p>;
  } else if (recruitment.isError) {
    content = (
      <div className={styles.cardBody}>
        <ErrorMessage>{errorMessage(t, recruitment.error)}</ErrorMessage>
      </div>
    );
  } else if (running.length === 0) {
    content = <p className={styles.empty}>{t.training.empty}</p>;
  } else {
    content = (
      <>
        <RecruitOrderList>
          {running.map((order) => (
            <RecruitOrderLine key={order.id} order={order} />
          ))}
        </RecruitOrderList>
        {waiting > 0 && <p className={styles.waiting}>{t.training.waiting(waiting)}</p>}
      </>
    );
  }
  return (
    <Card title={t.training.title}>
      {content}
      <p className={styles.cardFooter}>
        <Link className={styles.link} to={`/v/${villageId}/recruit`}>
          {t.training.toRecruit}
        </Link>
      </p>
    </Card>
  );
}

/**
 * Shown while attacks are on their way to the village (`incomingAttacks`, counted by the server):
 * how many, with a link to the rally point's incoming list.
 */
function IncomingAttacksNotice({ village }: { village: Village }) {
  const t = useT();
  if (village.incomingAttacks === 0) {
    return null;
  }
  return (
    <p className={styles.attacks}>
      <Icon name="troops" className={styles.attacksIcon} />
      <span>
        <strong>{t.village.incomingAttacks(village.incomingAttacks)}</strong>{' '}
        <Link className={styles.attacksLink} to={{ pathname: `/v/${village.id}/rally`, hash: 'incoming' }}>
          {t.village.toIncoming}
        </Link>
      </span>
    </p>
  );
}

/** Shown while the player's beginner protection lasts; disappears on the second it ends. */
function ProtectionNotice() {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const now = useServerNow();
  const until = Date.parse(me.protectedUntil);
  if (!(until > now)) {
    return null;
  }
  return (
    <p className={styles.protection}>
      <Icon name="shield" className={styles.protectionIcon} />
      <span>
        <strong>{t.village.protection(formatDateTime(until, language))}</strong> {t.village.protectionHint}
      </span>
    </p>
  );
}

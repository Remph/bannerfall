import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router';
import { useMarket, useRefreshWhenTransportsChange } from '../../api/queries/market';
import { useVillage } from '../../api/queries/villages';
import { DevCheats } from '../../components/DevCheats';
import { SegmentedControl } from '../../components/SegmentedControl';
import { formatCoords, formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import { ExchangeCard } from './ExchangeCard';
import styles from './MarketPage.module.css';
import { OffersCard } from './OffersCard';
import { OwnOffersCard, OwnOffersList } from './OwnOffersCard';
import { SendResourcesCard } from './SendResourcesCard';
import { IncomingTransportsCard, OutgoingTransportsCard } from './TransportCards';

type Tab = 'send' | 'offers' | 'mine' | 'exchange' | 'merchants';

/**
 * /v/:villageId/market?x=&y= – the market (05-frontend §3), five tabs: send resources (the map's
 * "Send resources" button fills the target), the offers of others (accept), the village's own
 * offers (create, cancel), trading with the market itself (v1.3; hidden when the world turned it
 * off) and the merchants on their way. The merchants' count is always on top.
 */
export function MarketPage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village starts with empty forms
  return <Market key={villageId} villageId={villageId} />;
}

function Market({ villageId }: { villageId: number }) {
  const { t, language } = useI18n();
  const village = useVillage(villageId);
  const market = useMarket(villageId);
  useRefreshWhenTransportsChange(villageId, market.data, market.dataUpdatedAt, market.errorUpdatedAt);
  // the first tab also takes the map's `?x=&y=` ("Send resources") through its own search params
  const [tab, setTab] = useState<Tab>('send');

  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (market.isError) {
    return <VillageError error={market.error} onRetry={() => void market.refetch()} />;
  }
  if (village.isPending || market.isPending) {
    return <p>{t.common.loading}</p>;
  }

  const { merchants, exchange } = market.data;
  const exchangeOn = exchange.ratio > 0;
  const tabs: { value: Tab; label: string }[] = [
    { value: 'send', label: t.market.tabs.send },
    { value: 'offers', label: t.market.tabs.offers },
    { value: 'mine', label: t.market.tabs.mine },
    ...(exchangeOn ? [{ value: 'exchange' as const, label: t.market.tabs.exchange }] : []),
    { value: 'merchants', label: t.market.tabs.merchants },
  ];
  // the admin can turn the exchange off while the tab is open
  const shown: Tab = tab === 'exchange' && !exchangeOn ? 'send' : tab;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.market.title}</h1>
        <p className={styles.meta}>
          {village.data.name} {formatCoords(village.data.x, village.data.y)}
          {' · '}
          {t.market.merchants}:{' '}
          {t.market.ofFree(formatNumber(merchants.free, language), formatNumber(merchants.total, language))}
        </p>
      </header>
      {merchants.total === 0 ? (
        <>
          <p className={styles.notice}>
            <span>{t.market.noMarket}</span>
            <Link className={styles.noticeLink} to={{ pathname: `/v/${villageId}/hq`, hash: 'MARKET' }}>
              {t.market.toTownHall}
            </Link>
          </p>
          {/* a village without a market still receives goods, and catapults can raze a market
              while its merchants are away: what is on the way stays visible. So do open offers:
              the server still lets them be cancelled (no new ones without merchants) */}
          {(market.data.incoming.length > 0 || market.data.outgoing.length > 0 || market.data.offers.length > 0) && (
            <div className={styles.twoColumns}>
              {market.data.offers.length > 0 && <OwnOffersList villageId={villageId} offers={market.data.offers} />}
              {market.data.outgoing.length > 0 && (
                <OutgoingTransportsCard villageId={villageId} transports={market.data.outgoing} />
              )}
              {market.data.incoming.length > 0 && (
                <IncomingTransportsCard villageId={villageId} transports={market.data.incoming} />
              )}
            </div>
          )}
        </>
      ) : (
        <>
          <SegmentedControl label={t.market.tabs.label} options={tabs} value={shown} onChange={setTab} scroll />
          <div className={shown === 'merchants' || shown === 'mine' ? styles.twoColumns : styles.oneColumn}>
            {shown === 'send' && <SendResourcesCard village={village.data} market={market.data} />}
            {shown === 'offers' && <OffersCard village={village.data} free={merchants.free} />}
            {shown === 'mine' && <OwnOffersCard village={village.data} market={market.data} />}
            {shown === 'exchange' && <ExchangeCard village={village.data} exchange={exchange} />}
            {shown === 'merchants' && (
              <>
                <OutgoingTransportsCard villageId={villageId} transports={market.data.outgoing} />
                <IncomingTransportsCard villageId={villageId} transports={market.data.incoming} />
              </>
            )}
          </div>
        </>
      )}
      {import.meta.env.DEV && <DevCheats villageId={villageId} movements />}
    </div>
  );
}

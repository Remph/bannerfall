import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useAcceptOffer, useOffers, type Offer } from '../../api/queries/market';
import type { Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { CostList } from '../../components/CostList';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { Sheet } from '../../components/Sheet';
import { UnitCountInput } from '../../components/UnitCountInput';
import { formatDecimal, formatDuration, formatNumber, formatWhenExact } from '../../game/format';
import { maxAcceptable, merchantsNeeded, offerValue, resourceKey } from '../../game/market';
import { projectResources, type ResourceAmounts } from '../../game/resources';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import { VillageError } from '../village/VillageError';
import { OfferText } from './OfferText';
import styles from './MarketPage.module.css';

type Sort = 'near' | 'best';

function sorted(offers: readonly Offer[], sort: Sort): Offer[] {
  const copy = [...offers];
  if (sort === 'best') {
    // the most you get for one paid first; the server's order (nearest first) breaks ties
    copy.sort((a, b) => offerValue(b) - offerValue(a));
  }
  return copy;
}

/**
 * The open offers of other players (05-frontend §3), nearest first or best deal first. "Accept"
 * opens a sheet where the player picks how many units and sees what he pays, gets and when.
 */
export function OffersCard({ village, free }: { village: Village; free: number }) {
  const { t, language } = useI18n();
  const offers = useOffers(village.id);
  const [sort, setSort] = useState<Sort>('near');
  const [accepting, setAccepting] = useState<Offer | null>(null);
  const [done, setDone] = useState<string | null>(null);

  if (offers.isError) {
    return (
      <Card title={t.market.offersTitle}>
        <VillageError error={offers.error} onRetry={() => void offers.refetch()} />
      </Card>
    );
  }
  if (offers.isPending) {
    return (
      <Card title={t.market.offersTitle}>
        <p className={styles.empty}>{t.common.loading}</p>
      </Card>
    );
  }
  return (
    <Card title={t.market.offersTitle}>
      <div className={styles.body}>
        {offers.data.length > 1 && (
          <SegmentedControl
            label={t.market.sortLabel}
            value={sort}
            onChange={setSort}
            options={[
              { value: 'near', label: t.market.sortNear },
              { value: 'best', label: t.market.sortBest },
            ]}
          />
        )}
        {offers.data.length === 0 ? (
          <p className={styles.empty}>{t.market.noOffers}</p>
        ) : (
          <ul className={styles.offerList}>
            {sorted(offers.data, sort).map((offer) => (
              <li key={offer.id} className={styles.offer}>
                <div className={styles.offerMain}>
                  <OfferText {...offer} />
                  <span className={styles.offerCount}>
                    {t.market.times(formatNumber(offer.remainingCount, language))}
                  </span>
                  <span className={styles.offerMeta}>
                    {offer.village.name} · {offer.village.playerName} ·{' '}
                    {t.map.fields(formatDecimal(offer.distance ?? 0, language))} ·{' '}
                    {formatDuration(offer.travelSeconds ?? 0)}
                  </span>
                </div>
                <Button
                  type="button"
                  size="small"
                  onClick={() => {
                    setDone(null);
                    setAccepting(offer);
                  }}
                >
                  {t.market.accept}
                </Button>
              </li>
            ))}
          </ul>
        )}
        <p role="status" className={styles.sent}>
          {done}
        </p>
      </div>
      <Sheet open={accepting !== null} onClose={() => setAccepting(null)} title={t.market.acceptTitle}>
        {accepting !== null && (
          <AcceptOffer
            // a different offer starts with its own count
            key={accepting.id}
            offer={accepting}
            village={village}
            free={free}
            onDone={(text) => {
              setAccepting(null);
              setDone(text);
            }}
            onBack={() => setAccepting(null)}
          />
        )}
      </Sheet>
    </Card>
  );
}

interface AcceptOfferProps {
  offer: Offer;
  village: Village;
  free: number;
  onDone: (text: string) => void;
  onBack: () => void;
}

/** The accept sheet: how many units, what they cost, what comes back, the merchants and the arrival. */
function AcceptOffer({ offer, village, free, onDone, onBack }: AcceptOfferProps) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const accept = useAcceptOffer(village.id);
  const [count, setCount] = useState(1);
  const stock = projectResources(village, now);
  const give = resourceKey(offer.offerResource);
  const want = resourceKey(offer.wantResource);
  const max = maxAcceptable(offer.remainingCount, offer.wantAmount, stock[want], free);
  const pay: ResourceAmounts = { wood: 0, clay: 0, iron: 0, [want]: offer.wantAmount * count };
  const get: ResourceAmounts = { wood: 0, clay: 0, iron: 0, [give]: offer.offerAmount * count };
  const merchants = merchantsNeeded(offer.wantAmount * count);
  const arriveAt = now + (offer.travelSeconds ?? 0) * 1000;
  const ok = count >= 1 && count <= max;

  return (
    <div className={styles.confirm}>
      <p className={styles.confirmTitle}>
        <OfferText {...offer} />{' '}
        <span className={styles.offerMeta}>
          {offer.village.name} · {offer.village.playerName}
        </span>
      </p>
      <UnitCountInput
        value={count}
        onChange={setCount}
        max={max}
        limit={offer.remainingCount}
        label={t.market.countLabel}
        maxLabel={t.market.maxUnits}
        disabled={max === 0}
      />
      <CostList label={t.market.youPay} showLabel cost={pay} hideZero stock={stock} />
      <CostList label={t.market.youGet} showLabel cost={get} hideZero />
      <dl className={styles.facts}>
        <div className={merchants > free ? styles.short : undefined}>
          <dt>{t.market.merchantsNeeded}</dt>
          <dd>{t.market.ofFree(formatNumber(merchants, language), formatNumber(free, language))}</dd>
        </div>
        <div>
          <dt>{t.market.goodsArrive}</dt>
          <dd>{formatWhenExact(arriveAt, now, language)}</dd>
        </div>
      </dl>
      {max === 0 && <ErrorMessage>{t.market.cannotAccept}</ErrorMessage>}
      {accept.isError && <ErrorMessage>{errorMessage(t, accept.error)}</ErrorMessage>}
      <div className={styles.confirmActions}>
        <Button type="button" variant="secondary" onClick={onBack}>
          {t.rally.back}
        </Button>
        <Button
          type="button"
          disabled={!ok || accept.isPending}
          onClick={() =>
            accept.mutate(
              { offerId: offer.id, count },
              { onSuccess: () => onDone(t.market.accepted(formatWhenExact(arriveAt, now, language))) },
            )
          }
        >
          {accept.isPending ? t.market.accepting : t.market.accept}
        </Button>
      </div>
    </div>
  );
}

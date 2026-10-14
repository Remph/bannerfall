import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useCancelOffer, useCreateOffer, type Market } from '../../api/queries/market';
import type { Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { CostList } from '../../components/CostList';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { UnitCountInput } from '../../components/UnitCountInput';
import { formatNumber } from '../../game/format';
import {
  MAX_AMOUNT,
  MAX_OFFER_COUNT,
  maxOfferCount,
  merchantsForOffer,
  offerResources,
  ratioAllowed,
  resourceKey,
  wantRange,
  type OfferResource,
} from '../../game/market';
import { projectResources, type ResourceAmounts } from '../../game/resources';
import { useServerNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import styles from './MarketPage.module.css';
import { OfferText } from './OfferText';

type Offers = Market['offers'];

/** "Create an offer" and the village's own offers with a cancel button each. */
export function OwnOffersCard({ village, market }: { village: Village; market: Market }) {
  return (
    <>
      <CreateOfferCard village={village} free={market.merchants.free} />
      <OwnOffersList villageId={village.id} offers={market.offers} />
    </>
  );
}

function CreateOfferCard({ village, free }: { village: Village; free: number }) {
  const { t, language } = useI18n();
  const now = useServerNow();
  const create = useCreateOffer(village.id);
  const maxRatio = useWorld().data?.tradeMaxRatio ?? 3;
  const [give, setGive] = useState<OfferResource>('WOOD');
  const [want, setWant] = useState<OfferResource>('CLAY');
  const [offerAmount, setOfferAmount] = useState(0);
  const [wantAmount, setWantAmount] = useState(0);
  const [count, setCount] = useState(1);
  const [created, setCreated] = useState(false);

  const stock = projectResources(village, now);
  const sameResource = give === want;
  const ratioBroken = offerAmount > 0 && wantAmount > 0 && !ratioAllowed(offerAmount, wantAmount, maxRatio);
  const range = wantRange(offerAmount, maxRatio);
  const maxCount = maxOfferCount(stock[resourceKey(give)], free, offerAmount);
  const pay: ResourceAmounts = { wood: 0, clay: 0, iron: 0, [resourceKey(give)]: offerAmount * count };
  const merchants = merchantsForOffer(offerAmount, count);
  const ok = !sameResource && offerAmount > 0 && wantAmount > 0 && !ratioBroken && count >= 1 && count <= maxCount;

  function edited() {
    setCreated(false);
    create.reset();
  }

  const options = offerResources.map((value) => ({ value, label: t.resources[resourceKey(value)] }));
  return (
    <Card title={t.market.createTitle}>
      <div className={styles.body}>
        <div className={styles.field}>
          <span className={styles.fieldLabel}>{t.market.iGive}</span>
          <SegmentedControl
            label={t.market.iGive}
            options={options}
            value={give}
            onChange={(value) => {
              setGive(value);
              edited();
            }}
          />
          <UnitCountInput
            value={offerAmount}
            onChange={(value) => {
              setOfferAmount(value);
              edited();
            }}
            max={Math.min(MAX_AMOUNT, Math.floor(stock[resourceKey(give)]))}
            limit={MAX_AMOUNT}
            step={100}
            label={t.market.offerAmountLabel}
          />
        </div>
        <div className={styles.field}>
          <span className={styles.fieldLabel}>{t.market.iWant}</span>
          <SegmentedControl
            label={t.market.iWant}
            options={options}
            value={want}
            onChange={(value) => {
              setWant(value);
              edited();
            }}
          />
          <UnitCountInput
            value={wantAmount}
            onChange={(value) => {
              setWantAmount(value);
              edited();
            }}
            // what may be asked depends on what is given, so the field waits for that
            max={offerAmount > 0 ? range.max : 0}
            limit={MAX_AMOUNT}
            step={100}
            label={t.market.wantAmountLabel}
          />
          {ratioBroken && (
            <ErrorMessage>
              {t.market.ratioHint(formatNumber(range.min, language), formatNumber(range.max, language))}
            </ErrorMessage>
          )}
        </div>
        <div className={styles.field}>
          <span className={styles.fieldLabel}>{t.market.times1}</span>
          <UnitCountInput
            value={count}
            onChange={(value) => {
              setCount(value);
              edited();
            }}
            max={maxCount}
            limit={MAX_OFFER_COUNT}
            label={t.market.countLabel}
            maxLabel={t.market.maxUnits}
          />
        </div>
        {sameResource && <ErrorMessage>{t.errors.SAME_RESOURCE}</ErrorMessage>}
        {offerAmount > 0 && (
          <>
            <CostList label={t.market.youPay} showLabel cost={pay} hideZero stock={stock} />
            <dl className={styles.facts}>
              <div className={merchants > free ? styles.short : undefined}>
                <dt>{t.market.merchantsReserved}</dt>
                <dd>{t.market.ofFree(formatNumber(merchants, language), formatNumber(free, language))}</dd>
              </div>
            </dl>
          </>
        )}
        {create.isError && <ErrorMessage>{errorMessage(t, create.error)}</ErrorMessage>}
        <div className={styles.actions}>
          <Button
            type="button"
            disabled={!ok || create.isPending}
            onClick={() =>
              create.mutate(
                { offerResource: give, offerAmount, wantResource: want, wantAmount, count },
                {
                  onSuccess: () => {
                    setCreated(true);
                    setOfferAmount(0);
                    setWantAmount(0);
                    setCount(1);
                  },
                },
              )
            }
          >
            {create.isPending ? t.market.creating : t.market.create}
          </Button>
        </div>
        <p role="status" className={styles.sent}>
          {created && t.market.created}
        </p>
      </div>
    </Card>
  );
}

/** The village's own offers, cancel only: also shown without a market (MarketPage). */
export function OwnOffersList({ villageId, offers }: { villageId: number; offers: Offers }) {
  const { t, language } = useI18n();
  const cancel = useCancelOffer(villageId);
  return (
    <Card title={t.market.ownTitle}>
      <div className={styles.body}>
        {offers.length === 0 ? (
          <p className={styles.empty}>{t.market.noOwnOffers}</p>
        ) : (
          <ul className={styles.offerList}>
            {offers.map((offer) => (
              <li key={offer.id} className={styles.offer}>
                <div className={styles.offerMain}>
                  <OfferText {...offer} />
                  <span className={styles.offerCount}>
                    {t.market.timesLeft(formatNumber(offer.remainingCount, language))}
                  </span>
                </div>
                <Button
                  type="button"
                  size="small"
                  variant="danger"
                  disabled={cancel.isPending}
                  onClick={() => cancel.mutate(offer.id)}
                >
                  {t.market.cancel}
                </Button>
              </li>
            ))}
          </ul>
        )}
        <p className={styles.muted}>{t.market.cancelHint}</p>
        {cancel.isError && <ErrorMessage>{errorMessage(t, cancel.error)}</ErrorMessage>}
      </div>
    </Card>
  );
}

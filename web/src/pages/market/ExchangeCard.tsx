import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useExchange, type Exchange } from '../../api/queries/market';
import type { Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { CostList } from '../../components/CostList';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { UnitCountInput } from '../../components/UnitCountInput';
import { formatNumber, formatRatio, formatWhen } from '../../game/format';
import {
  exchangeCost,
  MAX_AMOUNT,
  maxExchange,
  offerResources,
  resourceKey,
  type OfferResource,
} from '../../game/market';
import { projectResources, type ResourceAmounts } from '../../game/resources';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import styles from './MarketPage.module.css';

/**
 * Trading with the market itself (v1.3, 02-game-rules §12.1): `ratio` of one resource for 1 of
 * another, at once and without merchants, up to the day's amount per resource.
 */
export function ExchangeCard({ village, exchange }: { village: Village; exchange: Exchange }) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const trade = useExchange(village.id);
  const [give, setGive] = useState<OfferResource>('WOOD');
  const [receive, setReceive] = useState<OfferResource>('IRON');
  const [amount, setAmount] = useState(0);
  const [done, setDone] = useState(false);

  const { ratio, dailyCap } = exchange;
  const stock = projectResources(village, now);
  const sameResource = give === receive;
  const left = Math.max(0, dailyCap - exchange.received[resourceKey(receive)]);
  const room = village.storageCapacity - Math.floor(stock[resourceKey(receive)]);
  const max = sameResource ? 0 : maxExchange(left, stock[resourceKey(give)], ratio, room);
  const cost = exchangeCost(amount, ratio);
  const pay: ResourceAmounts = { wood: 0, clay: 0, iron: 0, [resourceKey(give)]: cost };
  const ok = !sameResource && amount >= 1 && amount <= max;

  function edited() {
    setDone(false);
    trade.reset();
  }

  const options = offerResources.map((value) => ({ value, label: t.resources[resourceKey(value)] }));
  return (
    <Card title={t.market.exchangeTitle}>
      <div className={styles.body}>
        <p className={styles.muted}>{t.market.exchangeText(formatRatio(ratio, language))}</p>
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
        </div>
        <div className={styles.field}>
          <span className={styles.fieldLabel}>{t.market.iGet}</span>
          <SegmentedControl
            label={t.market.iGet}
            options={options}
            value={receive}
            onChange={(value) => {
              setReceive(value);
              edited();
            }}
          />
          <UnitCountInput
            value={amount}
            onChange={(value) => {
              setAmount(value);
              edited();
            }}
            max={max}
            limit={MAX_AMOUNT}
            step={100}
            label={t.market.exchangeAmountLabel}
          />
        </div>
        {sameResource && <ErrorMessage>{t.market.exchangeSame}</ErrorMessage>}
        {amount > 0 && <CostList label={t.market.youPay} showLabel cost={pay} hideZero stock={stock} />}
        <dl className={styles.facts}>
          <div className={left === 0 ? styles.short : undefined}>
            <dt>{t.market.exchangeLeft(t.resources[resourceKey(receive)])}</dt>
            <dd>{t.market.ofFree(formatNumber(left, language), formatNumber(dailyCap, language))}</dd>
          </div>
          <div>
            <dt>{t.market.exchangeResets}</dt>
            <dd>{formatWhen(Date.parse(exchange.resetsAt), now, language)}</dd>
          </div>
        </dl>
        {trade.isError && <ErrorMessage>{errorMessage(t, trade.error)}</ErrorMessage>}
        <div className={styles.actions}>
          <Button
            type="button"
            disabled={!ok || trade.isPending}
            onClick={() =>
              trade.mutate(
                { give, receive, amount },
                {
                  onSuccess: () => {
                    setDone(true);
                    setAmount(0);
                  },
                },
              )
            }
          >
            {trade.isPending ? t.market.exchanging : t.market.exchange}
          </Button>
        </div>
        <p role="status" className={styles.sent}>
          {done && t.market.exchanged}
        </p>
        <p className={styles.muted}>{t.market.exchangeHint}</p>
      </div>
    </Card>
  );
}

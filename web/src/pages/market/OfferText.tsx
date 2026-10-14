import { Icon } from '../../components/Icon';
import { formatNumber } from '../../game/format';
import { resourceKey } from '../../game/market';
import type { OfferResource } from '../../game/market';
import { useI18n } from '../../i18n';
import styles from './MarketPage.module.css';

interface OfferTextProps {
  offerResource: OfferResource;
  offerAmount: number;
  wantResource: OfferResource;
  wantAmount: number;
}

/** "1,000 [wood] for 800 [clay]": what an offer gives and what it wants, with the resource icons. */
export function OfferText({ offerResource, offerAmount, wantResource, wantAmount }: OfferTextProps) {
  const { t, language } = useI18n();
  const give = resourceKey(offerResource);
  const want = resourceKey(wantResource);
  return (
    <span className={styles.offerText}>
      <span className={styles.offerPart}>
        <Icon name={give} />
        <span className="sr-only">{t.resources[give]}: </span>
        {formatNumber(offerAmount, language)}
      </span>
      <span aria-hidden="true">→</span>
      <span className="sr-only">{t.market.for}</span>
      <span className={styles.offerPart}>
        <Icon name={want} />
        <span className="sr-only">{t.resources[want]}: </span>
        {formatNumber(wantAmount, language)}
      </span>
    </span>
  );
}

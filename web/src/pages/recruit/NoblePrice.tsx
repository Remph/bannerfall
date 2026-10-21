import type { Noble } from '../../api/queries/noble';
import { CostList } from '../../components/CostList';
import { Icon } from '../../components/Icon';
import { formatFactor } from '../../game/format';
import type { ResourceAmounts } from '../../game/resources';
import { useI18n } from '../../i18n';
import styles from './NoblePrice.module.css';

interface NoblePriceProps {
  noble: Noble;
  /** The village's stock: a price it can't pay right now is shown in red. */
  stock?: ResourceAmounts;
}

/**
 * Behind the noble's info button in the Academy (1.6, 02-game-rules §5.7): what the next noble and
 * the one after it cost, the rank that makes the price and the rule behind it.
 */
export function NoblePriceBody({ noble, stock }: NoblePriceProps) {
  const { t, language } = useI18n();
  const labels = t.noble;
  const flat = noble.step <= 0;
  const atMaximum = !flat && noble.factor >= noble.maxFactor;
  return (
    <div className={styles.body}>
      <p className={styles.head}>
        <Icon name="NOBLE" className={styles.headIcon} />
        <span>{labels.next}</span>
        {!flat && <span className={styles.factor}>{labels.factor(formatFactor(noble.factor, language))}</span>}
      </p>
      <CostList label={labels.next} cost={noble.cost} stock={stock} />
      {!flat && (
        <>
          <p className={styles.rank}>
            {labels.rankLine(
              noble.villages,
              noble.nobles,
              noble.freeRanks,
              noble.rank,
              formatFactor(noble.step * 100, language),
              formatFactor(noble.factor, language),
              atMaximum,
            )}
          </p>
          {!atMaximum && (
            <div className={styles.after}>
              <p className={styles.afterLabel}>{labels.after}</p>
              <CostList label={labels.after} cost={noble.costAfter} />
            </div>
          )}
        </>
      )}
      <div className={styles.notes}>
        {flat ? (
          <p>{labels.flat}</p>
        ) : (
          <>
            {atMaximum && <p>{labels.atMaximum}</p>}
            <p>
              {labels.rule(
                noble.freeRanks,
                formatFactor(noble.step * 100, language),
                formatFactor(noble.maxFactor, language),
              )}
            </p>
            <p>{labels.counted}</p>
            <p>{labels.cheaper}</p>
            <p>{labels.order}</p>
          </>
        )}
      </div>
    </div>
  );
}

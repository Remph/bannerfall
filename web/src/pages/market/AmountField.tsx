import { Icon } from '../../components/Icon';
import { UnitCountInput } from '../../components/UnitCountInput';
import { MAX_AMOUNT } from '../../game/market';
import type { ResourceType } from '../../game/resources';
import { useT } from '../../i18n';
import styles from './MarketPage.module.css';

interface AmountFieldProps {
  resource: ResourceType;
  value: number;
  onChange: (value: number) => void;
  /** What can be typed at most right now (stock, merchants); the "max" chip sets it. */
  max: number;
}

/** One row of a transport form: the resource's icon and name, then − / amount / + and "max" (in steps of 100). */
export function AmountField({ resource, value, onChange, max }: AmountFieldProps) {
  const t = useT();
  return (
    <li className={styles.amountRow}>
      <span className={styles.amountName}>
        <Icon name={resource} />
        {t.resources[resource]}
      </span>
      <span className={styles.amountInput}>
        <UnitCountInput
          value={value}
          onChange={onChange}
          max={max}
          limit={MAX_AMOUNT}
          step={100}
          label={t.market.amountLabel(t.resources[resource])}
        />
      </span>
    </li>
  );
}

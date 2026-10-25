import { formatNumber } from '../game/format';
import { countBounds, parseCount } from '../game/unitCount';
import { useI18n } from '../i18n';
import { IconButton } from './IconButton';
import styles from './UnitCountInput.module.css';

interface UnitCountInputProps {
  /** The count; 0 shows an empty field. */
  value: number;
  onChange: (value: number) => void;
  /** Most units that make sense right now (what the village can pay for): + stops there (unless `stepMax`), "max" sets it. */
  max: number;
  /** Nothing above this can be typed (e.g. the API's 30 000 per order). */
  limit: number;
  /** Names the field for screen readers (there is no visible label in a unit row). */
  label: string;
  /** Text of the chip for the formatted maximum; default "max 42" (the rally point says "all 120"). */
  maxLabel?: (amount: string) => string;
  disabled?: boolean;
  /** How much − and + change the count (default 1; the market uses 100 for amounts of resources). */
  step?: number;
  /**
   * How far + counts, if not to `max` (still never above `limit`). The farm template counts past the
   * units at home while its chip says "all 20" (D12).
   */
  stepMax?: number;
  /** Enter in the field (a valid number typed): the recruit page starts the order. */
  onEnter?: () => void;
}

/**
 * A count of units, made for thumbs (05-frontend §8): − / + steppers, a "max" chip and a field that
 * opens the numeric keyboard on phones. Typing more than `max` is allowed (the row says why it can't
 * be recruited), only `limit` is a hard stop.
 */
export function UnitCountInput(props: UnitCountInputProps) {
  const { value, onChange, max, limit, label, maxLabel, disabled = false, step = 1, stepMax, onEnter } = props;
  const { t, language } = useI18n();
  const { chip: top, plus } = countBounds(max, limit, stepMax);
  return (
    <div className={styles.group}>
      <IconButton
        icon="minus"
        label={t.unitCount.less}
        disabled={disabled || value <= 0}
        onClick={() => onChange(Math.max(0, value - step))}
      />
      <input
        className={styles.input}
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        autoComplete="off"
        placeholder="0"
        aria-label={label}
        value={value === 0 ? '' : String(value)}
        disabled={disabled}
        onChange={(event) => onChange(parseCount(event.target.value, limit))}
        onKeyDown={(event) => {
          // not while an input method is still composing the number
          if (event.key === 'Enter' && onEnter !== undefined && !event.nativeEvent.isComposing) {
            event.preventDefault();
            onEnter();
          }
        }}
      />
      <IconButton
        icon="plus"
        label={t.unitCount.more}
        disabled={disabled || value >= plus}
        onClick={() => onChange(Math.min(plus, value + step))}
      />
      <button type="button" className={styles.chip} disabled={disabled || top <= 0} onClick={() => onChange(top)}>
        {(maxLabel ?? t.unitCount.max)(formatNumber(top, language))}
      </button>
    </div>
  );
}

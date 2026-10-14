import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useDismiss, type RecruitUnit } from '../../api/queries/recruitment';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import { UnitCountInput } from '../../components/UnitCountInput';
import { formatDuration, formatNumber } from '../../game/format';
import { MAX_RECRUIT_COUNT } from '../../game/recruitment';
import { useI18n } from '../../i18n';
import styles from './RecruitPage.module.css';

interface DismissUnitProps {
  villageId: number;
  unit: RecruitUnit;
  /** How many of them stand in the home stack (what can be dismissed). */
  atHome: number;
  /** The building's dismiss queue is full. */
  queueFull: boolean;
}

/**
 * "Dismiss" next to a unit with units at home (02-game-rules §5.2a): a sheet with the count, the
 * time it takes and what happens, then one confirmation. Dismissing can't be undone for the units
 * already gone, so it never happens on a single tap.
 */
export function DismissUnit({ villageId, unit, atHome, queueFull }: DismissUnitProps) {
  const { t, language } = useI18n();
  const dismiss = useDismiss(villageId);
  const [open, setOpen] = useState(false);
  const [count, setCount] = useState(0);
  const name = t.units[unit.unit];

  if (atHome < 1) {
    return null;
  }

  function close() {
    setOpen(false);
    setCount(0);
    dismiss.reset();
  }

  return (
    <>
      <Button
        type="button"
        variant="secondary"
        size="small"
        disabled={queueFull}
        aria-label={t.dismiss.buttonLabel(name)}
        title={queueFull ? t.dismiss.queueFull : undefined}
        onClick={() => setOpen(true)}
      >
        {t.dismiss.button}
      </Button>
      <Sheet open={open} onClose={close} title={t.dismiss.title(name)}>
        <div className={styles.confirm}>
          <p>{t.dismiss.explain}</p>
          <p>
            <strong>{t.dismiss.atHome(formatNumber(atHome, language))}</strong>
            {' · '}
            {t.dismiss.perUnit(formatDuration(unit.dismissSecondsPerUnit))}
          </p>
          <UnitCountInput
            value={count}
            onChange={setCount}
            max={atHome}
            limit={Math.min(atHome, MAX_RECRUIT_COUNT)}
            label={t.dismiss.countLabel(name)}
          />
          {count >= 1 && (
            <p>{t.dismiss.total(formatNumber(count, language), formatDuration(unit.dismissSecondsPerUnit * count))}</p>
          )}
          {dismiss.isError && <ErrorMessage>{errorMessage(t, dismiss.error)}</ErrorMessage>}
          <div className={styles.confirmActions}>
            <Button type="button" variant="secondary" onClick={close}>
              {t.dismiss.keep}
            </Button>
            <Button
              type="button"
              disabled={count < 1 || count > atHome || dismiss.isPending}
              onClick={() => dismiss.mutate({ unit: unit.unit, count }, { onSuccess: close })}
            >
              {t.dismiss.confirm}
            </Button>
          </div>
        </div>
      </Sheet>
    </>
  );
}

import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useCopyFarmList } from '../../api/queries/farm';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import { formatCoords, formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './Farm.module.css';

interface CopyListSheetProps {
  open: boolean;
  onClose: () => void;
  /** The village whose list is copied. */
  villageId: number;
}

/**
 * "Copy entries to another village" (1.6.1): pick one of the player's other villages, and the
 * entries its list lacks are added (same template or own units). The answer says what was copied
 * and what was passed over; the first village's list stays as it is.
 */
export function CopyListSheet({ open, onClose, villageId }: CopyListSheetProps) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const copy = useCopyFarmList(villageId);
  const others = me.villages.filter((village) => village.id !== villageId);
  const result = copy.data;
  const copiedTo = others.find((village) => village.id === copy.variables);
  const close = () => {
    copy.reset();
    onClose();
  };
  return (
    <Sheet open={open} onClose={close} title={t.farm.copyTitle}>
      <div className={styles.copySheet}>
        <p className={styles.hint}>{t.farm.copyHint}</p>
        {result !== undefined && copiedTo !== undefined && (
          <p role="status">{t.farm.copyResult(copiedTo.name, result)}</p>
        )}
        {copy.isError && <ErrorMessage>{errorMessage(t, copy.error)}</ErrorMessage>}
        <ul className={styles.copyList}>
          {others.map((village) => (
            <li key={village.id}>
              <button
                type="button"
                className={styles.copyRow}
                disabled={copy.isPending}
                onClick={() => copy.mutate(village.id)}
              >
                <span className={styles.copyName}>{village.name}</span>
                <span className={styles.hint}>
                  {formatCoords(village.x, village.y)} · {t.village.points(formatNumber(village.points, language))}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </Sheet>
  );
}

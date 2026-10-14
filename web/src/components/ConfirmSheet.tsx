import { errorMessage } from '../api/errors';
import { useI18n } from '../i18n';
import { Button } from './Button';
import styles from './ConfirmSheet.module.css';
import { ErrorMessage } from './ErrorMessage';
import { Sheet } from './Sheet';

interface ConfirmSheetProps {
  open: boolean;
  title: string;
  text: string;
  confirmLabel: string;
  /** The confirm button looks like a warning (something that cannot be undone). */
  danger?: boolean;
  pending: boolean;
  /** The error of the last try; shown above the buttons. */
  error: unknown;
  onConfirm: () => void;
  onClose: () => void;
}

/** "Are you sure?" in a sheet: a question, Cancel and the action. Closing it is up to the caller. */
export function ConfirmSheet({
  open,
  title,
  text,
  confirmLabel,
  danger = false,
  pending,
  error,
  onConfirm,
  onClose,
}: ConfirmSheetProps) {
  const { t } = useI18n();
  return (
    <Sheet open={open} onClose={onClose} title={title}>
      <div className={styles.confirm}>
        <p>{text}</p>
        {error !== null && error !== undefined && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
        <div className={styles.buttons}>
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button type="button" variant={danger ? 'danger' : 'primary'} disabled={pending} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}

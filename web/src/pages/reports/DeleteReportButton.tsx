import { useEffect, useState } from 'react';
import { useDeleteReport } from '../../api/queries/reports';
import { Icon } from '../../components/Icon';
import { useT } from '../../i18n';
import styles from './ReportsPage.module.css';

/** How long the button waits for the second tap. */
const ARMED_MS = 4000;

interface DeleteReportButtonProps {
  reportId: number;
  /** The report's title, for the button's accessible name in the list. */
  title: string;
  /** Only the icon (list lines); otherwise icon and "Delete". */
  compact?: boolean;
  /** The second tap: the delete starts now. */
  onStart?: () => void;
  onDeleted?: () => void;
  onError?: (error: Error) => void;
}

/**
 * Deletes a report with two taps: the first turns the button into a red "Really delete?", the
 * second deletes. Without a second tap it turns back after a few seconds. No sheet: cleaning up a
 * list of reports shouldn't need a dialog per report, and a stray tap alone deletes nothing.
 */
export function DeleteReportButton({
  reportId,
  title,
  compact = false,
  onStart,
  onDeleted,
  onError,
}: DeleteReportButtonProps) {
  const t = useT();
  const remove = useDeleteReport();
  const [armed, setArmed] = useState(false);

  useEffect(() => {
    if (!armed) {
      return;
    }
    const timer = setTimeout(() => setArmed(false), ARMED_MS);
    return () => clearTimeout(timer);
  }, [armed]);

  function onClick() {
    if (!armed) {
      setArmed(true);
      return;
    }
    setArmed(false);
    onStart?.();
    remove.mutate(reportId, { onSuccess: onDeleted, onError });
  }

  const classes = [styles.delete, compact && styles.deleteCompact, armed && styles.deleteArmed].filter(Boolean);
  return (
    <button
      type="button"
      className={classes.join(' ')}
      onClick={onClick}
      // a mouse or keyboard user who moves on doesn't leave it armed
      onBlur={() => setArmed(false)}
      disabled={remove.isPending}
      aria-label={armed ? undefined : t.reports.deleteReport(title)}
      title={armed ? undefined : t.reports.delete}
    >
      {armed ? (
        t.reports.reallyDelete
      ) : (
        <>
          <Icon name="trash" />
          {!compact && <span>{t.reports.delete}</span>}
        </>
      )}
    </button>
  );
}

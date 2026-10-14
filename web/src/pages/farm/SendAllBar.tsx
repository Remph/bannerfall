import { useEffect, useState } from 'react';
import { errorMessage } from '../../api/errors';
import type { FarmTarget } from '../../api/queries/farm';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import type { SendAllResult } from '../../game/farm';
import { formatCoords } from '../../game/format';
import { useT } from '../../i18n';
import type { Dictionary } from '../../i18n/en';
import styles from './Farm.module.css';

/** How long "Send all" waits for the second tap (like a row's "Send anyway?"). */
const ARMED_MS = 4000;

interface SendAllBarProps {
  targets: FarmTarget[];
  /** Whether "send all" leaves players' villages out of this list now (the sender is protected). */
  leavesPlayersOut: boolean;
  pending: boolean;
  result: SendAllResult | undefined;
  error: unknown;
  onSend: () => void;
}

/**
 * "Send all" (M39, 02-game-rules §18) above the list: two taps, since one sends up to 50 attacks –
 * the first turns the button into "Really send all?", without a second it turns back after a few
 * seconds. After the run: how many attacks went out and why it ended; the rows say the rest.
 */
export function SendAllBar({ targets, leavesPlayersOut, pending, result, error, onSend }: SendAllBarProps) {
  const t = useT();
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
    onSend();
  }

  return (
    <div className={styles.sendAll}>
      <div className={styles.actions}>
        <Button type="button" disabled={pending} onClick={onClick} onBlur={() => setArmed(false)}>
          {armed ? t.farm.sendAllArmed : t.farm.sendAll}
        </Button>
      </div>
      <p className={styles.meta}>{t.farm.sendAllHint}</p>
      {leavesPlayersOut && <p className={styles.notice}>{t.farm.sendAllLeavesPlayersOut}</p>}
      {result !== undefined && (
        <p className={styles.sent} role="status">
          {summary(t, result, targets)}
        </p>
      )}
      {error != null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
    </div>
  );
}

function summary(t: Dictionary, result: SendAllResult, targets: FarmTarget[]): string {
  const parts = [`${t.farm.sendAllSent(result.sent.length)}.`];
  if (result.skipped.length > 0) {
    parts.push(t.farm.sendAllSkipped(result.skipped.length));
  }
  if (result.stop === 'NOT_ENOUGH_UNITS') {
    // the entry may be gone from the refreshed list (removed meanwhile): then without its name
    const stopped = targets.find((target) => target.id === result.stoppedAt)?.target;
    parts.push(
      t.farm.sendAllStopped(
        stopped === undefined
          ? null
          : `${stopped.name ?? t.map.barbarianVillage} ${formatCoords(stopped.x, stopped.y)}`,
      ),
    );
  } else {
    parts.push(t.farm.sendAllDone);
  }
  return parts.join(' ');
}

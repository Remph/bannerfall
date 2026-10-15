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
  /**
   * The targets with a banner chance (`bannerChance`: no "Banner chance used" pill, a template that
   * can win a roll) for the second button (1.2.3); undefined hides it (a world without banner drops).
   */
  bannerChances: number | undefined;
  pending: boolean;
  result: SendAllResult | undefined;
  error: unknown;
  onSend: (bannerChanceOnly: boolean) => void;
}

/**
 * "Send all" (M39, 02-game-rules §18) above the list, and next to it "Send banner chances", the same
 * run over only the targets without "Banner chance used" (1.2.3). Both take two taps, since one
 * sends up to 50 attacks. After the run: how many attacks went out and why it ended; the rows say
 * the rest.
 */
export function SendAllBar({
  targets,
  leavesPlayersOut,
  bannerChances,
  pending,
  result,
  error,
  onSend,
}: SendAllBarProps) {
  const t = useT();
  return (
    <div className={styles.sendAll}>
      <div className={styles.actions}>
        <TwoTapButton
          label={t.farm.sendAll}
          armedLabel={t.farm.sendAllArmed}
          disabled={pending}
          onSend={() => onSend(false)}
        />
        {bannerChances !== undefined && (
          <TwoTapButton
            variant="secondary"
            label={t.farm.sendBannerChances(bannerChances)}
            armedLabel={t.farm.sendBannerChancesArmed}
            disabled={pending || bannerChances === 0}
            onSend={() => onSend(true)}
          />
        )}
      </div>
      <p className={styles.meta}>{t.farm.sendAllHint}</p>
      {bannerChances !== undefined && <p className={styles.meta}>{t.farm.sendBannerChancesHint}</p>}
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

interface TwoTapButtonProps {
  label: string;
  armedLabel: string;
  variant?: 'secondary';
  disabled: boolean;
  onSend: () => void;
}

/** The first tap turns the label into the question, without a second it turns back after a few seconds. */
function TwoTapButton({ label, armedLabel, variant, disabled, onSend }: TwoTapButtonProps) {
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
    <Button type="button" variant={variant} disabled={disabled} onClick={onClick} onBlur={() => setArmed(false)}>
      {armed ? armedLabel : label}
    </Button>
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

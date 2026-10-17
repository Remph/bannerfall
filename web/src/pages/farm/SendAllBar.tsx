import { useEffect, useId, useState } from 'react';
import { errorMessage } from '../../api/errors';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import type { SendAllResult } from '../../game/farm';
import { useT } from '../../i18n';
import type { Dictionary } from '../../i18n/en';
import styles from './Farm.module.css';

/** How long "Send all" waits for the second tap (like a row's "Send anyway?"). */
const ARMED_MS = 4000;

interface SendAllBarProps {
  /** Whether "send all" leaves players' villages out of this list now (the sender is protected). */
  leavesPlayersOut: boolean;
  /**
   * The targets with a banner chance (`bannerChance`: no "Banner from …" pill, a template that can
   * win a roll) for the second button (1.2.3); undefined hides it (a world without banner drops).
   * At 0 the button gives way to a short line saying none is open (1.3).
   */
  bannerChances: number | undefined;
  pending: boolean;
  result: SendAllResult | undefined;
  error: unknown;
  onSend: (bannerChanceOnly: boolean) => void;
}

/**
 * "Send all" (M39, 02-game-rules §18) above the list, and next to it "Send banner chances", the same
 * run over only the targets without "Banner from …" (1.2.3). Both take two taps, since one sends up
 * to 50 attacks. After the run a chip next to the buttons says how many attacks went out, how many
 * were skipped and how many of those for lack of troops (the run goes on past them since 1.3); the
 * rows say which. One hint line below (1.3); the banner chance rules open behind its (i) button.
 */
export function SendAllBar({ leavesPlayersOut, bannerChances, pending, result, error, onSend }: SendAllBarProps) {
  const t = useT();
  const rulesId = useId();
  const [rulesOpen, setRulesOpen] = useState(false);
  return (
    <div className={styles.sendAll}>
      <div className={styles.actions}>
        <TwoTapButton
          label={t.farm.sendAll}
          armedLabel={t.farm.sendAllArmed}
          disabled={pending}
          onSend={() => onSend(false)}
        />
        {bannerChances !== undefined &&
          (bannerChances > 0 ? (
            <TwoTapButton
              variant="secondary"
              label={t.farm.sendBannerChances(bannerChances)}
              armedLabel={t.farm.sendBannerChancesArmed}
              disabled={pending}
              onSend={() => onSend(true)}
            />
          ) : (
            <span className={styles.noChances}>{t.farm.noBannerChances}</span>
          ))}
        {result !== undefined && (
          // green with a check only when attacks went out; a run that sent none stays calm
          <span className={result.sent.length > 0 ? styles.sentChip : styles.noneSentChip} role="status">
            {result.sent.length > 0 && <Icon name="check" />}
            {summary(t, result)}
          </span>
        )}
      </div>
      <div className={styles.hintLine}>
        <p className={styles.meta}>{t.farm.sendAllHint}</p>
        {bannerChances !== undefined && (
          <IconButton
            icon="info"
            label={t.farm.bannerRulesLabel}
            aria-expanded={rulesOpen}
            aria-controls={rulesId}
            onClick={() => setRulesOpen(!rulesOpen)}
          />
        )}
      </div>
      {bannerChances !== undefined && (
        <p id={rulesId} className={styles.meta} hidden={!rulesOpen}>
          {t.farm.sendBannerChancesHint}
        </p>
      )}
      {leavesPlayersOut && <p className={styles.notice}>{t.farm.sendAllLeavesPlayersOut}</p>}
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

/**
 * The first tap turns the label into the question, without a second it turns back after a few
 * seconds. Armed it is the red attack button (1.3), with a thin bar running down over those seconds.
 */
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
    <Button
      type="button"
      variant={armed ? 'attack' : variant}
      className={armed ? styles.armed : undefined}
      disabled={disabled}
      onClick={onClick}
      onBlur={() => setArmed(false)}
    >
      {armed ? armedLabel : label}
    </Button>
  );
}

function summary(t: Dictionary, result: SendAllResult): string {
  const parts = [`${t.farm.sendAllSent(result.sent.length)}.`];
  const short = result.skipped.filter((skip) => skip.reason === 'NOT_ENOUGH_UNITS').length;
  if (result.skipped.length > short) {
    parts.push(t.farm.sendAllSkipped(result.skipped.length - short));
  }
  if (short > 0) {
    parts.push(t.farm.sendAllShort(short));
  }
  return parts.join(' ');
}

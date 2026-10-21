import { useEffect, useId, useState } from 'react';
import { errorMessage } from '../../api/errors';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { SwitchRow } from '../../components/SwitchRow';
import type { RunPreview, SendAllResult } from '../../game/farm';
import { formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
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
  /**
   * "Notify me when this run is back" (1.6): one push when the run's last attack is home;
   * undefined hides the switch (no push device).
   */
  notifyRun?: { on: boolean; onChange: (on: boolean) => void };
  onSend: (bannerChanceOnly: boolean) => void;
  /** What each run would do now (1.6): shown on its first tap, in place of the hint. */
  preview: { all: RunPreview; bannerChances: RunPreview };
}

/**
 * "Send all" (M39, 02-game-rules §18) above the list, and next to it "Send banner chances", the same
 * run over only the targets without "Banner from …" (1.2.3). Both take two taps, since one sends up
 * to 50 attacks. After the run a chip next to the buttons says how many attacks went out, how many
 * were skipped and how many of those for lack of troops (the run goes on past them since 1.3); the
 * rows say which. One hint line below (1.3); the banner chance rules open behind its (i) button.
 * On the first tap that line says what the run would do now: how many attacks and the most they
 * can bring home together (1.6).
 */
export function SendAllBar({
  leavesPlayersOut,
  bannerChances,
  pending,
  result,
  error,
  notifyRun,
  onSend,
  preview,
}: SendAllBarProps) {
  const { t, language } = useI18n();
  // the run waiting for its second tap, if any
  const [armedRun, setArmedRun] = useState<keyof SendAllBarProps['preview']>();
  const shown = armedRun === undefined ? undefined : preview[armedRun];

  useEffect(() => {
    if (armedRun === undefined) {
      return;
    }
    const timer = setTimeout(() => setArmedRun(undefined), ARMED_MS);
    return () => clearTimeout(timer);
  }, [armedRun]);
  const rulesId = useId();
  const [rulesOpen, setRulesOpen] = useState(false);
  return (
    <div className={styles.sendAll}>
      <div className={styles.actions}>
        <TwoTapButton
          armed={armedRun === 'all'}
          onArm={(armed) => setArmedRun(armed ? 'all' : undefined)}
          label={t.farm.sendAll}
          armedLabel={t.farm.sendAllArmed}
          disabled={pending}
          onSend={() => onSend(false)}
        />
        {bannerChances !== undefined &&
          (bannerChances > 0 ? (
            <TwoTapButton
              armed={armedRun === 'bannerChances'}
              onArm={(armed) => setArmedRun(armed ? 'bannerChances' : undefined)}
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
        {shown === undefined ? (
          <p className={styles.meta}>{t.farm.sendAllHint}</p>
        ) : (
          <p className={styles.runPreview} role="status">
            <Icon name="sack" /> {t.farm.runPreview(shown.attacks, formatNumber(shown.haul, language))}
          </p>
        )}
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
      {notifyRun !== undefined && (
        <SwitchRow label={t.farm.notifyRun} checked={notifyRun.on} onChange={notifyRun.onChange} />
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
  /** Waiting for the second tap (the bar holds it, so the line below can say what the run does). */
  armed: boolean;
  onArm: (armed: boolean) => void;
  onSend: () => void;
}

/**
 * The first tap turns the label into the question, without a second it turns back after a few
 * seconds (the bar's timer). Armed it is the red attack button (1.3), with a thin bar running down
 * over those seconds.
 */
function TwoTapButton({ label, armedLabel, variant, disabled, armed, onArm, onSend }: TwoTapButtonProps) {
  function onClick() {
    if (!armed) {
      onArm(true);
      return;
    }
    onArm(false);
    onSend();
  }

  return (
    <Button
      type="button"
      variant={armed ? 'attack' : variant}
      className={armed ? styles.armed : undefined}
      disabled={disabled}
      onClick={onClick}
      onBlur={() => armed && onArm(false)}
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

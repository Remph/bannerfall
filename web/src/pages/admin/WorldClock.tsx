import { errorMessage } from '../../api/errors';
import { useSetWorldClock, useWorldClock } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { ActionsStayOn } from '../../layout/ActionsStayOn';
import { useI18n } from '../../i18n';
import styles from './AdminPage.module.css';

const SPEEDS = [1, 2, 4] as const;

/**
 * The host's speed control of a solo world (2.1, 12-solo-and-allies §3): pause and play, and the fast
 * forward ×2 or ×4. It works at once, not through the World tab's save bar. Every step of the fast
 * forward reloads the players' screens, so the counters jump by 10 or 30 seconds every 10 seconds.
 */
export function WorldClock() {
  const { t } = useI18n();
  const labels = t.admin.worldClock;
  const clock = useWorldClock();
  const change = useSetWorldClock();
  const state = clock.data;
  if (state === undefined || !state.soloMode) {
    return null;
  }
  const status = state.paused ? labels.paused : state.asleepSince !== null ? labels.asleep : labels.running;
  return (
    <ActionsStayOn>
      <div className={styles.stack}>
        <p>
          <strong>{status}</strong>
        </p>
        {change.error !== null && <ErrorMessage>{errorMessage(t, change.error)}</ErrorMessage>}
        <div className={styles.actions}>
          <Button
            type="button"
            variant={state.paused ? 'primary' : 'secondary'}
            disabled={change.isPending}
            onClick={() => change.mutate({ paused: !state.paused })}
          >
            {state.paused ? labels.play : labels.pause}
          </Button>
        </div>
        <SegmentedControl
          track
          label={labels.speed}
          options={SPEEDS.map((speed) => ({ value: String(speed), label: labels.speeds[speed] }))}
          value={String(state.speed)}
          onChange={(value) => change.mutate({ speed: Number(value) })}
        />
        <p className={styles.hint}>{labels.hint}</p>
      </div>
    </ActionsStayOn>
  );
}

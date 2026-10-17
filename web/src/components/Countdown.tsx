import { formatDuration } from '../game/format';
import { secondsUntil, useGameNow } from '../game/serverTime';
import { useT } from '../i18n';
import { useWorldEnded } from '../layout/worldEnd';

/**
 * Time left until `until` (an ISO time from the API) as h:mm:ss, ticking with the server clock
 * (05-frontend §5). Stops at 0:00:00; reloading the data afterwards is done elsewhere
 * (useRefreshWhenBuildsEnd), so a countdown that isn't on screen still leads to a refresh.
 */
export function Countdown({ until, className }: { until: string; className?: string }) {
  const now = useGameNow();
  const t = useT();
  const ended = useWorldEnded();
  const seconds = secondsUntil(Date.parse(until), now);
  if (ended) {
    // an ended world is frozen (v1.3): nothing finishes any more
    return <span className={className}>{t.worldEnd.paused}</span>;
  }
  return (
    <time dateTime={`PT${seconds}S`} className={className}>
      {formatDuration(seconds)}
    </time>
  );
}

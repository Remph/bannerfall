import { formatDuration } from '../game/format';
import { secondsUntil, useServerNow } from '../game/serverTime';

/**
 * Time left until `until` (an ISO time from the API) as h:mm:ss, ticking with the server clock
 * (05-frontend §5). Stops at 0:00:00; reloading the data afterwards is done elsewhere
 * (useRefreshWhenBuildsEnd), so a countdown that isn't on screen still leads to a refresh.
 */
export function Countdown({ until, className }: { until: string; className?: string }) {
  const now = useServerNow();
  const seconds = secondsUntil(Date.parse(until), now);
  return (
    <time dateTime={`PT${seconds}S`} className={className}>
      {formatDuration(seconds)}
    </time>
  );
}

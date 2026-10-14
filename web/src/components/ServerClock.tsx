import { formatClock } from '../game/format';
import { useServerNow } from '../game/serverTime';
import { useI18n } from '../i18n';

/** The server's current time, e.g. 21:14:07 – handy for timing attacks later. */
export function ServerClock() {
  const { language } = useI18n();
  const now = useServerNow();
  return <time dateTime={new Date(now).toISOString()}>{formatClock(now, language)}</time>;
}

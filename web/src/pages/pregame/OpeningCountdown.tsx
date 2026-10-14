import { Countdown } from '../../components/Countdown';
import { secondsUntil, useServerNow } from '../../game/serverTime';
import { useT } from '../../i18n';
import { splitCountdown } from './openingTime';

/** "2 days 4:46:40": the time until the world opens, ticking with the server clock. */
export function OpeningCountdown({ opensAt }: { opensAt: string }) {
  const t = useT();
  const now = useServerNow();
  const { days } = splitCountdown(secondsUntil(Date.parse(opensAt), now));
  if (days === 0) {
    return <Countdown until={opensAt} />;
  }
  // more than a day: the days in words, the rest as the usual clock (it starts from the next full day)
  const restUntil = new Date(Date.parse(opensAt) - days * 86_400_000).toISOString();
  return (
    <>
      {t.pregame.days(days)} <Countdown until={restUntil} />
    </>
  );
}

import { useEffect, useRef } from 'react';
import { useTutorial } from '../api/queries/tutorial';
import { useShowToast } from '../components/toasts';
import { resourceText } from '../game/tutorial';
import { useI18n } from '../i18n';

/**
 * Keeps the tutorial (1.7) up to date on every game screen: it looks again whenever the current
 * village's data changed (an order done or placed, units back), and tells about a refund the server
 * just paid with a toast. The query only asks the server for a card while the player has one on.
 *
 * @param villageUpdatedAt when the current village's data last arrived (0 while there is none)
 */
export function useTutorialSync(villageUpdatedAt: number): void {
  const { t, language } = useI18n();
  const showToast = useShowToast();
  const tutorial = useTutorial();
  const { refetch } = tutorial;
  const active = tutorial.data?.active === true;

  useEffect(() => {
    if (active && villageUpdatedAt > 0) {
      // cancelRefetch: false – the look that is running already is as good as a new one
      void refetch({ cancelRefetch: false });
    }
  }, [active, villageUpdatedAt, refetch]);

  const toasted = useRef(0);
  const refunds = tutorial.data?.refunds;
  const answeredAt = tutorial.dataUpdatedAt;
  useEffect(() => {
    if (refunds === undefined || refunds.length === 0 || toasted.current === answeredAt) {
      return;
    }
    toasted.current = answeredAt;
    for (const refund of refunds) {
      showToast({
        tone: 'success',
        text: t.tutorial.refundToast(resourceText(refund.amount, t, language)),
        to: { pathname: `/v/${refund.villageId}` },
      });
    }
  }, [refunds, answeredAt, showToast, t, language]);
}

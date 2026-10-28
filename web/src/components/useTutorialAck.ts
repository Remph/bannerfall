import { useEffect } from 'react';
import { useTutorial, useTutorialDone, type TutorialStepKey } from '../api/queries/tutorial';

/**
 * The player did what the server cannot see (opened a barbarian village on the map, the
 * simulator): tells the server once, while that step is the current one.
 */
export function useTutorialAck(step: TutorialStepKey, happened = true): void {
  const current = useTutorial().data?.current;
  const { mutate } = useTutorialDone();
  const due = happened && current?.key === step;
  useEffect(() => {
    if (due) {
      mutate(step);
    }
  }, [due, mutate, step]);
}

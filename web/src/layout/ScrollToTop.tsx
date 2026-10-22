import { useLayoutEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router';
import { opensAtTop } from './opensAtTop';

/**
 * Scrolls to the top when a link with `OPENS_AT_TOP` was followed, also when it points at the
 * page that is open (tapping the lit entry again). Back and forward leave the position to the
 * browser. Put it once in the game frame.
 */
export function ScrollToTop() {
  const { key, state } = useLocation();
  const navigationType = useNavigationType();
  const wanted = opensAtTop(state);
  useLayoutEffect(() => {
    if (wanted && navigationType !== 'POP') {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    }
  }, [key, wanted, navigationType]);
  return null;
}

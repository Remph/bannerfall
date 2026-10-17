import { Button } from '../components/Button';
import { ErrorMessage } from '../components/ErrorMessage';
import { FullScreenMessage } from '../components/FullScreenMessage';
import { useT } from '../i18n';
import { ActionsStayOn } from './ActionsStayOn';

/** While the first page's code loads (a deep link to a lazy page, 1.3). */
export function PageLoading() {
  const t = useT();
  return <FullScreenMessage>{t.common.loading}</FullScreenMessage>;
}

/**
 * When a page can't be shown at all: its code didn't load (the reload after an update didn't help)
 * or it crashed. Reloading is the only way out, so that is the button.
 */
export function RouteError() {
  const t = useT();
  return (
    <FullScreenMessage>
      <ErrorMessage>{t.errors.UNKNOWN}</ErrorMessage>
      {/* reloading changes nothing in the world, so it stays on in an ended one (v1.3) */}
      <ActionsStayOn>
        <Button type="button" onClick={() => window.location.reload()}>
          {t.common.retry}
        </Button>
      </ActionsStayOn>
    </FullScreenMessage>
  );
}

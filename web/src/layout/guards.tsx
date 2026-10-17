import { Navigate, Outlet } from 'react-router';
import { errorMessage } from '../api/errors';
import { useMe } from '../api/queries/auth';
import { Button } from '../components/Button';
import { ErrorMessage } from '../components/ErrorMessage';
import { FullScreenMessage } from '../components/FullScreenMessage';
import { useT } from '../i18n';
import { ActionsStayOn } from './ActionsStayOn';
import { AppLayout } from './AppLayout';
import { AuthLayout } from './AuthLayout';

/**
 * Route guards. They wrap other routes in router.tsx and either render them (<Outlet />) or
 * redirect. Both wait until we know whether someone is logged in.
 */
export function RequireLogin() {
  const me = useMe();
  if (me.data === undefined) {
    return <MeLoading />;
  }
  return me.data === null ? <Navigate to="/login" replace /> : <Outlet />;
}

/** The admin pages: for an ADMIN account only (the server checks it too, 04-api *Admin*). */
export function RequireAdmin() {
  const me = useMe();
  return me.data?.role === 'ADMIN' ? <Outlet /> : <Navigate to="/" replace />;
}

/** Login and register pages: already logged-in users go straight into the game. */
export function RequireGuest() {
  const me = useMe();
  if (me.data === undefined) {
    return <MeLoading />;
  }
  return me.data === null ? <Outlet /> : <Navigate to="/" replace />;
}

/**
 * Pages for everybody (the privacy page): guests see them in the login frame, players in the game's
 * frame, so nobody is sent away to log in or out first.
 */
export function GuestOrPlayerLayout() {
  const me = useMe();
  if (me.data === undefined) {
    return <MeLoading />;
  }
  return me.data === null ? <AuthLayout /> : <AppLayout />;
}

function MeLoading() {
  const t = useT();
  const me = useMe();
  if (me.isError) {
    return (
      <FullScreenMessage>
        <ErrorMessage>{errorMessage(t, me.error)}</ErrorMessage>
        {/* reading again changes nothing, so it stays on in an ended world (v1.3) */}
        <ActionsStayOn>
          <Button type="button" onClick={() => void me.refetch()}>
            {t.common.retry}
          </Button>
        </ActionsStayOn>
      </FullScreenMessage>
    );
  }
  return <FullScreenMessage>{t.common.loading}</FullScreenMessage>;
}

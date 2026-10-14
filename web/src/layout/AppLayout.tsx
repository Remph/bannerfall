import { useEffect, useId, useState } from 'react';
import { Link, Navigate, Outlet, useLocation, useMatch } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { useRefreshWhenBuildsEnd } from '../api/queries/buildings';
import { useVillage } from '../api/queries/villages';
import { useWorld } from '../api/queries/world';
import { FullScreenMessage } from '../components/FullScreenMessage';
import { Icon } from '../components/Icon';
import { ResourceBar } from '../components/ResourceBar';
import { ServerClock } from '../components/ServerClock';
import { Sheet } from '../components/Sheet';
import { ToastProvider } from '../components/ToastProvider';
import { VersionTag } from '../components/VersionTag';
import { useT } from '../i18n';
import { ChatDock } from '../pages/chat/ChatDock';
import { ChatPanel } from '../pages/chat/ChatPanel';
import { PregameFrame } from '../pages/pregame/PregameFrame';
import { WorldInfo } from '../pages/pregame/WorldInfo';
import { RealtimeProvider } from '../realtime/RealtimeProvider';
import styles from './AppLayout.module.css';
import { ChangelogBanner } from './ChangelogBanner';
import { rememberVillage } from './currentVillage';
import { IncomingAttacksBadge } from './IncomingAttacksBadge';
import { MenuSheet, type MenuKind } from './MenuSheet';
import { DesktopNav, TabBar } from './navigation';
import { loadResourceDetails, saveResourceDetails } from './resourceDetails';
import { useCurrentVillageId } from './useCurrentVillageId';
import { DESKTOP_QUERY, useMediaQuery } from './useMediaQuery';
import { VillageSwitcher } from './VillageSwitcher';
import { isOpenWithoutVillage } from './withoutVillage';

/**
 * Frame of all game screens (05-frontend §3, §8). Phone: green strip on top (village switcher +
 * resources) and a tab bar at the bottom. Desktop (≥ 1024 px): one top bar with brand, switcher,
 * resources and settings, a tab row below, a footer with the server time. The CSS does the switch.
 * Around it the live connection (toasts, notifications, chat; M9b): it opens with the game screens
 * after login and closes when they go away on logout.
 */
export function AppLayout() {
  return (
    <ToastProvider>
      <RealtimeProvider>
        <PhaseFrame />
      </RealtimeProvider>
    </ToastProvider>
  );
}

/** Before the world opens (M14g) there is no village to frame: the slim pre-game frame, else the game's. */
function PhaseFrame() {
  const t = useT();
  const world = useWorld();
  if (world.data?.phase === 'PREGAME') {
    return <PregameFrame />;
  }
  // Without the answer the game frame would flash the village screens for a moment in the pre-game.
  return world.isPending ? <FullScreenMessage>{t.common.loading}</FullScreenMessage> : <GameFrame />;
}

function GameFrame() {
  const t = useT();
  const me = useCurrentAccount();
  // The chat: a floating panel on desktop, a full-screen sheet from "More" on phones.
  const desktop = useMediaQuery(DESKTOP_QUERY);
  // The map fills the whole space between the bars, and the page itself doesn't scroll. Phones and
  // tablets: the map alone (the tab bar in the column); desktop: the whole window width, detailed map
  // and side column (M11b, for a bigger map).
  const onMap = useMatch('/v/:villageId/map') !== null;
  const fullScreen = onMap && !desktop;
  const wide = onMap && desktop;
  // Without a village (all conquered, M11) only "/" (the respawn screen) and the pages that aren't about one village are open.
  const allowedWithoutVillage = isOpenWithoutVillage(useLocation().pathname);
  const villageId = useCurrentVillageId();
  // Same query (and cache entry) as the village page: one request serves both.
  const village = useVillage(villageId);
  const current = village.data ?? me.villages.find((summary) => summary.id === villageId);
  const loadedId = village.data?.id;
  // New levels and production show up by themselves when a build order ends, on every screen.
  useRefreshWhenBuildsEnd(village.data, village.dataUpdatedAt, village.errorUpdatedAt);

  // Remember the village once it loaded (it is the player's), so "/" opens it next time.
  useEffect(() => {
    if (loadedId !== undefined) {
      rememberVillage(loadedId);
    }
  }, [loadedId]);
  const [menu, setMenu] = useState<MenuKind | null>(null);
  const [chatOpen, setChatOpen] = useState(false);
  const [worldInfoOpen, setWorldInfoOpen] = useState(false);
  // Phones: the resource strip is one slim row unless the player opens the details (remembered).
  const [details, setDetails] = useState(loadResourceDetails);
  const resourcesId = useId();

  return (
    <div className={fullScreen || wide ? `${styles.app} ${styles.appFull}` : styles.app}>
      <header className={styles.header}>
        <div className={styles.topBar}>
          {/* desktop only: back to the village overview */}
          <Link className={styles.brand} to={villageId === undefined ? '/' : `/v/${villageId}`} title={t.nav.village}>
            {t.common.appName}
          </Link>
          <div className={styles.switcher}>
            <VillageSwitcher current={current} />
          </div>
          <div className={styles.attacks}>
            <IncomingAttacksBadge currentVillageId={villageId} />
          </div>
          <button
            type="button"
            className={styles.detailsButton}
            onClick={() => {
              setDetails(!details);
              saveResourceDetails(!details);
            }}
            aria-expanded={details}
            aria-controls={resourcesId}
            aria-label={details ? t.resources.hideDetails : t.resources.showDetails}
            title={details ? t.resources.hideDetails : t.resources.showDetails}
          >
            <Icon name={details ? 'chevronUp' : 'chevronDown'} />
          </button>
          <div id={resourcesId} className={details ? styles.resources : `${styles.resources} ${styles.resourcesSlim}`}>
            {village.data && <ResourceBar village={village.data} compact={!details} />}
          </div>
          <button
            type="button"
            className={styles.settingsButton}
            onClick={() => setMenu('settings')}
            aria-label={t.nav.settings}
            aria-haspopup="dialog"
          >
            <Icon name="settings" />
          </button>
        </div>
        <DesktopNav villageId={villageId} />
      </header>
      <ChangelogBanner />
      <main className={[styles.main, fullScreen && styles.mainFull, wide && styles.mainWide].filter(Boolean).join(' ')}>
        {me.needsRespawn && !allowedWithoutVillage ? <Navigate to="/" replace /> : <Outlet />}
      </main>
      <footer className={styles.footer}>
        {t.settings.serverTime} <ServerClock /> <VersionTag />
      </footer>
      <TabBar villageId={villageId} onMore={() => setMenu('more')} inFlow={fullScreen} />
      <MenuSheet
        kind={menu}
        villageId={villageId}
        onClose={() => setMenu(null)}
        onOpenChat={() => {
          setMenu(null);
          setChatOpen(true);
        }}
        onOpenWorldInfo={() => {
          setMenu(null);
          setWorldInfoOpen(true);
        }}
      />
      <Sheet open={worldInfoOpen} onClose={() => setWorldInfoOpen(false)} title={t.worldInfo.title}>
        <WorldInfo />
      </Sheet>
      {desktop ? (
        <ChatDock onMap={onMap} />
      ) : (
        <Sheet open={chatOpen} onClose={() => setChatOpen(false)} title={t.chat.title} fullScreen>
          <ChatPanel />
        </Sheet>
      )}
    </div>
  );
}

import { useEffect, useId, useMemo, useRef, useState, type RefObject } from 'react';
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
import { setFrozenAt } from '../game/serverTime';
import { SOURCE_URL } from '../game/version';
import { ToastProvider } from '../components/ToastProvider';
import { VersionTag } from '../components/VersionTag';
import { useT } from '../i18n';
import { ChatDock } from '../pages/chat/ChatDock';
import { ChatPanel } from '../pages/chat/ChatPanel';
import { PregameFrame } from '../pages/pregame/PregameFrame';
import { WorldInfo } from '../pages/pregame/WorldInfo';
import { RealtimeProvider } from '../realtime/RealtimeProvider';
import { ActionsStayOn } from './ActionsStayOn';
import styles from './AppLayout.module.css';
import { ChangelogBanner } from './ChangelogBanner';
import { VictoryClockStrip } from './VictoryClockStrip';
import { WorldEndedBanner } from './WorldEndedBanner';
import { WorldEndScreen } from './WorldEndScreen';
import { rememberVillage } from './currentVillage';
import { IncomingAttacksBadge } from './IncomingAttacksBadge';
import { MenuSheet, type MenuKind } from './MenuSheet';
import { DesktopNav, TabBar } from './navigation';
import { loadResourceDetails, saveResourceDetails } from './resourceDetails';
import { useCurrentVillageId } from './useCurrentVillageId';
import { useNavEntry } from './useNavEntry';
import { OPENS_AT_TOP } from './opensAtTop';
import { ScrollToTop } from './ScrollToTop';
import { DESKTOP_QUERY, useMediaQuery } from './useMediaQuery';
import { VillageSwitcher } from './VillageSwitcher';
import { isOpenWithoutVillage } from './withoutVillage';
import { isOpenWhenEnded, WorldEndContext } from './worldEnd';
import { usePushRefresh } from '../push/usePushRefresh';

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
  // A world that has ended is frozen (v1.3): resources and countdowns stand still at its end.
  const endedAt = world.data?.phase === 'ENDED' ? world.data.endedAt : null;
  useEffect(() => setFrozenAt(endedAt === null ? null : Date.parse(endedAt)), [endedAt]);
  // ... and its game buttons are off (Button), except where ActionsStayOn says otherwise
  const ended = endedAt !== null;
  const worldEnd = useMemo(() => ({ ended, frozen: ended }), [ended]);
  if (world.data?.phase === 'PREGAME') {
    return <PregameFrame />;
  }
  // Without the answer the game frame would flash the village screens for a moment in the pre-game.
  if (world.isPending) {
    return <FullScreenMessage>{t.common.loading}</FullScreenMessage>;
  }
  return (
    <WorldEndContext value={worldEnd}>
      <GameFrame />
    </WorldEndContext>
  );
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
  const { pathname } = useLocation();
  const allowedWithoutVillage = isOpenWithoutVillage(pathname);
  // the own profile and the settings pages are in the gear menu, so the gear is lit there (1.5.1)
  const navEntry = useNavEntry();
  const onSettingsPage = navEntry === 'profile' || navEntry === 'settings';
  const villageId = useCurrentVillageId();
  // Same query (and cache entry) as the village page: one request serves both.
  const village = useVillage(villageId);
  const current = village.data ?? me.villages.find((summary) => summary.id === villageId);
  const loadedId = village.data?.id;
  // New levels and production show up by themselves when a build order ends, on every screen.
  useRefreshWhenBuildsEnd(village.data, village.dataUpdatedAt, village.errorUpdatedAt);
  usePushRefresh(me.accountId);

  // Remember the village once it loaded (it is the player's), so "/" opens it again in this session.
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
  const appRef = useRef<HTMLDivElement>(null);
  const headerRef = useRef<HTMLElement>(null);
  const footerRef = useRef<HTMLElement>(null);
  useBarHeights(appRef, headerRef, footerRef);

  return (
    <div ref={appRef} className={fullScreen || wide ? `${styles.app} ${styles.appFull}` : styles.app}>
      <ScrollToTop />
      <header ref={headerRef} className={styles.header}>
        <div className={styles.topBar}>
          {/* desktop only: back to the village overview */}
          <Link
            className={styles.brand}
            to={villageId === undefined ? '/' : `/v/${villageId}`}
            state={OPENS_AT_TOP}
            title={t.nav.village}
          >
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
            aria-current={onSettingsPage ? 'page' : undefined}
          >
            <Icon name="settings" />
          </button>
        </div>
        <DesktopNav villageId={villageId} />
      </header>
      <WorldEndedBanner />
      <VictoryClockStrip />
      <WorldEndScreen />
      <ChangelogBanner />
      <main className={[styles.main, fullScreen && styles.mainFull, wide && styles.mainWide].filter(Boolean).join(' ')}>
        <ActionsStayOn open={isOpenWhenEnded(pathname)}>
          {me.needsRespawn && !allowedWithoutVillage ? <Navigate to="/" replace /> : <Outlet />}
        </ActionsStayOn>
      </main>
      <footer ref={footerRef} className={styles.footer}>
        {t.settings.serverTime} <ServerClock /> <VersionTag />
        <span aria-hidden="true">·</span>
        <a
          className={styles.footerLink}
          href={SOURCE_URL}
          target="_blank"
          rel="noopener"
          title={t.settings.sourceTitle}
        >
          {t.settings.source}
        </a>
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
        profile
      />
      <Sheet open={worldInfoOpen} onClose={() => setWorldInfoOpen(false)} title={t.worldInfo.title}>
        <WorldInfo />
      </Sheet>
      {/* the chat stays open in an ended world (v1.3) */}
      <ActionsStayOn>
        {desktop ? (
          <ChatDock onMap={onMap} />
        ) : (
          <Sheet open={chatOpen} onClose={() => setChatOpen(false)} title={t.chat.title} fullScreen>
            <ChatPanel />
          </Sheet>
        )}
      </ActionsStayOn>
    </div>
  );
}

/**
 * The sticky header's and the footer's heights as `--app-header-height` and `--app-footer-height`
 * on the frame (1.3), for what sticks or is fixed below the header or fills the window between the
 * two: the open report or conversation next to the list, the chat column. They change with the
 * window width and the resource details.
 */
function useBarHeights(
  appRef: RefObject<HTMLDivElement | null>,
  headerRef: RefObject<HTMLElement | null>,
  footerRef: RefObject<HTMLElement | null>,
) {
  useEffect(() => {
    const app = appRef.current;
    const header = headerRef.current;
    const footer = footerRef.current;
    if (app === null || header === null || typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver(() => {
      app.style.setProperty('--app-header-height', `${header.getBoundingClientRect().height}px`);
      app.style.setProperty('--app-footer-height', `${footer?.getBoundingClientRect().height ?? 0}px`);
    });
    observer.observe(header);
    if (footer !== null) {
      observer.observe(footer);
    }
    return () => observer.disconnect();
  }, [appRef, headerRef, footerRef]);
}

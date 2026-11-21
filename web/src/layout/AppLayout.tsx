import { useCallback, useEffect, useId, useMemo, useRef, useState, type CSSProperties, type RefObject } from 'react';
import { Link, Navigate, Outlet, useLocation, useMatch } from 'react-router';
import { useCurrentAccount, useMe } from '../api/queries/auth';
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
import { useChatDock } from '../pages/chat/useChatDock';
import { ChatPanel } from '../pages/chat/ChatPanel';
import { PregameFrame } from '../pages/pregame/PregameFrame';
import { WorldInfo } from '../pages/pregame/WorldInfo';
import { BroadcastPopup } from '../realtime/BroadcastPopup';
import { RealtimeProvider } from '../realtime/RealtimeProvider';
import { ActionsStayOn } from './ActionsStayOn';
import styles from './AppLayout.module.css';
import { ChangelogBanner } from './ChangelogBanner';
import { NewVersionBanner } from './NewVersionBanner';
import { HelpRequestStrip } from './HelpRequestStrip';
import { VictoryClockStrip } from './VictoryClockStrip';
import { WorldEndedBanner } from './WorldEndedBanner';
import { WorldPausedBanner } from './WorldPausedBanner';
import { WorldEndScreen } from './WorldEndScreen';
import { rememberVillage } from './currentVillage';
import { IncomingAttacksBadge } from './IncomingAttacksBadge';
import { GearMenu } from './GearMenu';
import { menuPlaceUnder } from './menuPlace';
import { MenuSheet, type MenuKind } from './MenuSheet';
import { DesktopNav, TabBar } from './navigation';
import { loadResourceDetails, saveResourceDetails } from './resourceDetails';
import { useCurrentVillageId } from './useCurrentVillageId';
import { useNavEntry } from './useNavEntry';
import { OPENS_AT_TOP } from './opensAtTop';
import { ScrollToTop } from './ScrollToTop';
import { useSlimSky } from './slimSky';
import { CHAT_COLUMN_QUERY, DESKTOP_QUERY, useMediaQuery } from './useMediaQuery';
import { VillageSwitcher } from './VillageSwitcher';
import { isOpenWithoutVillage } from './withoutVillage';
import { isOpenWhenEnded, useWorldEnded, WorldEndContext } from './worldEnd';
import { usePushRefresh } from '../push/usePushRefresh';
import { NowColumn } from './now/NowColumn';
import { NowDrawer } from './now/NowDrawer';
import { NowHintStrip } from './now/NowHintStrip';
import { NowSheet } from './now/NowSheet';
import { useTutorialSync } from './useTutorialSync';

/**
 * Frame of all game screens (05-frontend §3, §8; the sky since 2.0). Phone: the sky on top (village
 * switcher, attack alarm) with the four stocks under its hills, and a tab bar at the bottom; the sky
 * gets slim once the page is scrolled. Desktop (≥ 1024 px): the sky with brand, switcher, stocks and
 * settings, a rail with every page at the left, the server time under the page. The CSS does the switch.
 * Around it the live connection (toasts, notifications, chat; M9b): it opens with the game screens
 * after login and closes when they go away on logout.
 */
export function AppLayout() {
  return (
    <ToastProvider>
      <RealtimeProvider>
        <PhaseFrame />
        <BroadcastPopup />
      </RealtimeProvider>
    </ToastProvider>
  );
}

/** Before the world opens (M14g) there is no village to frame: the slim pre-game frame, else the game's. */
function PhaseFrame() {
  const t = useT();
  const world = useWorld();
  // A world that has ended is frozen (v1.3): resources and countdowns stand still at its end. So does a
  // solo world the host paused (2.1), at the moment he paused it.
  const endedAt = world.data?.phase === 'ENDED' ? world.data.endedAt : null;
  const pausedAt = world.data?.pausedAt ?? null;
  const frozenAt = endedAt ?? pausedAt;
  useEffect(() => setFrozenAt(frozenAt === null ? null : Date.parse(frozenAt)), [frozenAt]);
  // ... and its game buttons are off (Button), except where ActionsStayOn says otherwise
  const ended = endedAt !== null;
  const paused = !ended && pausedAt !== null;
  const host = useMe().data?.role === 'ADMIN';
  const worldEnd = useMemo(() => ({ ended, frozen: ended || paused, paused, host }), [ended, paused, host]);
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
  // The Now list (2.0): a column from 1600 px (not on the map), a tab with a drawer on the other desktops and
  // on the map, the pull-down sheet on phones. An ended world has nothing running any more: no Now list.
  const wideScreen = useMediaQuery(CHAT_COLUMN_QUERY);
  const ended = useWorldEnded();
  const nowMode = ended ? 'none' : !desktop ? 'sheet' : wideScreen && !onMap ? 'column' : 'drawer';
  const [nowOpen, setNowOpen] = useState(false);
  const fullScreen = onMap && !desktop;
  const wide = onMap && desktop;
  // Without a village (all conquered, M11) only "/" (the respawn screen) and the pages that aren't about one village are open.
  const { pathname } = useLocation();
  const allowedWithoutVillage = isOpenWithoutVillage(pathname);
  // another page puts the overlay away (set while rendering, like the rally point's visits)
  const [nowPath, setNowPath] = useState(pathname);
  if (nowPath !== pathname) {
    setNowPath(pathname);
    setNowOpen(false);
  }
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
  // the tutorial card follows the village (1.7)
  useTutorialSync(village.dataUpdatedAt);

  // Remember the village once it loaded (it is the player's), so "/" opens it again in this session.
  useEffect(() => {
    if (loadedId !== undefined) {
      rememberVillage(loadedId);
    }
  }, [loadedId]);
  const [menu, setMenu] = useState<MenuKind | null>(null);
  // the PC's gear opens a small menu under the gear, phones and the pre-game a sheet (2.0)
  const gearRef = useRef<HTMLButtonElement>(null);
  const [gearPlace, setGearPlace] = useState<CSSProperties | null>(null);
  const gearOpen = desktop && menu === 'settings' && gearPlace !== null;
  const closeMenu = useCallback(() => setMenu(null), []);
  const [chatOpen, setChatOpen] = useState(false);
  // desktop: the chat panel, opened from the rail (remembered, except on the map)
  const [dockOpen, setDockOpen] = useChatDock(onMap);
  // the rail's Chat entry: "Minimise" on the panel moves the keyboard focus here
  const chatEntryRef = useRef<HTMLButtonElement>(null);
  // phones: one slim band on the map and on a page that is scrolled down (2.0)
  const slim = useSlimSky(onMap);
  const [worldInfoOpen, setWorldInfoOpen] = useState(false);
  // Phones: the resource strip is one slim row unless the player opens the details (remembered).
  const [details, setDetails] = useState(loadResourceDetails);
  const resourcesId = useId();
  const appRef = useRef<HTMLDivElement>(null);
  const headerRef = useRef<HTMLElement>(null);
  const footerRef = useRef<HTMLElement>(null);
  useBarHeights(appRef, headerRef, footerRef);

  return (
    <div
      ref={appRef}
      className={[styles.app, (fullScreen || wide) && styles.appFull, nowMode === 'drawer' && styles.appNowTab]
        .filter(Boolean)
        .join(' ')}
    >
      <ScrollToTop />
      <header ref={headerRef} className={styles.header} data-slim={slim || undefined}>
        <div className={styles.topBar}>
          {/* the sky itself: three bands (with stars at night), the sun or moon in the free room after the
              village's name, two bands of hills in front of it */}
          <div className={styles.sky} aria-hidden="true" />
          <div className={styles.orbSlot} aria-hidden="true">
            <span className={styles.orb} />
          </div>
          <svg className={styles.hills} viewBox="0 0 400 30" preserveAspectRatio="none" aria-hidden="true">
            <path className={styles.hillFar} d="M0 30V15C60 3 110 2 170 12S300 25 400 7V30Z" />
            <path className={styles.hillNear} d="M0 30V23C80 11 150 25 230 19S340 9 400 21V30Z" />
          </svg>
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
            <IncomingAttacksBadge
              onOpen={() => {
                if (nowMode === 'none') {
                  return;
                }
                if (nowMode === 'column') {
                  // always there: the keyboard goes to it
                  document.getElementById('now-column')?.querySelector('h2')?.focus();
                } else {
                  setNowOpen((open) => nowMode === 'drawer' || !open);
                }
              }}
            />
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
          {/* phones: the slim row opens the details on a tap (the arrow button is the keyboard's way) */}
          <div
            id={resourcesId}
            className={details ? styles.resources : `${styles.resources} ${styles.resourcesSlim}`}
            onClick={
              desktop || details
                ? undefined
                : () => {
                    setDetails(true);
                    saveResourceDetails(true);
                  }
            }
          >
            {village.data && <ResourceBar village={village.data} compact={!details} linked={desktop || details} />}
          </div>
          <button
            ref={gearRef}
            type="button"
            className={styles.settingsButton}
            onClick={(event) => {
              setGearPlace(menuPlaceUnder(event.currentTarget, 'right'));
              setMenu(gearOpen ? null : 'settings');
            }}
            aria-label={t.nav.settings}
            aria-haspopup="dialog"
            aria-expanded={gearOpen}
            aria-current={onSettingsPage ? 'page' : undefined}
          >
            <Icon name="settings" />
          </button>
        </div>
      </header>
      <div className={styles.body}>
        <DesktopNav
          villageId={villageId}
          chatOpen={dockOpen}
          onToggleChat={() => setDockOpen(!dockOpen)}
          chatEntryRef={chatEntryRef}
        />
        <div className={styles.column}>
          <WorldEndedBanner />
          <WorldPausedBanner />
          <HelpRequestStrip />
          <VictoryClockStrip />
          <WorldEndScreen />
          <NewVersionBanner />
          <ChangelogBanner />
          {/* not over the map, which fills the phone's screen: the hint waits for the next page (2.0 UI review
              follow-up: three lines at 320 px in German left the map 336 of 568 px) */}
          {nowMode === 'sheet' && !fullScreen && <NowHintStrip sheetOpen={nowOpen} />}
          <main
            className={[styles.main, fullScreen && styles.mainFull, wide && styles.mainWide].filter(Boolean).join(' ')}
          >
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
        </div>
      </div>
      <TabBar villageId={villageId} onMore={() => setMenu('more')} inFlow={fullScreen} />
      <GearMenu
        place={gearOpen ? gearPlace : null}
        anchor={gearRef}
        onClose={closeMenu}
        onOpenWorldInfo={() => {
          setMenu(null);
          setWorldInfoOpen(true);
        }}
      />
      <MenuSheet
        kind={gearOpen ? null : menu}
        villageId={villageId}
        onClose={() => setMenu(null)}
        onOpenChat={() => {
          setMenu(null);
          setChatOpen(true);
        }}
        onOpenNow={
          nowMode === 'sheet'
            ? () => {
                setMenu(null);
                setNowOpen(true);
              }
            : undefined
        }
        onOpenWorldInfo={() => {
          setMenu(null);
          setWorldInfoOpen(true);
        }}
        profile
      />
      <Sheet open={worldInfoOpen} onClose={() => setWorldInfoOpen(false)} title={t.worldInfo.title}>
        <WorldInfo />
      </Sheet>
      {nowMode === 'column' && <NowColumn currentVillageId={villageId} withChat={dockOpen} />}
      {nowMode === 'drawer' && <NowDrawer open={nowOpen} onOpenChange={setNowOpen} currentVillageId={villageId} />}
      {nowMode === 'sheet' && (
        <NowSheet headerRef={headerRef} open={nowOpen} onOpenChange={setNowOpen} currentVillageId={villageId} />
      )}
      {/* the chat stays open in an ended world (v1.3) */}
      <ActionsStayOn>
        {desktop ? (
          <ChatDock
            onMap={onMap}
            open={dockOpen}
            onOpenChange={setDockOpen}
            focusAfterMinimise={chatEntryRef}
            belowNow={nowMode === 'column'}
            besideNowTab={nowMode === 'drawer'}
          />
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

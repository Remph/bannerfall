import { useState } from 'react';
import { Link, Navigate, Outlet, useLocation } from 'react-router';
import { Icon } from '../../components/Icon';
import { ServerClock } from '../../components/ServerClock';
import { Sheet } from '../../components/Sheet';
import { useI18n } from '../../i18n';
import { MenuSheet } from '../../layout/MenuSheet';
import { DESKTOP_QUERY, useMediaQuery } from '../../layout/useMediaQuery';
import { useChatUnread } from '../../realtime/chatSeen';
import { badgeText } from '../../game/reports';
import { ChatDock } from '../chat/ChatDock';
import { ChatPanel } from '../chat/ChatPanel';
import styles from './Pregame.module.css';

/** The pages that work without a village (the server closes the rest, `PregameGateFilter`). */
const PREGAME_PATHS: readonly string[] = ['/', '/admin', '/password', '/about', '/guild'];

/** The pre-game pages with an id in the path (a guild's page, M15a). */
const PREGAME_PREFIXES: readonly string[] = ['/guilds/'];

/**
 * The frame of the pre-game (M14g): a slim bar with the chat and the settings menu, no village
 * switcher, resources or tabs. Desktop has the chat dock like the game, phones a button in the bar.
 */
export function PregameFrame() {
  const { t } = useI18n();
  const location = useLocation();
  const desktop = useMediaQuery(DESKTOP_QUERY);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const unread = badgeText(useChatUnread());

  if (!PREGAME_PATHS.includes(location.pathname) && !PREGAME_PREFIXES.some((p) => location.pathname.startsWith(p))) {
    return <Navigate to="/" replace />;
  }
  return (
    <div className={styles.frame}>
      <header className={styles.bar}>
        {/* back to the pre-game page, e.g. from the admin page (there is no other way without typing the address) */}
        <Link className={styles.brand} to="/">
          {t.common.appName}
        </Link>
        {!desktop && (
          <button
            type="button"
            className={styles.barButton}
            onClick={() => setChatOpen(true)}
            aria-haspopup="dialog"
            aria-label={t.nav.chat}
          >
            <Icon name="chat" />
            {unread !== undefined && <span aria-hidden="true">{unread}</span>}
          </button>
        )}
        <button
          type="button"
          className={styles.barButton}
          onClick={() => setSettingsOpen(true)}
          aria-label={t.nav.settings}
          aria-haspopup="dialog"
        >
          <Icon name="settings" />
        </button>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
      <MenuSheet kind={settingsOpen ? 'settings' : null} villageId={undefined} onClose={() => setSettingsOpen(false)} />
      {desktop ? (
        <ChatDock />
      ) : (
        <Sheet open={chatOpen} onClose={() => setChatOpen(false)} title={t.chat.title} fullScreen>
          <ChatPanel />
        </Sheet>
      )}
      <p className="sr-only">
        {t.settings.serverTime} <ServerClock />
      </p>
    </div>
  );
}

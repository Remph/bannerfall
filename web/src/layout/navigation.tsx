import type { ReactNode } from 'react';
import { Link, NavLink, useLocation, useMatch } from 'react-router';
import { useUnreadMessages } from '../api/queries/messages';
import { useUnreadReports } from '../api/queries/reports';
import { useMyGuild } from '../api/queries/guilds';
import { useWorld } from '../api/queries/world';
import { Icon } from '../components/Icon';
import { badgeText } from '../game/reports';
import { guildSectionVisible } from '../game/guilds';
import { useT } from '../i18n';
import { useChatUnread } from '../realtime/chatSeen';
import styles from './navigation.module.css';
import { desktopSections, moreSections, phoneTabs, sections, visibleSections, type SectionKey } from './sections';

/**
 * Whether the guild section is in the navigation: while the host has guilds switched on, and for
 * the members of an existing guild even when it is off, so they keep their way to it (M15a).
 */
function useGuildSectionVisible(): boolean {
  const enabled = useWorld().data?.guildsEnabled === true;
  const guild = useMyGuild().data?.guild;
  return guildSectionVisible(enabled, guild !== null && guild !== undefined);
}

interface SectionLinkProps {
  sectionKey: SectionKey;
  villageId: number | undefined;
  className: string | undefined; // CSS module classes are typed `string | undefined`
  onNavigate?: () => void;
  /** Show "coming soon" as a visible pill (touch screens never show the `title` tooltip). */
  showSoon?: boolean;
  children: ReactNode;
}

/**
 * A link to a section, or a dimmed "coming soon" entry while its screen doesn't exist yet (or, for
 * a per-village section, while the player has no village).
 * NavLink sets aria-current="page" on the active link; the CSS highlights that. The bars' links
 * cross-fade the page (View Transitions, 1.3), except to the page already open; the menu sheet's
 * links don't, as the sheet closes.
 */
function SectionLink({ sectionKey, villageId, className, onNavigate, showSoon = false, children }: SectionLinkProps) {
  const t = useT();
  // the warehouse (M22), smithy (M30) and Banner Hall (M32a) pages have no entry of their own: they are part of the village overview, so that tab stays lit
  const onWarehouse = useMatch('/v/:villageId/warehouse') !== null;
  const onSmithy = useMatch('/v/:villageId/smithy') !== null;
  const onBanners = useMatch('/v/:villageId/banners') !== null;
  const { pathname } = useLocation();
  const section = sections[sectionKey];
  const target =
    section.path !== undefined && (villageId !== undefined || section.global === true)
      ? section.path(villageId ?? 0)
      : undefined;
  if (target === undefined) {
    return (
      <span className={className} aria-disabled="true" title={t.common.comingSoon}>
        {children}
        {showSoon ? (
          <span className={styles.soon}>{t.common.comingSoon}</span>
        ) : (
          <span className="sr-only"> ({t.common.comingSoon})</span>
        )}
      </span>
    );
  }
  const viewTransition = onNavigate === undefined && pathname !== target;
  if (sectionKey === 'village' && (onWarehouse || onSmithy || onBanners)) {
    return (
      <Link viewTransition={viewTransition} to={target} className={className} aria-current="page" onClick={onNavigate}>
        {children}
      </Link>
    );
  }
  // `end`: the village overview /v/1 is not "active" on /v/1/hq.
  return (
    <NavLink
      viewTransition={viewTransition}
      to={target}
      end={sectionKey === 'village'}
      className={className}
      onClick={onNavigate}
    >
      {children}
    </NavLink>
  );
}

/**
 * The number of unread reports on the Reports entry (05-frontend §3), nothing while there are none.
 * Screen readers hear "Reports, 3 unread reports" instead of a bare number.
 */
function UnreadBadge({ className }: { className: string | undefined }) {
  const t = useT();
  const unread = useUnreadReports().data ?? 0;
  const text = badgeText(unread);
  if (text === undefined) {
    return null;
  }
  return (
    <span className={className}>
      <span aria-hidden="true">{text}</span>
      <span className="sr-only">, {t.reports.unreadBadge(unread)}</span>
    </span>
  );
}

/** Unread chat messages (phone: on "More" and its chat entry), nothing while there are none. */
function ChatBadge({ className }: { className: string | undefined }) {
  const t = useT();
  const unread = useChatUnread();
  const text = badgeText(unread);
  if (text === undefined) {
    return null;
  }
  return (
    <span className={className}>
      <span aria-hidden="true">{text}</span>
      <span className="sr-only">, {t.chat.unreadBadge(unread)}</span>
    </span>
  );
}

/** Unread private messages on the Messages entry, nothing while there are none (M17). */
function MessagesBadge({ className }: { className: string | undefined }) {
  const t = useT();
  const unread = useUnreadMessages().data ?? 0;
  const text = badgeText(unread);
  if (text === undefined) {
    return null;
  }
  return (
    <span className={className}>
      <span aria-hidden="true">{text}</span>
      <span className="sr-only">, {t.messages.unreadBadge(unread)}</span>
    </span>
  );
}

/** Phone: the badge on "More" counts what is behind it, unread chat and unread messages. */
function MoreBadge({ className }: { className: string | undefined }) {
  const t = useT();
  const chat = useChatUnread();
  const messages = useUnreadMessages().data ?? 0;
  const text = badgeText(chat + messages);
  if (text === undefined) {
    return null;
  }
  return (
    <span className={className}>
      <span aria-hidden="true">{text}</span>
      <span className="sr-only">
        {chat > 0 && `, ${t.chat.unreadBadge(chat)}`}
        {messages > 0 && `, ${t.messages.unreadBadge(messages)}`}
      </span>
    </span>
  );
}

/**
 * Desktop (≥ 1024 px): the tab row under the top bar. The village's pages on the left; the pages
 * that are the same for every village (reports, messages …) on the right after a divider (1.3).
 */
export function DesktopNav({ villageId }: { villageId: number | undefined }) {
  const t = useT();
  const guildVisible = useGuildSectionVisible();
  const keys = visibleSections(desktopSections, guildVisible);
  const firstGlobal = keys.find((key) => sections[key].global === true);
  return (
    <nav className={styles.desktopNav} aria-label={t.nav.label}>
      {keys.map((key) => (
        <SectionLink
          key={key}
          sectionKey={key}
          villageId={villageId}
          className={key === firstGlobal ? `${styles.navItem} ${styles.groupStart}` : styles.navItem}
        >
          {t.nav[key]}
          {key === 'reports' && <UnreadBadge className={styles.badge} />}
          {key === 'messages' && <MessagesBadge className={styles.badge} />}
        </SectionLink>
      ))}
    </nav>
  );
}

interface TabBarProps {
  villageId: number | undefined;
  onMore: () => void;
  /** Part of the page's column instead of fixed over it (full-screen screens like the map). */
  inFlow?: boolean;
}

/** Phone: the tab bar at the bottom, within thumb reach. */
export function TabBar({ villageId, onMore, inFlow = false }: TabBarProps) {
  const t = useT();
  return (
    <nav className={inFlow ? `${styles.tabBar} ${styles.tabBarInFlow}` : styles.tabBar} aria-label={t.nav.label}>
      {phoneTabs.map((key) => (
        <SectionLink key={key} sectionKey={key} villageId={villageId} className={styles.tab}>
          <span className={styles.tabIcon}>
            <Icon name={sections[key].icon} />
            {key === 'reports' && <UnreadBadge className={styles.tabBadge} />}
          </span>
          {/* On the phone the rally point tab is simply called "Troops". */}
          <span>{key === 'rallyPoint' ? t.nav.troops : t.nav[key]}</span>
        </SectionLink>
      ))}
      <button type="button" className={styles.tab} onClick={onMore} aria-haspopup="dialog">
        <span className={styles.tabIcon}>
          <Icon name="more" />
          <MoreBadge className={styles.tabBadge} />
        </span>
        <span>{t.nav.more}</span>
      </button>
    </nav>
  );
}

interface MoreLinksProps {
  villageId: number | undefined;
  onNavigate: () => void;
  onOpenChat: () => void;
}

/** Phone: the sections that don't fit into the tab bar (inside the More sheet), and the chat. */
export function MoreLinks({ villageId, onNavigate, onOpenChat }: MoreLinksProps) {
  const t = useT();
  const guildVisible = useGuildSectionVisible();
  return (
    <ul className={styles.moreList}>
      {visibleSections(moreSections, guildVisible).map((key) => (
        <li key={key}>
          <SectionLink
            sectionKey={key}
            villageId={villageId}
            className={styles.moreItem}
            onNavigate={onNavigate}
            showSoon
          >
            <span className={styles.moreIcon}>
              <Icon name={sections[key].icon} />
            </span>
            <span className={styles.moreLabel}>
              {t.nav[key]}
              {key === 'messages' && <MessagesBadge className={styles.badge} />}
            </span>
          </SectionLink>
        </li>
      ))}
      <li>
        <button type="button" className={styles.moreItem} onClick={onOpenChat} aria-haspopup="dialog">
          <span className={styles.moreIcon}>
            <Icon name="chat" />
          </span>
          <span className={styles.moreLabel}>
            {t.nav.chat}
            <ChatBadge className={styles.badge} />
          </span>
        </button>
      </li>
    </ul>
  );
}

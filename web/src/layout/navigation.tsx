import { useId, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { useUnreadMessages } from '../api/queries/messages';
import { useUnreadReports } from '../api/queries/reports';
import { useMyGuild } from '../api/queries/guilds';
import { useWorld } from '../api/queries/world';
import { Icon } from '../components/Icon';
import { bannerHallAvailable } from '../game/banners';
import { badgeText } from '../game/reports';
import { guildSectionVisible } from '../game/guilds';
import { useT } from '../i18n';
import { playerPath } from '../pages/ranking/rankingPaths';
import { useChatUnread } from '../realtime/chatSeen';
import styles from './navigation.module.css';
import { useNavEntry } from './useNavEntry';
import {
  desktopSections,
  moreGroups,
  phoneTabs,
  bannerHallPath,
  sections,
  visibleSections,
  type NavEntry,
  type OptionalSections,
  type SectionKey,
} from './sections';

/**
 * Which optional sections are in the navigation. The guild: while the host has guilds switched on,
 * and for the members of an existing guild even when it is off, so they keep their way to it
 * (M15a). The Banner Hall: while it can be built, i.e. paladins or banners are on (1.4).
 */
function useOptionalSections(): OptionalSections {
  const world = useWorld().data;
  const guild = useMyGuild().data?.guild;
  return {
    guild: guildSectionVisible(world?.guildsEnabled === true, guild !== null && guild !== undefined),
    bannerHall: world !== undefined && bannerHallAvailable(world),
  };
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
 * The link of the open page's entry gets aria-current="page" (`useNavEntry`); the CSS highlights
 * that. The bars' links cross-fade the page (View Transitions, 1.3), except to the page already
 * open; the menu sheet's links don't, as the sheet closes.
 */
function SectionLink({ sectionKey, villageId, className, onNavigate, showSoon = false, children }: SectionLinkProps) {
  const t = useT();
  const bannersOn = useWorld().data?.bannersEnabled !== false;
  const current = useNavEntry() === sectionKey;
  const { pathname } = useLocation();
  const section = sections[sectionKey];
  const target =
    sectionKey === 'bannerHall' && villageId !== undefined
      ? bannerHallPath(villageId, bannersOn)
      : section.path !== undefined && (villageId !== undefined || section.global === true)
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
  return (
    <Link
      viewTransition={onNavigate === undefined && pathname !== target}
      to={target}
      className={className}
      aria-current={current ? 'page' : undefined}
      onClick={onNavigate}
    >
      {children}
    </Link>
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
  const keys = visibleSections(desktopSections, useOptionalSections());
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
  const entry = useNavEntry();
  // every page without a tab of its own is behind "More", so that tab is lit there (1.5.1)
  const behindMore = entry !== undefined && !(phoneTabs as readonly NavEntry[]).includes(entry);
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
      <button
        type="button"
        className={styles.tab}
        onClick={onMore}
        aria-haspopup="dialog"
        aria-current={behindMore ? 'page' : undefined}
      >
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

/**
 * Phone: the sections that don't fit into the tab bar (inside the More sheet), and the chat, in
 * the desktop's two groups: the village's pages, then the player's (1.4), which opens with the
 * player's own profile (1.5.1; on desktop it is in the gear menu). Unread counts sit on the icon,
 * like in the tab bar, so a long label never wraps under them.
 */
export function MoreLinks({ villageId, onNavigate, onOpenChat }: MoreLinksProps) {
  const t = useT();
  const me = useCurrentAccount();
  const entry = useNavEntry();
  const shown = useOptionalSections();
  const headingId = useId();
  const item = (key: SectionKey) => (
    <li key={key}>
      <SectionLink sectionKey={key} villageId={villageId} className={styles.moreItem} onNavigate={onNavigate} showSoon>
        <span className={styles.moreIcon}>
          <Icon name={sections[key].icon} />
          {key === 'messages' && <MessagesBadge className={styles.moreBadge} />}
        </span>
        <span className={styles.moreLabel}>{t.nav[key]}</span>
      </SectionLink>
    </li>
  );
  return (
    <div className={styles.moreGroups}>
      <h3 id={`${headingId}-village`} className={styles.moreHeading}>
        {t.nav.moreVillage}
      </h3>
      <ul className={styles.moreList} aria-labelledby={`${headingId}-village`}>
        {visibleSections(moreGroups.village, shown).map(item)}
      </ul>
      <h3 id={`${headingId}-player`} className={styles.moreHeading}>
        {t.nav.morePlayer}
      </h3>
      <ul className={styles.moreList} aria-labelledby={`${headingId}-player`}>
        <li>
          <Link
            to={playerPath(me.playerId)}
            className={styles.moreItem}
            aria-current={entry === 'profile' ? 'page' : undefined}
            onClick={onNavigate}
          >
            <span className={styles.moreIcon}>
              <Icon name="profile" />
            </span>
            <span className={styles.moreLabel}>{t.nav.profile}</span>
          </Link>
        </li>
        {visibleSections(moreGroups.player, shown).flatMap((key) =>
          key === 'messages'
            ? [
                item(key),
                <li key="chat">
                  <button type="button" className={styles.moreItem} onClick={onOpenChat} aria-haspopup="dialog">
                    <span className={styles.moreIcon}>
                      <Icon name="chat" />
                      <ChatBadge className={styles.moreBadge} />
                    </span>
                    <span className={styles.moreLabel}>{t.nav.chat}</span>
                  </button>
                </li>,
              ]
            : [item(key)],
        )}
      </ul>
    </div>
  );
}

import { useId, type ReactNode, type RefObject } from 'react';
import { Link, useLocation } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { useUnreadMessages } from '../api/queries/messages';
import { useNow } from '../api/queries/now';
import { useUnreadReports } from '../api/queries/reports';
import { useMyGuild } from '../api/queries/guilds';
import { useWorld } from '../api/queries/world';
import { Icon } from '../components/Icon';
import { bannerHallAvailable } from '../game/banners';
import { badgeText } from '../game/reports';
import { guildSectionVisible } from '../game/guilds';
import { nowSections } from '../game/now';
import { useT } from '../i18n';
import { playerPath } from '../pages/ranking/rankingPaths';
import { useChatUnread } from '../realtime/chatSeen';
import styles from './navigation.module.css';
import { OPENS_AT_TOP } from './opensAtTop';
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
  /** A tooltip for a link whose name may be hidden (the rail folded to icons). */
  title?: string;
  children: ReactNode;
}

/**
 * A link to a section, or a dimmed "coming soon" entry while its screen doesn't exist yet (or, for
 * a per-village section, while the player has no village).
 * The link of the open page's entry gets aria-current="page" (`useNavEntry`); the CSS highlights
 * that. The bars' links cross-fade the page (View Transitions, 1.3), except to the page already
 * open; the menu sheet's links don't, as the sheet closes.
 */
function SectionLink({
  sectionKey,
  villageId,
  className,
  onNavigate,
  showSoon = false,
  title,
  children,
}: SectionLinkProps) {
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
      state={OPENS_AT_TOP}
      className={className}
      title={title}
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

/** How much runs, on the Now tile of "More" (2.0); nothing while nothing does. */
function NowBadge({ className }: { className: string | undefined }) {
  const t = useT();
  const now = useNow().data;
  const count = now === undefined ? 0 : nowSections(now, 'all', undefined).count;
  const text = badgeText(count);
  if (text === undefined) {
    return null;
  }
  return (
    <span className={className}>
      <span aria-hidden="true">{text}</span>
      <span className="sr-only">, {t.now.running(count)}</span>
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

interface DesktopNavProps {
  villageId: number | undefined;
  /** The chat panel (ChatDock): the rail's Chat entry opens and closes it. */
  chatOpen: boolean;
  onToggleChat: () => void;
  /** The Chat entry's button, so the panel's "Minimise" can hand the keyboard focus to it. */
  chatEntryRef?: RefObject<HTMLButtonElement | null>;
}

/**
 * Desktop (≥ 1024 px): the rail at the left with every page (2.0; a tab row under the top bar
 * before), in the More sheet's two groups: the village's pages, then the pages that are the same for
 * every village, with the chat after the messages. Below 1200 px it folds to icons only; the names
 * stay for screen readers and as tooltips.
 */
export function DesktopNav({ villageId, chatOpen, onToggleChat, chatEntryRef }: DesktopNavProps) {
  const t = useT();
  const keys = visibleSections(desktopSections, useOptionalSections());
  const headingId = useId();
  const item = (key: SectionKey) => (
    <li key={key}>
      <SectionLink sectionKey={key} villageId={villageId} className={styles.railItem} title={t.nav[key]}>
        <Icon name={sections[key].icon} className={styles.railIcon} />
        <span className={styles.railLabel}>{t.nav[key]}</span>
        {key === 'reports' && <UnreadBadge className={styles.railBadge} />}
        {key === 'messages' && <MessagesBadge className={styles.railBadge} />}
      </SectionLink>
    </li>
  );
  return (
    <nav className={styles.rail} aria-label={t.nav.label}>
      <h2 id={`${headingId}-village`} className={styles.railHeading}>
        {t.nav.moreVillage}
      </h2>
      <ul className={styles.railList} aria-labelledby={`${headingId}-village`}>
        {keys.filter((key) => sections[key].global !== true).map(item)}
      </ul>
      <h2 id={`${headingId}-player`} className={styles.railHeading}>
        {t.nav.morePlayer}
      </h2>
      <ul className={styles.railList} aria-labelledby={`${headingId}-player`}>
        {keys
          .filter((key) => sections[key].global === true)
          .flatMap((key) =>
            key === 'messages'
              ? [
                  item(key),
                  <li key="chat">
                    <button
                      ref={chatEntryRef}
                      type="button"
                      className={styles.railItem}
                      onClick={onToggleChat}
                      aria-expanded={chatOpen}
                      title={t.nav.chat}
                    >
                      <Icon name="chat" className={styles.railIcon} />
                      <span className={styles.railLabel}>{t.nav.chat}</span>
                      <ChatBadge className={styles.railBadge} />
                    </button>
                  </li>,
                ]
              : [item(key)],
          )}
      </ul>
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
          {/* one name per place (1.7 design review): the tab says "Rally Point" like the page it
              opens; as "Troops" it read like the place to recruit, which is behind "More" */}
          <span className={styles.tabLabel}>{t.nav[key]}</span>
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
  /** Phone: the first tile opens the Now sheet, for anyone who doesn't swipe the sky down (2.0). */
  onOpenNow?: () => void;
}

/**
 * Phone: the sections that don't fit into the tab bar (inside the More sheet), and the chat, in
 * the desktop's two groups: the village's pages, then the player's (1.4), which opens with the
 * player's own profile (1.5.1; on desktop it is in the gear menu). Unread counts sit on the icon,
 * like in the tab bar, so a long label never wraps under them.
 */
export function MoreLinks({ villageId, onNavigate, onOpenChat, onOpenNow }: MoreLinksProps) {
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
        {onOpenNow !== undefined && (
          <li>
            <button type="button" className={styles.moreItem} onClick={onOpenNow} aria-haspopup="dialog">
              <span className={styles.moreIcon}>
                <Icon name="clock" />
                <NowBadge className={styles.moreBadge} />
              </span>
              <span className={styles.moreLabel}>{t.now.title}</span>
            </button>
          </li>
        )}
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

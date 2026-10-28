import { useState, type ReactNode } from 'react';
import { useDisbandGuild, useEnemies, useLeaveGuild, type Guild } from '../../api/queries/guilds';
import { useWorld } from '../../api/queries/world';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { formatNumber } from '../../game/format';
import { enemySets } from '../../game/enemies';
import { bracketTag } from '../../game/guilds';
import { useI18n } from '../../i18n';
import { DescriptionCard } from './DescriptionCard';
import { EnemiesCard } from './EnemiesCard';
import { EnemyMenu } from './EnemyMenu';
import { EnemyPill } from './EnemyPill';
import { GuildBannerCard } from './GuildBannerCard';
import { InvitePlayerCard } from './InvitePlayerCard';
import { MembersCard } from './MembersCard';
import styles from './Guild.module.css';
import { OWN_GUILD_PATH } from './guildPaths';

interface GuildViewProps {
  guild: Guild;
  /** The viewer's player id; null when he is not in this guild (read-only view). */
  ownPlayerId: number | null;
  /** Most members a guild may have in this world. */
  maxMembers: number;
  /** A link back to the guild page (the page of somebody else's guild). */
  back?: boolean;
  /** Further cards at the end of the page. */
  children?: ReactNode;
}

/**
 * One guild: name, tag, points, description and members. For a member of this guild it also has
 * "Leave"; for its leader the tools to invite, edit, remove, hand the lead over and disband.
 */
export function GuildView({ guild, ownPlayerId, maxMembers, back = false, children }: GuildViewProps) {
  const { t, language } = useI18n();
  const own = guild.members.find((member) => member.playerId === ownPlayerId);
  const enemies = enemySets(useEnemies().data);
  const world = useWorld().data;
  // 1.7: the guild banner, for the members, where the world has it (and has villages to pay from)
  const bannerOn = world?.guildBannersEnabled === true && world.phase !== 'PREGAME';
  const isLeader = own?.role === 'LEADER';
  // another guild's profile has nothing for the right column: it stays one column
  const split = isLeader || own !== undefined || children !== undefined;

  return (
    <div className={split ? `${styles.page} ${styles.wide}` : styles.page}>
      {back && <BackLink to={OWN_GUILD_PATH}>{t.guild.back}</BackLink>}
      <header className={styles.head}>
        <h1>
          <span className={styles.tagInName}>{bracketTag(guild.tag)}</span>
          {guild.name}
          {own === undefined && enemies.guilds.has(guild.id) && <EnemyPill />}
        </h1>
        <p className={styles.facts}>
          {t.guild.membersOf(guild.members.length, maxMembers)} · {t.guild.points(formatNumber(guild.points, language))}
          {/* 1.7: the level of somebody else's guild banner, nothing more */}
          {own === undefined && world?.guildBannersEnabled === true && guild.bannerLevel > 0 && (
            <> · {t.guild.banner.publicLevel(guild.bannerLevel, 3)}</>
          )}
        </p>
        {/* the leader of another guild marks it (1.7); the own guild has none */}
        {own === undefined && (
          <EnemyMenu
            targets={[{ kind: 'GUILD', targetId: guild.id, name: guild.name }]}
            label={t.guild.enemies.more(guild.name)}
          />
        )}
      </header>
      {/* desktop: description, members and the banner left, the rest at the right (1.3); the banner
          is the long card, so it sits in the wide column (1.7) */}
      <div className={split ? `${styles.columns} ${styles.split}` : styles.columns}>
        <div className={styles.column}>
          <DescriptionCard guild={guild} isLeader={isLeader} />
          <MembersCard guild={guild} ownPlayerId={ownPlayerId ?? 0} isLeader={isLeader} />
          {own !== undefined && bannerOn && <GuildBannerCard />}
        </div>
        {split && (
          <div className={styles.column}>
            {isLeader && <InvitePlayerCard guild={guild} />}
            {own !== undefined && <EnemiesCard isLeader={isLeader} />}
            {own !== undefined && <LeaveCard guild={guild} isLeader={isLeader} />}
            {children}
          </div>
        )}
      </div>
    </div>
  );
}

/** Leave (a member), or disband (the leader) – both ask first. */
function LeaveCard({ guild, isLeader }: { guild: Guild; isLeader: boolean }) {
  const { t } = useI18n();
  const labels = t.guild;
  const leave = useLeaveGuild();
  const disband = useDisbandGuild();
  const [asking, setAsking] = useState<'leave' | 'disband' | null>(null);
  const alone = guild.members.length === 1;
  // The leader of a guild with other members must hand the lead over first (the server refuses
  // the leave otherwise); disbanding is always open to him.
  const mustHandOver = isLeader && !alone;

  function close() {
    setAsking(null);
    leave.reset();
    disband.reset();
  }

  return (
    <Card title={mustHandOver ? labels.leader.disband : labels.leave}>
      <div className={styles.body}>
        {mustHandOver && <p className={styles.hint}>{labels.leaderMustHandOver}</p>}
        <div className={styles.buttons}>
          {!mustHandOver && (
            <Button type="button" variant="danger" onClick={() => setAsking('leave')}>
              {labels.leave}
            </Button>
          )}
          {isLeader && !alone && (
            <Button type="button" variant="danger" onClick={() => setAsking('disband')}>
              {labels.leader.disband}
            </Button>
          )}
        </div>
      </div>
      <ConfirmSheet
        open={asking === 'leave'}
        title={labels.leaveTitle}
        text={alone ? labels.leaveLastText(guild.name) : labels.leaveText(guild.name)}
        confirmLabel={labels.leave}
        danger
        pending={leave.isPending}
        error={leave.error}
        onClose={close}
        onConfirm={() => leave.mutate(undefined, { onSuccess: close })}
      />
      <ConfirmSheet
        open={asking === 'disband'}
        title={labels.leader.disbandTitle}
        text={labels.leader.disbandText(guild.name)}
        confirmLabel={labels.leader.disband}
        danger
        pending={disband.isPending}
        error={disband.error}
        onClose={close}
        onConfirm={() => disband.mutate(undefined, { onSuccess: close })}
      />
    </Card>
  );
}

import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import { useDeletePlayer } from '../../api/queries/admin';
import { useCurrentAccount } from '../../api/queries/auth';
import { useInvitePlayer, useMyGuild } from '../../api/queries/guilds';
import { usePlayerProfile, type PlayerProfile } from '../../api/queries/ranking';
import { useWorld } from '../../api/queries/world';
import { AchievementBadge } from '../../components/AchievementBadge';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import { sortEarned } from '../../game/achievements';
import { formatCoords, formatDateTime, formatNumber } from '../../game/format';
import { bracketTag, profileInvite } from '../../game/guilds';
import { useI18n } from '../../i18n';
import { ActionsStayOn } from '../../layout/ActionsStayOn';
import { useCurrentVillageId } from '../../layout/useCurrentVillageId';
import { achievementName, levelName } from '../achievements/achievementText';
import { conversationPath } from '../messages/messagePaths';
import { mapPath } from '../rally/rallyPaths';
import { guildPath } from '../guild/guildPaths';
import { HistoryCard, StatisticsCard } from './PlayerStatistics';
import { parsePlayerId, rankingPath } from './rankingPaths';
import styles from './PlayerPage.module.css';

/**
 * /players/:id – a player's profile: rank, points, villages, statistics and their history (1.5),
 * achievements (04-api *World & player*).
 */
export function PlayerPage() {
  const playerId = parsePlayerId(useParams().playerId);
  if (playerId === undefined) {
    return <ProfileError error={new ApiError(404, 'PLAYER_NOT_FOUND', 'Not found')} />;
  }
  return <Profile playerId={playerId} />;
}

function Profile({ playerId }: { playerId: number }) {
  const { t } = useI18n();
  const profile = usePlayerProfile(playerId);
  if (profile.isError) {
    return <ProfileError error={profile.error} />;
  }
  if (profile.isPending) {
    return <p>{t.common.loading}</p>;
  }
  return <ProfileView profile={profile.data} />;
}

function ProfileError({ error }: { error: Error }) {
  const { t } = useI18n();
  return (
    <div className={styles.page}>
      <ToRanking />
      <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
    </div>
  );
}

function ToRanking() {
  const { t } = useI18n();
  return <BackLink to={rankingPath(0)}>{t.profile.back}</BackLink>;
}

function ProfileView({ profile }: { profile: PlayerProfile }) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const currentVillageId = useCurrentVillageId();
  const isMe = profile.id === me.playerId;
  const navigate = useNavigate();

  return (
    <div className={styles.page}>
      <ToRanking />
      <header className={styles.head}>
        <h1>
          {profile.name}
          {isMe && <span className={styles.tag}>{t.map.youTag}</span>}
          {profile.npc && <span className={styles.tag}>{t.map.npcTag}</span>}
        </h1>
        <p className={styles.facts}>
          {t.profile.rank(profile.rank)} · {t.profile.points(formatNumber(profile.points, language))}
        </p>
        {profile.guild != null && (
          <p className={styles.facts}>
            {t.profile.guild}:{' '}
            <Link to={guildPath(profile.guild.id)} className={styles.guildLink}>
              {bracketTag(profile.guild.tag)} {profile.guild.name}
            </Link>
          </p>
        )}
        {profile.npc && <p className={styles.hint}>{t.profile.npcHint}</p>}
        {/* on the account server, which asks for its own login (1.5) */}
        {profile.careerUrl != null && (
          <p className={styles.facts}>
            <a href={profile.careerUrl} target="_blank" rel="noopener noreferrer" className={styles.careerLink}>
              {t.profile.career}
            </a>
          </p>
        )}
        {/* NPC lords don't read mail */}
        {!isMe && !profile.npc && (
          <div className={styles.actions}>
            {/* messages stay open in an ended world (v1.3) */}
            <ActionsStayOn>
              <Button type="button" onClick={() => void navigate(conversationPath(profile.id))}>
                {t.messages.write}
              </Button>
            </ActionsStayOn>
            {/* keyed: an error or a pending request belongs to this player, not to the next profile */}
            <InviteToGuild key={profile.id} profile={profile} />
          </div>
        )}
      </header>
      <Card title={t.profile.villages}>
        {profile.villages.length === 0 ? (
          <p className={styles.empty}>{t.profile.noVillages}</p>
        ) : (
          <ul className={styles.villages}>
            {profile.villages.map((village) => (
              <li key={village.id} className={styles.village}>
                <span className={styles.villageName}>{village.name}</span>
                <span className={styles.villageMeta}>
                  {currentVillageId === undefined ? (
                    formatCoords(village.x, village.y)
                  ) : (
                    <Link
                      to={mapPath(currentVillageId, village)}
                      aria-label={`${t.profile.onMap}: ${formatCoords(village.x, village.y)}`}
                    >
                      {formatCoords(village.x, village.y)}
                    </Link>
                  )}{' '}
                  · {formatNumber(village.points, language)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <StatisticsCard stats={profile.stats} />
      <HistoryCard key={profile.id} playerId={profile.id} name={profile.name} />
      {!profile.npc && <EarnedAchievements profile={profile} isMe={isMe} />}
      {me.role === 'ADMIN' && !isMe && <DeletePlayer profile={profile} />}
    </div>
  );
}

/**
 * Guild leaders can invite the player straight from the profile, through the same request as the
 * name field on the guild page (and so the same checks on the server).
 */
function InviteToGuild({ profile }: { profile: PlayerProfile }) {
  const { t } = useI18n();
  const me = useCurrentAccount();
  const world = useWorld();
  const mine = useMyGuild();
  const invite = useInvitePlayer();
  const state = profileInvite({
    guildsEnabled: world.data?.guildsEnabled === true,
    mine: mine.data,
    maxMembers: world.data?.guildMaxMembers ?? 0,
    viewerPlayerId: me.playerId,
    profile,
  });

  // An error (say, the player joined another guild meanwhile) stays visible after the refresh hides
  // the button, so it doesn't vanish without a word.
  return (
    <>
      {state === 'invite' && (
        <Button
          type="button"
          variant="secondary"
          disabled={invite.isPending}
          onClick={() => invite.mutate(profile.name)}
        >
          {t.profile.invite}
        </Button>
      )}
      {state === 'invited' && <p className={styles.hint}>{t.profile.invited}</p>}
      {state === 'full' && <p className={styles.hint}>{t.profile.inviteFull}</p>}
      {invite.isError && <ErrorMessage>{errorMessage(t, invite.error)}</ErrorMessage>}
    </>
  );
}

/** The achievements the player has earned: public (02-game-rules §19). Only his own page links to the progress. */
function EarnedAchievements({ profile, isMe }: { profile: PlayerProfile; isMe: boolean }) {
  const { t, language } = useI18n();
  const earned = sortEarned(profile.achievements);
  return (
    <Card title={t.achievements.profileTitle}>
      {earned.length === 0 ? (
        <p className={styles.empty}>{t.achievements.profileEmpty}</p>
      ) : (
        <ul className={styles.badges}>
          {earned.map((achievement) => {
            const name = achievementName(t, achievement.key);
            // a one-time achievement is level 1 too, but has no level to name
            const levelText = achievement.leveled ? levelName(t, achievement.level) : null;
            const label = t.achievements.badgeLabel(name, levelText);
            return (
              <li
                key={achievement.key}
                className={styles.badgeItem}
                title={`${label} · ${t.achievements.earnedOn(formatDateTime(Date.parse(achievement.earnedAt), language))}`}
              >
                <AchievementBadge badge={achievement} size={48} />
                <span className={styles.badgeName}>
                  {name}
                  {levelText !== null && <span className={styles.badgeLevel}> {levelText}</span>}
                </span>
              </li>
            );
          })}
        </ul>
      )}
      {isMe && (
        <p className={styles.more}>
          <Link to="/achievements">{t.achievements.viewAll}</Link>
        </p>
      )}
    </Card>
  );
}

/** Admin only: deleting asks first in a sheet; afterwards the profile no longer exists, so back to the ranking. */
function DeletePlayer({ profile }: { profile: PlayerProfile }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const remove = useDeletePlayer();
  const [open, setOpen] = useState(false);

  // Admin endpoints stay open in an ended world.
  return (
    <ActionsStayOn>
      <div>
        <Button type="button" variant="danger" onClick={() => setOpen(true)}>
          {t.profile.admin.delete}
        </Button>
      </div>
      <Sheet open={open} onClose={() => setOpen(false)} title={t.profile.admin.title}>
        <div className={styles.confirm}>
          <p>{t.profile.admin.text(profile.name)}</p>
          {remove.isError && <ErrorMessage>{errorMessage(t, remove.error)}</ErrorMessage>}
          <div className={styles.confirmButtons}>
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              {t.common.cancel}
            </Button>
            <Button
              type="button"
              variant="danger"
              disabled={remove.isPending}
              onClick={() =>
                remove.mutate(profile.id, {
                  onSuccess: () => {
                    setOpen(false);
                    void navigate(rankingPath(0), { replace: true });
                  },
                })
              }
            >
              {t.profile.admin.confirm}
            </Button>
          </div>
        </div>
      </Sheet>
    </ActionsStayOn>
  );
}

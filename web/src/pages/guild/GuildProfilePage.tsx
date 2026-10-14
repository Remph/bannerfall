import { Link, Navigate, useParams } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useGuild } from '../../api/queries/guilds';
import { useWorld } from '../../api/queries/world';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { useI18n } from '../../i18n';
import styles from './Guild.module.css';
import { GuildView } from './GuildView';
import { OWN_GUILD_PATH, parseGuildId } from './guildPaths';

/** /guilds/:id – any guild, read-only; the viewer's own guild is the /guild page. */
export function GuildProfilePage() {
  const guildId = parseGuildId(useParams().guildId);
  if (guildId === undefined) {
    return <GuildError error={new ApiError(404, 'GUILD_NOT_FOUND', 'Not found')} />;
  }
  return <Profile guildId={guildId} />;
}

function Profile({ guildId }: { guildId: number }) {
  const { t } = useI18n();
  const me = useCurrentAccount();
  const world = useWorld();
  const guild = useGuild(guildId);
  if (guild.isError) {
    return <GuildError error={guild.error} />;
  }
  if (guild.data === undefined || world.data === undefined) {
    return <p>{t.common.loading}</p>;
  }
  // The viewer's own guild has its tools on /guild.
  if (guild.data.members.some((member) => member.playerId === me.playerId)) {
    return <Navigate to={OWN_GUILD_PATH} replace />;
  }
  return <GuildView guild={guild.data} ownPlayerId={null} maxMembers={world.data.guildMaxMembers} back />;
}

function GuildError({ error }: { error: Error }) {
  const { t } = useI18n();
  return (
    <div className={styles.page}>
      <Link to={OWN_GUILD_PATH} className={styles.back}>
        <Icon name="chevronLeft" /> {t.guild.back}
      </Link>
      <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
    </div>
  );
}

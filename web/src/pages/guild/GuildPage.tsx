import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useMyGuild } from '../../api/queries/guilds';
import { useWorld } from '../../api/queries/world';
import { ActionsStayOn } from '../../layout/ActionsStayOn';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { useI18n } from '../../i18n';
import { CreateGuildCard } from './CreateGuildCard';
import { InvitationsCard } from './InvitationsCard';
import styles from './Guild.module.css';
import { GuildListCard } from './GuildListCard';
import { GuildView } from './GuildView';

/**
 * /guild – the player's own guild, or the way into one: found it, accept an invitation, look at the
 * others (02-game-rules §16). Also works in the pre-game.
 */
export function GuildPage() {
  const { t } = useI18n();
  const me = useCurrentAccount();
  const world = useWorld();
  const mine = useMyGuild();

  if (mine.isError || world.isError) {
    return (
      <div className={styles.page}>
        <ErrorMessage>{errorMessage(t, mine.error ?? world.error)}</ErrorMessage>
        {/* reading again changes nothing, so it stays on in an ended world (v1.3) */}
        <ActionsStayOn>
          <div>
            <Button
              type="button"
              onClick={() => {
                void mine.refetch();
                void world.refetch();
              }}
            >
              {t.common.retry}
            </Button>
          </div>
        </ActionsStayOn>
      </div>
    );
  }
  if (mine.data === undefined || world.data === undefined) {
    return <p>{t.common.loading}</p>;
  }

  const { guild, invitations } = mine.data;
  if (guild !== null && guild !== undefined) {
    return (
      <GuildView guild={guild} ownPlayerId={me.playerId} maxMembers={world.data.guildMaxMembers}>
        <GuildListCard />
      </GuildView>
    );
  }
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.guild.title}</h1>
        <p className={styles.hint}>{world.data.guildsEnabled ? t.guild.explainer : t.guild.disabled}</p>
      </header>
      {world.data.guildsEnabled && <InvitationsCard invitations={invitations} />}
      {world.data.guildsEnabled && <CreateGuildCard />}
      <GuildListCard />
    </div>
  );
}

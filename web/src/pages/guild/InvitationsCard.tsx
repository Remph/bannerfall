import { errorMessage } from '../../api/errors';
import { useAcceptInvitation, useDeclineInvitation, type GuildInvitation } from '../../api/queries/guilds';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { bracketTag } from '../../game/guilds';
import { useI18n } from '../../i18n';
import { Link } from 'react-router';
import styles from './Guild.module.css';
import { guildPath } from './guildPaths';

/** The invitations the player has received: join or decline. */
export function InvitationsCard({ invitations }: { invitations: GuildInvitation[] }) {
  const { t } = useI18n();
  const accept = useAcceptInvitation();
  const decline = useDeclineInvitation();
  const busy = accept.isPending || decline.isPending;
  const error = accept.error ?? decline.error;

  return (
    <Card title={t.guild.invitations.title}>
      {invitations.length === 0 ? (
        <p className={`${styles.hint} ${styles.listEmpty}`}>{t.guild.invitations.none}</p>
      ) : (
        <ul className={styles.list}>
          {invitations.map((invitation) => (
            <li key={invitation.id} className={styles.row}>
              <span className={styles.rowMain}>
                <Link to={guildPath(invitation.guildId)} className={styles.rowName}>
                  {bracketTag(invitation.guildTag)} {invitation.guildName}
                </Link>
                <span className={styles.rowSub}>{t.guild.invitations.by(invitation.invitedByName)}</span>
              </span>
              <span className={styles.rowActions}>
                <Button
                  type="button"
                  size="small"
                  disabled={busy}
                  onClick={() => {
                    decline.reset();
                    accept.mutate(invitation.id);
                  }}
                >
                  {t.guild.invitations.accept}
                </Button>
                <Button
                  type="button"
                  size="small"
                  variant="secondary"
                  disabled={busy}
                  onClick={() => {
                    accept.reset();
                    decline.mutate(invitation.id);
                  }}
                >
                  {t.guild.invitations.decline}
                </Button>
              </span>
            </li>
          ))}
        </ul>
      )}
      {error !== null && (
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>
        </div>
      )}
    </Card>
  );
}

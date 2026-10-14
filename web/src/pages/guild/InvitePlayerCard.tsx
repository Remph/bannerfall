import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useInvitePlayer, useWithdrawInvitation, type Guild } from '../../api/queries/guilds';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { useI18n } from '../../i18n';
import styles from './Guild.module.css';

/** Leader only: invite a player by name, and see (and take back) the open invitations. */
export function InvitePlayerCard({ guild }: { guild: Guild }) {
  const { t } = useI18n();
  const labels = t.guild.leader;
  const invite = useInvitePlayer();
  const withdraw = useWithdrawInvitation();
  const [name, setName] = useState('');
  const pending = guild.invitations ?? [];

  function submit(event: FormEvent) {
    event.preventDefault();
    if (name.trim() !== '') {
      invite.mutate(name.trim(), { onSuccess: () => setName('') });
    }
  }

  return (
    <Card title={labels.inviteTitle}>
      <form className={`${styles.body} ${styles.form}`} onSubmit={submit}>
        <TextField
          label={labels.inviteName}
          value={name}
          maxLength={64}
          autoComplete="off"
          autoCapitalize="off"
          onChange={(event) => {
            setName(event.target.value);
            invite.reset();
          }}
        />
        {invite.isError && <ErrorMessage>{errorMessage(t, invite.error)}</ErrorMessage>}
        <div>
          <Button type="submit" disabled={name.trim() === '' || invite.isPending}>
            {labels.invite}
          </Button>
        </div>
      </form>
      <h3 className={styles.subTitle}>{labels.pendingTitle}</h3>
      {pending.length === 0 ? (
        <p className={`${styles.hint} ${styles.listEmpty}`}>{labels.pendingNone}</p>
      ) : (
        <ul className={styles.list}>
          {pending.map((invitation) => (
            <li key={invitation.id} className={styles.row}>
              <span className={styles.rowMain}>
                <span className={styles.rowName}>{invitation.playerName}</span>
                <span className={styles.rowSub}>{labels.pendingBy(invitation.invitedByName)}</span>
              </span>
              <span className={styles.rowActions}>
                <Button
                  type="button"
                  size="small"
                  variant="secondary"
                  disabled={withdraw.isPending}
                  onClick={() => withdraw.mutate(invitation.id)}
                >
                  {labels.withdraw}
                </Button>
              </span>
            </li>
          ))}
        </ul>
      )}
      {withdraw.isError && (
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, withdraw.error)}</ErrorMessage>
        </div>
      )}
    </Card>
  );
}

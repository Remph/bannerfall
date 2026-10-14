import { useState } from 'react';
import { Link } from 'react-router';
import { useHandLeadOver, useKickMember, type Guild, type GuildMember } from '../../api/queries/guilds';
import { Button } from '../../components/Button';
import { useWorld } from '../../api/queries/world';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import { playerPath } from '../ranking/rankingPaths';
import styles from './Guild.module.css';

type Action = { kind: 'kick' | 'lead'; member: GuildMember };

interface MembersCardProps {
  guild: Guild;
  /** The viewer's player id, to mark him and to keep the leader's buttons off his own line. */
  ownPlayerId: number;
  /** The viewer leads this guild: he can remove members and hand the lead over. */
  isLeader: boolean;
}

/** The members, leader first. The leader of the viewer's own guild gets "Make leader" and "Remove". */
export function MembersCard({ guild, ownPlayerId, isLeader }: MembersCardProps) {
  const { t, language } = useI18n();
  const labels = t.guild.leader;
  // profiles are closed before the opening (no ranking yet), so the names are plain text then
  const pregame = useWorld().data?.phase === 'PREGAME';
  const kick = useKickMember();
  const handOver = useHandLeadOver();
  const [action, setAction] = useState<Action | null>(null);

  function close() {
    setAction(null);
    kick.reset();
    handOver.reset();
  }

  const mutation = action?.kind === 'kick' ? kick : handOver;
  const nameWithPills = (member: GuildMember) => (
    <>
      {member.name}
      {member.playerId === ownPlayerId && <span className={styles.pill}>{t.map.youTag}</span>}
      {member.role === 'LEADER' && <span className={styles.pill}>{t.guild.roles.LEADER}</span>}
    </>
  );
  return (
    <Card title={t.guild.members}>
      <ul className={styles.list}>
        {guild.members.map((member) => (
          <li key={member.playerId} className={styles.row}>
            <span className={styles.rowMain}>
              {pregame ? (
                <span className={styles.rowName}>{nameWithPills(member)}</span>
              ) : (
                <Link to={playerPath(member.playerId)} className={styles.rowName}>
                  {nameWithPills(member)}
                </Link>
              )}
              <span className={styles.rowSub}>
                {t.guild.memberLine(member.villageCount, t.guild.points(formatNumber(member.points, language)))}
              </span>
            </span>
            {isLeader && member.playerId !== ownPlayerId && (
              <span className={styles.rowActions}>
                <Button
                  type="button"
                  size="small"
                  variant="secondary"
                  onClick={() => setAction({ kind: 'lead', member })}
                >
                  {labels.handOver}
                </Button>
                <Button type="button" size="small" variant="danger" onClick={() => setAction({ kind: 'kick', member })}>
                  {labels.kick}
                </Button>
              </span>
            )}
          </li>
        ))}
      </ul>
      <ConfirmSheet
        open={action !== null}
        title={action?.kind === 'kick' ? labels.kickTitle : labels.handOverTitle}
        text={
          action === null
            ? ''
            : action.kind === 'kick'
              ? labels.kickText(action.member.name)
              : labels.handOverText(action.member.name)
        }
        confirmLabel={action?.kind === 'kick' ? labels.kick : labels.handOver}
        danger={action?.kind === 'kick'}
        pending={mutation.isPending}
        error={mutation.error}
        onClose={close}
        onConfirm={() => {
          if (action !== null) {
            mutation.mutate(action.member.playerId, { onSuccess: close });
          }
        }}
      />
    </Card>
  );
}

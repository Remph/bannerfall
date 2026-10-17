import { useState } from 'react';
import { Link } from 'react-router';
import { useHandLeadOver, useKickMember, type Guild, type GuildMember } from '../../api/queries/guilds';
import { useWorld } from '../../api/queries/world';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { MoreMenu } from '../../components/MoreMenu';
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

/**
 * The members, leader first. The leader of the viewer's own guild gets "Make leader" and "Remove"
 * behind a "…" on each other member's line (1.3); both ask first.
 */
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
      {member.npc && <span className={styles.pill}>{t.map.npcTag}</span>}
      {member.role === 'LEADER' && <span className={styles.pill}>{t.guild.roles.LEADER}</span>}
    </>
  );
  return (
    <Card title={t.guild.members}>
      <ul className={styles.list}>
        {guild.members.map((member) => (
          <li key={member.playerId} className={`${styles.row} ${styles.memberRow}`}>
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
              <MoreMenu
                label={labels.more(member.name)}
                items={[
                  { label: labels.handOver, icon: 'guild', onSelect: () => setAction({ kind: 'lead', member }) },
                  {
                    label: labels.kick,
                    icon: 'close',
                    danger: true,
                    onSelect: () => setAction({ kind: 'kick', member }),
                  },
                ]}
              />
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

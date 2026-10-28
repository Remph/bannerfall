import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useEnemies, useUnmarkEnemy, type EnemyKind } from '../../api/queries/guilds';
import { useWorld } from '../../api/queries/world';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { IconButton } from '../../components/IconButton';
import { formatCoords } from '../../game/format';
import { bracketTag } from '../../game/guilds';
import { useI18n } from '../../i18n';
import { useCurrentVillageId } from '../../layout/useCurrentVillageId';
import { mapPath } from '../rally/rallyPaths';
import { playerPath } from '../ranking/rankingPaths';
import styles from './Guild.module.css';
import { guildPath } from './guildPaths';

/**
 * The enemies the leader has marked (1.7, 02-game-rules §16.3), for every member to read. The
 * leader takes a mark away here; marking happens on the enemy's guild, profile or map panel.
 */
export function EnemiesCard({ isLeader }: { isLeader: boolean }) {
  const { t } = useI18n();
  const labels = t.guild.enemies;
  const enemies = useEnemies();
  const unmark = useUnmarkEnemy();
  const currentVillageId = useCurrentVillageId();
  // profiles are closed before the opening (no ranking yet), so the names are plain text then
  const pregame = useWorld().data?.phase === 'PREGAME';
  const list = enemies.data;
  if (enemies.isError) {
    return (
      <Card title={labels.title}>
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, enemies.error)}</ErrorMessage>
        </div>
      </Card>
    );
  }
  if (list === undefined) {
    return null;
  }

  const remove = (kind: EnemyKind, targetId: number, name: string) => (
    <>
      {isLeader && (
        <IconButton
          icon="close"
          variant="danger"
          label={labels.remove(name)}
          disabled={unmark.isPending}
          onClick={() => unmark.mutate({ kind, targetId })}
        />
      )}
    </>
  );
  const named = (name: string, to: string | null) =>
    to === null || pregame ? (
      <span className={styles.rowName}>{name}</span>
    ) : (
      <Link to={to} className={styles.rowName}>
        {name}
      </Link>
    );

  const empty = list.guilds.length + list.players.length + list.villages.length === 0;
  return (
    <Card title={labels.title}>
      <div className={styles.body}>
        <p className={styles.hint}>{labels.hint}</p>
        {unmark.isError && <ErrorMessage>{errorMessage(t, unmark.error)}</ErrorMessage>}
      </div>
      {empty ? (
        <p className={`${styles.hint} ${styles.listEmpty}`}>{isLeader ? labels.noneLeader : labels.none}</p>
      ) : (
        <>
          {list.guilds.length > 0 && <h3 className={styles.subTitle}>{labels.guilds}</h3>}
          <ul className={styles.list}>
            {list.guilds.map((guild) => (
              <li key={`g${guild.id}`} className={`${styles.row} ${styles.memberRow}`}>
                <span className={styles.rowMain}>
                  {named(`${bracketTag(guild.tag)} ${guild.name}`, guildPath(guild.id))}
                </span>
                {remove('GUILD', guild.id, guild.name)}
              </li>
            ))}
          </ul>
          {list.players.length > 0 && <h3 className={styles.subTitle}>{labels.players}</h3>}
          <ul className={styles.list}>
            {list.players.map((player) => (
              <li key={`p${player.id}`} className={`${styles.row} ${styles.memberRow}`}>
                <span className={styles.rowMain}>
                  {named(
                    `${player.guildTag != null ? `${bracketTag(player.guildTag)} ` : ''}${player.name}`,
                    playerPath(player.id),
                  )}
                  {player.npc && <span className={styles.rowSub}>{t.map.npcTag}</span>}
                </span>
                {remove('PLAYER', player.id, player.name)}
              </li>
            ))}
          </ul>
          {list.villages.length > 0 && <h3 className={styles.subTitle}>{labels.villages}</h3>}
          <ul className={styles.list}>
            {list.villages.map((village) => (
              <li key={`v${village.id}`} className={`${styles.row} ${styles.memberRow}`}>
                <span className={styles.rowMain}>
                  {named(
                    `${village.name} ${formatCoords(village.x, village.y)}`,
                    currentVillageId === undefined ? null : mapPath(currentVillageId, village),
                  )}
                  <span className={styles.rowSub}>{labels.ownedBy(village.ownerName)}</span>
                </span>
                {remove('VILLAGE', village.id, village.name)}
              </li>
            ))}
          </ul>
        </>
      )}
      <p className={`${styles.hint} ${styles.listEmpty}`}>
        {labels.count(list.guilds.length + list.players.length + list.villages.length, list.max)}
      </p>
    </Card>
  );
}

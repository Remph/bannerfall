import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useGuildList } from '../../api/queries/guilds';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatNumber } from '../../game/format';
import { bracketTag } from '../../game/guilds';
import { useI18n } from '../../i18n';
import styles from './Guild.module.css';
import { guildPath } from './guildPaths';

/** Every guild of the world, strongest first. */
export function GuildListCard() {
  const { t, language } = useI18n();
  const guilds = useGuildList();
  return (
    <Card title={t.guild.allGuilds}>
      {guilds.isError && (
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, guilds.error)}</ErrorMessage>
        </div>
      )}
      {guilds.isPending && <p className={`${styles.hint} ${styles.listEmpty}`}>{t.common.loading}</p>}
      {guilds.data?.length === 0 && <p className={`${styles.hint} ${styles.listEmpty}`}>{t.guild.noGuilds}</p>}
      {guilds.data !== undefined && guilds.data.length > 0 && (
        <ul className={styles.list}>
          {guilds.data.map((guild) => (
            <li key={guild.id} className={styles.row}>
              <span className={styles.rowMain}>
                <Link to={guildPath(guild.id)} className={styles.rowName}>
                  {bracketTag(guild.tag)} {guild.name}
                </Link>
                <span className={styles.rowSub}>
                  {t.guild.memberCount(guild.memberCount)} · {t.guild.points(formatNumber(guild.points, language))}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

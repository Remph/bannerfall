import { useI18n } from '../../i18n';
import styles from './EnemyPill.module.css';

/** The red "Enemy" pill next to a name or village of an enemy of the viewer's guild (1.7). Red is the war colour. */
export function EnemyPill() {
  const { t } = useI18n();
  return <span className={styles.pill}>{t.guild.enemies.pill}</span>;
}

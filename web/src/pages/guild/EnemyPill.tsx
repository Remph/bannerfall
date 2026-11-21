import { useI18n } from '../../i18n';
import { NoteGap } from '../../components/NameNote';
import styles from './EnemyPill.module.css';

/**
 * The red "Enemy" pill next to a name or village of an enemy of the viewer's guild (1.7). Red is the war colour.
 * `inText`: after a name in running text that may wrap, the gap is a space, so a wrapped pill starts flush (2.1).
 */
export function EnemyPill({ inText = false }: { inText?: boolean }) {
  const { t } = useI18n();
  if (inText) {
    return (
      <>
        <NoteGap />
        <span className={`${styles.pill} ${styles.inText}`}>{t.guild.enemies.pill}</span>
      </>
    );
  }
  return <span className={styles.pill}>{t.guild.enemies.pill}</span>;
}

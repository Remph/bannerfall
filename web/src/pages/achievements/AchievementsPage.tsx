import { errorMessage } from '../../api/errors';
import { useAchievements, type Achievement } from '../../api/queries/achievements';
import { AchievementBadge } from '../../components/AchievementBadge';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { achievementCategories, nextThreshold, progressPercent } from '../../game/achievements';
import { formatDateTime, formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import { achievementDescription, achievementName, levelName } from './achievementText';
import styles from './AchievementsPage.module.css';

/** /achievements – all achievements with the player's own progress (02-game-rules §19). */
export function AchievementsPage() {
  const { t } = useI18n();
  const achievements = useAchievements();
  if (achievements.isError) {
    return <ErrorMessage>{errorMessage(t, achievements.error)}</ErrorMessage>;
  }
  if (achievements.isPending) {
    return <p>{t.common.loading}</p>;
  }
  const all = achievements.data.achievements;
  const earned = all.filter((achievement) => achievement.level > 0).length;
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{t.achievements.title}</h1>
        <p className={styles.intro}>{t.achievements.intro}</p>
        <p className={styles.count}>{t.achievements.earnedCount(earned, all.length)}</p>
      </header>
      {achievementCategories.map((category) => {
        const inCategory = all.filter((achievement) => achievement.category === category);
        return inCategory.length === 0 ? null : (
          <Card key={category} title={t.achievements.categories[category]}>
            <ul className={styles.list}>
              {inCategory.map((achievement) => (
                <li key={achievement.key} className={styles.item}>
                  <AchievementLine achievement={achievement} />
                </li>
              ))}
            </ul>
          </Card>
        );
      })}
    </div>
  );
}

function AchievementLine({ achievement }: { achievement: Achievement }) {
  const { t, language } = useI18n();
  const name = achievementName(t, achievement.key);
  const description = achievementDescription(t, achievement.key);
  const level = achievement.level;
  const levelText = achievement.leveled && level > 0 ? levelName(t, level) : null;
  const statusText =
    level === 0
      ? t.achievements.locked
      : t.achievements.earnedOn(formatDateTime(Date.parse(achievement.earnedAt ?? ''), language));
  const showProgress = achievement.leveled && achievement.progress != null;

  return (
    <>
      <AchievementBadge badge={achievement} size={56} />
      <div className={styles.text}>
        <h3 className={styles.name}>
          {name}
          {levelText !== null && <span className={styles.level}>{levelText}</span>}
        </h3>
        <p className={styles.description}>{description}</p>
        <p className={styles.status}>
          <span className="sr-only">{t.achievements.badgeLabel(name, levelText)}. </span>
          {statusText}
        </p>
        {showProgress && <Progress achievement={achievement} progress={achievement.progress ?? 0} />}
      </div>
    </>
  );
}

function Progress({ achievement, progress }: { achievement: Achievement; progress: number }) {
  const { t, language } = useI18n();
  const next = nextThreshold(achievement.thresholds, achievement.level);
  const value = formatNumber(progress, language);
  const percent = progressPercent(achievement.thresholds, achievement.level, progress);
  return (
    <div className={styles.progress}>
      <div
        className={styles.bar}
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-label={t.achievements.title}
      >
        <div className={styles.fill} style={{ width: `${percent}%` }} />
      </div>
      <p className={styles.progressText}>
        {next === undefined
          ? t.achievements.progressDone(value)
          : `${t.achievements.progress(value, formatNumber(next, language))} · ${t.achievements.nextLevel(levelName(t, achievement.level + 1))}`}
      </p>
    </div>
  );
}

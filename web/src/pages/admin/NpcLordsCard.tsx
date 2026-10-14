import { errorMessage } from '../../api/errors';
import {
  npcPersonalities,
  useAdminWorldSettings,
  useNpcLords,
  useSpawnNpcLord,
  type NpcLord,
  type NpcPersonality,
} from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { formatDateTime, formatDecimal, formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './AdminPage.module.css';

/** The NPC lords of the world, and a button per personality to spawn another one. */
export function NpcLordsCard() {
  const { t, language } = useI18n();
  const lords = useNpcLords();
  const spawn = useSpawnNpcLord();
  // there is no map before the world opens (M14g): the lords are created with it
  const pregame = useAdminWorldSettings().data?.phase === 'PREGAME';

  return (
    <Card title={t.admin.lords.title}>
      <div className={styles.stack}>
        {pregame && <p className={styles.hint}>{t.admin.lords.pregame}</p>}
        <div className={styles.spawn} hidden={pregame}>
          <span>{t.admin.lords.spawn}</span>
          {npcPersonalities.map((personality: NpcPersonality) => (
            <Button
              key={personality}
              type="button"
              variant="secondary"
              size="small"
              disabled={spawn.isPending}
              onClick={() => spawn.mutate(personality)}
            >
              {t.admin.lords.personalities[personality]}
            </Button>
          ))}
        </div>
        {spawn.isError && <ErrorMessage>{errorMessage(t, spawn.error)}</ErrorMessage>}
        {lords.isError && <ErrorMessage>{errorMessage(t, lords.error)}</ErrorMessage>}
        {lords.isPending && <p>{t.common.loading}</p>}
        {!pregame && lords.data?.length === 0 && <p className={styles.hint}>{t.admin.lords.empty}</p>}
        <ul className={styles.lords}>
          {lords.data?.map((lord: NpcLord) => (
            <li key={lord.playerId} className={styles.lord}>
              <div className={styles.lordHead}>
                <strong>{lord.name}</strong>
                <span>{t.admin.lords.personalities[lord.personality]}</span>
              </div>
              <p className={styles.meta}>
                {t.admin.lords.points(formatNumber(lord.points, language))} ·{' '}
                {t.admin.lords.villages(lord.villageCount)} ·{' '}
                {t.admin.lords.factor(formatDecimal(lord.productionFactor, language))}
                {lord.braked && ` · ${t.admin.lords.braked}`}
                {lord.boosted && ` · ${t.admin.lords.boosted}`}
              </p>
              <p className={styles.meta}>
                {lord.nextThinkAt == null
                  ? t.admin.lords.outOfTheGame
                  : t.admin.lords.nextRound(formatDateTime(Date.parse(lord.nextThinkAt), language))}
                {lord.lastThinkAt != null &&
                  ` · ${t.admin.lords.lastRound(formatDateTime(Date.parse(lord.lastThinkAt), language))}`}
              </p>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  );
}

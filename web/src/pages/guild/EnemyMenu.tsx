import { errorMessage } from '../../api/errors';
import { ErrorMessage } from '../../components/ErrorMessage';
import { MoreMenu } from '../../components/MoreMenu';
import { useI18n } from '../../i18n';
import { useEnemyItems, type EnemyTarget } from './useEnemyItems';

/**
 * The leader's "…" with "Mark … as enemy" / "Remove the enemy mark from …" for guilds, players and
 * villages (1.7, 02-game-rules §16.3), one item per target (the map panel offers the village, its
 * owner and his guild). Nothing for everybody else, and nothing for targets the server would refuse
 * (the own guild, guild mates, barbarian villages).
 */
export function EnemyMenu({ targets, label }: { targets: readonly EnemyTarget[]; label: string }) {
  const { t } = useI18n();
  const { items, error } = useEnemyItems(targets);
  return (
    <>
      {items.length > 0 && <MoreMenu label={label} items={items} />}
      {error !== null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
    </>
  );
}

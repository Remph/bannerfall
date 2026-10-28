import { useEnemies, useMarkEnemy, useMyGuild, useUnmarkEnemy, type EnemyKind } from '../../api/queries/guilds';
import { useWorld } from '../../api/queries/world';
import type { MoreMenuItem } from '../../components/MoreMenu';
import { enemyAction } from '../../game/enemies';
import { useI18n } from '../../i18n';
import { useActionsFrozen } from '../../layout/worldEnd';

export interface EnemyTarget {
  kind: EnemyKind;
  targetId: number;
  /** What the target is called, for the item's text ("Mark Tomburg as enemy"). */
  name: string;
  /** A village's owner (null for a barbarian village): his guild mates' villages can't be marked. */
  ownerId?: number | null;
}

/**
 * The "…" menu items for the leader's enemy marks (see `EnemyMenu`), and the error of the last
 * request, for a place that lays the menu and the message out on its own (the map panel's header).
 */
export function useEnemyItems(targets: readonly EnemyTarget[]): { items: MoreMenuItem[]; error: Error | null } {
  const { t } = useI18n();
  const world = useWorld();
  const mine = useMyGuild();
  const enemies = useEnemies().data;
  const mark = useMarkEnemy();
  const unmark = useUnmarkEnemy();
  const frozen = useActionsFrozen();
  const labels = t.guild.enemies;

  const items: MoreMenuItem[] = [];
  for (const { kind, targetId, name, ownerId } of targets) {
    const marked =
      enemies !== undefined &&
      (kind === 'GUILD' ? enemies.guilds : kind === 'PLAYER' ? enemies.players : enemies.villages).some(
        (enemy) => enemy.id === targetId,
      );
    const action = enemyAction({
      guildsEnabled: world.data?.guildsEnabled === true,
      mine: mine.data,
      target: kind === 'VILLAGE' ? { kind, id: targetId, ownerId } : { kind, id: targetId },
      marked,
    });
    if (action === 'mark') {
      items.push({
        label: labels.mark(name),
        icon: 'troops',
        disabled: frozen || mark.isPending,
        onSelect: () => mark.mutate({ kind, targetId }),
      });
    } else if (action === 'unmark') {
      items.push({
        label: labels.remove(name),
        icon: 'close',
        disabled: frozen || unmark.isPending,
        onSelect: () => unmark.mutate({ kind, targetId }),
      });
    }
  }
  return { items, error: mark.error ?? unmark.error };
}

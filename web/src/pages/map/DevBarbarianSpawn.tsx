import { errorMessage } from '../../api/errors';
import { useDevSpawnBarbarian } from '../../api/queries/dev';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import type { Coords } from '../../game/coords';
import { formatCoords } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './MapPage.module.css';

/**
 * Dev cheat on the map (only rendered in `pnpm dev`): puts a barbarian village on the empty field
 * that was jumped to with "Go to". Once the map has reloaded, the new village is selected like any
 * jumped-to village.
 */
export function DevBarbarianSpawn({ field }: { field: Coords }) {
  const { t } = useI18n();
  const spawn = useDevSpawnBarbarian();
  return (
    <div className={styles.devSpawn}>
      <Button
        type="button"
        variant="secondary"
        size="small"
        disabled={spawn.isPending}
        onClick={() => spawn.mutate({ x: field.x, y: field.y })}
      >
        {t.dev.spawnBarbarian(formatCoords(field.x, field.y))}
      </Button>
      {spawn.error !== null && <ErrorMessage>{errorMessage(t, spawn.error)}</ErrorMessage>}
    </div>
  );
}

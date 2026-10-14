import { useWorld } from '../api/queries/world';
import { versionLabel } from '../game/version';
import { useT } from '../i18n';

/**
 * The build the server runs, e.g. "v1.1.0", next to the server time (M28). Players name it in bug
 * reports and see at a glance that the realm was updated. Nothing while the world data loads or if
 * the server doesn't know its version.
 */
export function VersionTag() {
  const t = useT();
  const world = useWorld();
  const label = versionLabel(world.data?.version);
  if (label === null) {
    return null;
  }
  return (
    <span title={t.settings.version}>
      <span aria-hidden="true">· </span>
      <span className="sr-only">{t.settings.version} </span>
      {label}
    </span>
  );
}

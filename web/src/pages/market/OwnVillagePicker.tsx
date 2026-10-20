import { useState } from 'react';
import { useCurrentAccount } from '../../api/queries/auth';
import type { Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { distance, type Coords } from '../../game/coords';
import { formatCoords, formatDecimal, formatDuration } from '../../game/format';
import { travelSeconds } from '../../game/market';
import { useI18n } from '../../i18n';
import styles from './MarketPage.module.css';

/**
 * "Own villages" under the market's target field (1.5.1): a sheet with the player's other villages,
 * nearest first, each with its distance and the merchants' travel time; a tap fills the target.
 * Not shown while the player has only this one village.
 */
export function OwnVillagePicker({
  village,
  target,
  onPick,
}: {
  village: Village;
  /** The field the target field points at now: that village is marked in the list. */
  target: Coords | undefined;
  onPick: (coords: Coords) => void;
}) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const world = useWorld().data;
  const [open, setOpen] = useState(false);

  const others = me.villages
    .filter((own) => own.id !== village.id)
    .map((own) => ({ ...own, fields: distance(village, own) }))
    .sort((a, b) => a.fields - b.fields || a.name.localeCompare(b.name));
  if (others.length === 0) {
    return null;
  }

  return (
    <>
      <Button type="button" variant="secondary" size="small" className={styles.pickOwn} onClick={() => setOpen(true)}>
        <Icon name="village" />
        {t.market.ownVillages}
      </Button>
      <Sheet open={open} onClose={() => setOpen(false)} title={t.market.ownVillagesTitle}>
        <ul className={styles.ownList}>
          {others.map((own) => (
            <li key={own.id}>
              <button
                type="button"
                className={styles.ownVillage}
                aria-pressed={target !== undefined && target.x === own.x && target.y === own.y}
                onClick={() => {
                  onPick({ x: own.x, y: own.y });
                  setOpen(false);
                }}
              >
                <span className={styles.ownName}>{own.name}</span>
                <span className={styles.ownMeta}>
                  {formatCoords(own.x, own.y)} · {t.map.fields(formatDecimal(own.fields, language))}
                  {world !== undefined && <> · {formatDuration(travelSeconds(own.fields, world))}</>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </Sheet>
    </>
  );
}

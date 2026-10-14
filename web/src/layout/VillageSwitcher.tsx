import { useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { Icon } from '../components/Icon';
import { Sheet } from '../components/Sheet';
import { formatCoords, formatNumber } from '../game/format';
import { useI18n } from '../i18n';
import { villagePath } from './currentVillage';
import styles from './VillageSwitcher.module.css';

interface CurrentVillage {
  id: number;
  name: string;
  x: number;
  y: number;
}

/** Name and coordinates of the current village; opens a sheet with all own villages. */
export function VillageSwitcher({ current }: { current: CurrentVillage | undefined }) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);

  return (
    <>
      <button type="button" className={styles.trigger} onClick={() => setOpen(true)} aria-haspopup="dialog">
        {current === undefined ? (
          <span className={styles.name}>{t.village.switcherTitle}</span>
        ) : (
          <>
            <span className={styles.name}>{current.name}</span>
            <span className={styles.coords}>{formatCoords(current.x, current.y)}</span>
          </>
        )}
        <Icon name="chevronDown" className={styles.chevron} />
      </button>
      <Sheet open={open} onClose={() => setOpen(false)} title={t.village.switcherTitle}>
        <ul className={styles.list}>
          {me.villages.map((village) => (
            <li key={village.id}>
              <Link
                to={villagePath(pathname, village.id)}
                className={styles.village}
                aria-current={village.id === current?.id ? 'page' : undefined}
                onClick={() => setOpen(false)}
              >
                <span className={styles.villageName}>{village.name}</span>
                <span className={styles.villageMeta}>
                  {formatCoords(village.x, village.y)} · {t.village.points(formatNumber(village.points, language))}
                </span>
                {village.incomingAttacks > 0 && (
                  <span className={styles.villageAttacks}>{t.village.incomingAttacks(village.incomingAttacks)}</span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      </Sheet>
    </>
  );
}

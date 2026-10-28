import { useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { Icon } from '../components/Icon';
import { Sheet } from '../components/Sheet';
import { VillageTag } from '../components/VillageTag';
import { formatCoords, formatNumber } from '../game/format';
import {
  loadVillageTagFilter,
  saveVillageTagFilter,
  tagOfVillage,
  usedVillageTags,
  villagesWithTag,
} from '../game/villageTags';
import { useI18n } from '../i18n';
import { villagePath } from './currentVillage';
import styles from './VillageSwitcher.module.css';

interface CurrentVillage {
  id: number;
  name: string;
  x: number;
  y: number;
}

/**
 * Name and coordinates of the current village; opens a sheet with all own villages in the order of
 * `/api/me` (1.3: the one held longest first, tagged "Main village" once there are more). Villages
 * can carry the player's own tag (1.7); as soon as one does, chips above the list filter by tag.
 */
export function VillageSwitcher({ current }: { current: CurrentVillage | undefined }) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);
  const [chosenTag, setChosenTag] = useState(loadVillageTagFilter);
  const tags = usedVillageTags(me.villages);
  // a tag nobody uses any more (removed or conquered) falls back to "All"
  const filter = chosenTag !== null && tags.includes(chosenTag) ? chosenTag : null;
  const shown = villagesWithTag(me.villages, filter);
  const currentTag = current === undefined ? null : tagOfVillage(me.villages, current.id);

  function chooseTag(tag: string | null) {
    setChosenTag(tag);
    saveVillageTagFilter(tag);
  }

  return (
    <>
      <button type="button" className={styles.trigger} onClick={() => setOpen(true)} aria-haspopup="dialog">
        {current === undefined ? (
          <span className={styles.name}>{t.village.switcherTitle}</span>
        ) : (
          <>
            <span className={styles.name}>{current.name}</span>
            {currentTag !== null && <VillageTag tag={currentTag} onBar />}
            <span className={styles.coords}>{formatCoords(current.x, current.y)}</span>
          </>
        )}
        <Icon name="chevronDown" className={styles.chevron} />
      </button>
      <Sheet open={open} onClose={() => setOpen(false)} title={t.village.switcherTitle}>
        {tags.length > 0 && (
          <div className={styles.chips} role="group" aria-label={t.village.tagFilter}>
            <button
              type="button"
              className={styles.chip}
              aria-pressed={filter === null}
              onClick={() => chooseTag(null)}
            >
              {t.village.tagAll}
            </button>
            {tags.map((tag) => (
              <button
                key={tag}
                type="button"
                className={styles.chip}
                aria-pressed={filter === tag}
                onClick={() => chooseTag(tag)}
              >
                {tag}
              </button>
            ))}
          </div>
        )}
        <ul className={styles.list}>
          {shown.map((village) => (
            <li key={village.id}>
              <Link
                to={villagePath(pathname, village.id)}
                className={styles.village}
                aria-current={village.id === current?.id ? 'page' : undefined}
                onClick={() => setOpen(false)}
              >
                <span className={styles.villageName}>
                  {village.name}
                  {village.id === me.villages[0]?.id && me.villages.length > 1 && (
                    <span className={styles.main}>{t.village.mainVillage}</span>
                  )}
                  {village.tag !== null && (
                    <span className={styles.rowTag}>
                      <VillageTag tag={village.tag} />
                    </span>
                  )}
                </span>
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

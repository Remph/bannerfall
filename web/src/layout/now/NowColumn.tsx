import { useNow, useRefreshWhenNowEnds } from '../../api/queries/now';
import { useT } from '../../i18n';
import styles from './NowColumn.module.css';
import { NowList } from './NowList';

/**
 * From 1600 px, off the map (2.0): the Now list as a column at the right edge below the sky, always
 * there. The open chat sits below it. The page makes room for it like it does for the chat (AppLayout).
 */
export function NowColumn({ currentVillageId, withChat }: { currentVillageId: number | undefined; withChat: boolean }) {
  const t = useT();
  const now = useNow();
  useRefreshWhenNowEnds(now.data, now.dataUpdatedAt, now.errorUpdatedAt);
  return (
    <aside
      id="now-column"
      className={styles.column}
      aria-label={t.now.title}
      data-now-column
      data-with-chat={withChat || undefined}
    >
      <NowList currentVillageId={currentVillageId} />
    </aside>
  );
}

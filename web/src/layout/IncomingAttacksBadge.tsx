import { useCurrentAccount } from '../api/queries/auth';
import { Icon } from '../components/Icon';
import { badgeText } from '../game/reports';
import { useT } from '../i18n';
import { totalIncomingAttacks } from '../realtime/notifications';
import styles from './IncomingAttacksBadge.module.css';
import { useWorldEnded } from './worldEnd';

/**
 * The red counter in the top bar (05-frontend §7): attacks on the way to any of the player's
 * villages, from /api/me (refreshed by the INCOMING_ATTACK and VILLAGE_UPDATED notifications). Since
 * 2.0 it opens the Now list, which has the attacks on top (the column is focused where it is always
 * there). Nothing while no attack is coming.
 */
export function IncomingAttacksBadge({ onOpen }: { onOpen: () => void }) {
  const t = useT();
  const me = useCurrentAccount();
  const total = totalIncomingAttacks(me);
  // no attack lands in an ended world (v1.3)
  const ended = useWorldEnded();
  if (total === 0 || ended) {
    return null;
  }
  return (
    <button
      type="button"
      className={styles.badge}
      onClick={onOpen}
      aria-label={t.realtime.incomingBadge(total)}
      title={t.realtime.incomingBadge(total)}
    >
      <span className={styles.pill}>
        <Icon name="troops" />
        <span aria-hidden="true">{badgeText(total)}</span>
      </span>
    </button>
  );
}

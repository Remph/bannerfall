import { Link } from 'react-router';
import { useCurrentAccount } from '../api/queries/auth';
import { Icon } from '../components/Icon';
import { badgeText } from '../game/reports';
import { useT } from '../i18n';
import { attackedVillageId, totalIncomingAttacks } from '../realtime/notifications';
import styles from './IncomingAttacksBadge.module.css';
import { useWorldEnded } from './worldEnd';

/**
 * The red counter in the top bar (05-frontend §7): attacks on the way to any of the player's
 * villages, from /api/me (refreshed by the INCOMING_ATTACK and VILLAGE_UPDATED notifications). It
 * leads to the incoming list of the current village if that one is attacked, else of the first
 * attacked village. Nothing while no attack is coming.
 */
export function IncomingAttacksBadge({ currentVillageId }: { currentVillageId: number | undefined }) {
  const t = useT();
  const me = useCurrentAccount();
  const total = totalIncomingAttacks(me);
  const villageId = attackedVillageId(me, currentVillageId);
  // no attack lands in an ended world (v1.3)
  const ended = useWorldEnded();
  if (total === 0 || villageId === undefined || ended) {
    return null;
  }
  return (
    <Link
      className={styles.badge}
      to={{ pathname: `/v/${villageId}/rally`, hash: 'incoming' }}
      aria-label={t.realtime.incomingBadge(total)}
      title={t.realtime.incomingBadge(total)}
    >
      <span className={styles.pill}>
        <Icon name="troops" />
        <span aria-hidden="true">{badgeText(total)}</span>
      </span>
    </Link>
  );
}

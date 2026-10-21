import { Link } from 'react-router';
import { useBuildPlans, useVillageAutomation } from '../../api/queries/automation';
import { Icon } from '../../components/Icon';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import styles from './AutomationPage.module.css';
import { statusText } from './automationText';

/**
 * The Town Hall's row for the village automation (1.6): its plan, line and what it does now, or
 * from which village on it unlocks. A tap opens the automation page. Hidden in a world without it.
 */
export function AutomationRow({ villageId }: { villageId: number }) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const status = useVillageAutomation(villageId);
  const plans = useBuildPlans();
  if (!status.isSuccess || status.data.villagesNeeded === 0) {
    return null;
  }
  const plan = plans.data?.find((candidate) => candidate.letter === status.data.plan);
  const locked = status.data.state === 'LOCKED';
  const title = plan === undefined ? t.automation.title : t.automation.rowTitle(plan.letter, plan.name);
  const parts = locked
    ? [t.automation.lockedShort(status.data.villagesNeeded)]
    : [
        plan === undefined
          ? undefined
          : t.automation.rowLine(Math.min(status.data.line + 1, status.data.lineCount), status.data.lineCount),
        statusText(t, language, now, status.data),
      ];
  return (
    <Link className={styles.row} to={`/v/${villageId}/automation`} viewTransition>
      <span className={styles.rowIcon}>
        <Icon name={locked ? 'lock' : 'settings'} />
      </span>
      <span className={styles.rowText}>
        <strong>{title}</strong>
        <span className={styles.muted}>{parts.filter(Boolean).join(' · ')}</span>
      </span>
      <Icon name="chevronRight" className={styles.rowChevron} />
    </Link>
  );
}

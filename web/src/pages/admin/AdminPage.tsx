import { useSearchParams } from 'react-router';
import { useAdminStatus } from '../../api/queries/admin';
import { useT } from '../../i18n';
import styles from './AdminPage.module.css';
import { adminTabs, parseTab, visibleTabs, type AdminTab } from './adminTabs';
import { AuditCard } from './AuditCard';
import { BroadcastCard, ChatSayCard } from './BroadcastCard';
import { CheatsCard } from './CheatsCard';
import { FailedEventsCard } from './FailedEventsCard';
import { NpcDifficultyCard } from './NpcDifficultyCard';
import { NpcLordsCard } from './NpcLordsCard';
import { OpeningCard } from './OpeningCard';
import { SuspiciousCard } from './SuspiciousCard';
import { PlayersCard } from './PlayersCard';
import { StatusCard } from './StatusCard';
import { WorldTab } from './WorldTab';

/**
 * /admin – the host's page (08-host-console §7): one tab per area, the same operations as the CLI
 * `bannerfall-admin`, all through /api/admin/**. Deleting a player is on his profile, hiding a chat
 * message under the message.
 */
export function AdminPage() {
  const t = useT();
  const status = useAdminStatus();
  const cheatsEnabled = status.data?.cheatsEnabled ?? false;
  const [params, setParams] = useSearchParams();
  const tab = parseTab(params.get('tab'), cheatsEnabled);

  return (
    <div className={styles.page}>
      <h1>{t.admin.title}</h1>
      <div className={styles.tabs} role="tablist" aria-label={t.admin.tabs.label}>
        {visibleTabs(cheatsEnabled).map((value: AdminTab) => (
          <button
            key={value}
            type="button"
            role="tab"
            id={`admin-tab-${value}`}
            aria-selected={value === tab}
            aria-controls="admin-panel"
            className={styles.tab}
            onClick={() => setParams(value === 'status' ? {} : { tab: value }, { replace: true })}
          >
            {t.admin.tabs[value]}
          </button>
        ))}
      </div>
      <div id="admin-panel" role="tabpanel" aria-labelledby={`admin-tab-${tab}`} className={styles.panel}>
        <TabContent tab={tab} />
      </div>
    </div>
  );
}

function TabContent({ tab }: { tab: (typeof adminTabs)[number] }) {
  switch (tab) {
    case 'status':
      return (
        <>
          <StatusCard />
          <BroadcastCard />
          <ChatSayCard />
        </>
      );
    case 'players':
      return <PlayersCard />;
    case 'world':
      // the five sections of the setup wizard with one save bar (1.6); the opening time stays on top
      return <WorldTab opening={<OpeningCard />} />;
    case 'events':
      return <FailedEventsCard />;
    case 'lords':
      return (
        <>
          <NpcDifficultyCard />
          <NpcLordsCard />
        </>
      );
    case 'suspicious':
      return <SuspiciousCard />;
    case 'cheats':
      return <CheatsCard />;
    case 'audit':
      return <AuditCard />;
  }
}

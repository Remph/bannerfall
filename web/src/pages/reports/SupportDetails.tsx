import type { SupportReport } from '../../api/queries/reports';
import { Card } from '../../components/Card';
import { UnitSummary } from '../../components/UnitSummary';
import { useT } from '../../i18n';
import { ReportVillage } from './BattleDetails';
import styles from './ReportsPage.module.css';

/** A support that arrived or was sent home: from where, to where, which units (the title says what happened). */
export function SupportDetails({ report, villageId }: { report: SupportReport; villageId: number | undefined }) {
  const t = useT();
  return (
    <div className={styles.columns}>
      <Card title={t.reports.supportUnits}>
        <div className={styles.cardBody}>
          <UnitSummary units={report.units} />
          <dl className={styles.facts}>
            <div>
              <dt>{t.reports.supportFrom}</dt>
              <dd>
                <ReportVillage village={report.from} villageId={villageId} />
              </dd>
            </div>
            <div>
              <dt>{t.reports.supportTo}</dt>
              <dd>
                <ReportVillage village={report.to} villageId={villageId} />
              </dd>
            </div>
          </dl>
        </div>
      </Card>
    </div>
  );
}

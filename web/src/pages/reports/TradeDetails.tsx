import type { TradeReport } from '../../api/queries/reports';
import { Card } from '../../components/Card';
import { useT } from '../../i18n';
import { ReportVillage, ResourceList } from './BattleDetails';
import styles from './ReportsPage.module.css';

/** Merchants that delivered resources: from where, to where, what (the title says what happened). */
export function TradeDetails({ report, villageId }: { report: TradeReport; villageId: number | undefined }) {
  const t = useT();
  const lost = report.lost.wood + report.lost.clay + report.lost.iron > 0;
  return (
    <div className={styles.columns}>
      <Card title={t.reports.tradeGoods}>
        <div className={styles.cardBody}>
          <ResourceList amounts={report.resources} />
          {lost ? (
            <>
              <p>{t.reports.tradeLostToWarehouse}</p>
              <ResourceList amounts={report.lost} />
            </>
          ) : (
            <p>{t.reports.tradeNothingLost}</p>
          )}
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

import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { forgetReport, useReport, type Report, type ReportSummary } from '../../api/queries/reports';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { formatDateTimeExact } from '../../game/format';
import { farmCandidate, farmListVillageId, viewerOf } from '../../game/reports';
import { useI18n, useT } from '../../i18n';
import { useCurrentVillageId } from '../../layout/useCurrentVillageId';
import { simulatorPath } from '../rally/rallyPaths';
import { AddToFarmLink } from './AddToFarmLink';
import { BattleDetails } from './BattleDetails';
import { ConquestDetails } from './ConquestDetails';
import { DeleteReportButton } from './DeleteReportButton';
import { parseReportId, reportsPath } from './reportPaths';
import styles from './ReportsPage.module.css';
import { reportTitle } from './reportText';
import { SendReportButton } from './SendReportButton';
import { SupportDetails } from './SupportDetails';
import { TradeDetails } from './TradeDetails';

/** /reports/:reportId – one report (05-frontend §3). Opening it marks it read. */
export function ReportPage() {
  const reportId = parseReportId(useParams().reportId);
  if (reportId === undefined) {
    return <Navigate to={reportsPath(0)} replace />;
  }
  return <ReportDetail key={reportId} reportId={reportId} />;
}

/**
 * Where "All reports" leads: the list page the report was opened from (the list passes it as link
 * state), else the first page.
 */
function backPath(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const from = state.from;
    if (typeof from === 'string' && from.startsWith('/reports')) {
      return from;
    }
  }
  return reportsPath(0);
}

function ReportDetail({ reportId }: { reportId: number }) {
  const t = useT();
  const report = useReport(reportId);
  const back = backPath(useLocation().state);
  const queryClient = useQueryClient();
  // set when the report was deleted here: leaving the page then drops it from the cache
  const deleted = useRef(false);
  useEffect(() => {
    // the flag object itself never changes; its value is read when the page goes
    const wasDeleted = deleted;
    return () => {
      if (wasDeleted.current) {
        forgetReport(queryClient, reportId);
      }
    };
  }, [queryClient, reportId]);

  return (
    <div className={styles.page}>
      <Link className={styles.back} to={back}>
        <Icon name="chevronLeft" /> {t.reports.back}
      </Link>
      {report.isError ? (
        <div className={styles.status}>
          <ErrorMessage>{errorMessage(t, report.error)}</ErrorMessage>
          {!(report.error instanceof ApiError && report.error.code === 'REPORT_NOT_FOUND') && (
            <Button type="button" onClick={() => void report.refetch()}>
              {t.common.retry}
            </Button>
          )}
        </div>
      ) : report.isPending ? (
        <p>{t.common.loading}</p>
      ) : (
        <ReportContent
          report={report.data}
          back={back}
          onDeleted={() => {
            deleted.current = true;
          }}
        />
      )}
    </div>
  );
}

interface ReportContentProps {
  report: Report;
  /** The list page to return to after deleting. */
  back: string;
  /** Called after deleting, before leaving for the list. */
  onDeleted: () => void;
}

function ReportContent({ report, back, onDeleted }: ReportContentProps) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const villageId = useCurrentVillageId();
  const navigate = useNavigate();
  const [deleteError, setDeleteError] = useState<Error | null>(null);
  // who the report was sent to last on this page (M40)
  const [sentTo, setSentTo] = useState<string[] | null>(null);
  const { kind, payload } = report;
  // `payload.type` tells TypeScript which of the payloads it is (a discriminated union)
  const summary: ReportSummary | null =
    payload === null
      ? null
      : payload.type === 'BATTLE'
        ? { from: payload.attacker, to: payload.defender, outcome: payload.outcome }
        : payload.type === 'CONQUEST'
          ? { from: payload.from, to: payload.village, outcome: null }
          : { from: payload.from, to: payload.to, outcome: null };
  const { sharedBy } = report;
  // a copy someone sent (M40) reads as its first owner saw it ("you" is that player)
  const title = reportTitle(t, kind, summary, viewerOf(sharedBy, me.playerId));
  const farmVillage =
    payload?.type === 'BATTLE' ? farmCandidate(kind, payload.attacker, payload.defender, me.playerId) : null;
  const farmListOf =
    payload?.type === 'BATTLE'
      ? farmListVillageId(
          kind,
          payload.attacker.id,
          me.villages.map((village) => village.id),
          villageId,
        )
      : undefined;

  return (
    <>
      <header className={styles.detailHead}>
        <div className={styles.headText}>
          <h1 className={styles.detailTitle}>{title}</h1>
          <p className={styles.meta}>
            {t.reports.kinds[kind]} · {formatDateTimeExact(Date.parse(report.happenedAt), language)}
          </p>
        </div>
        <div className={styles.detailActions}>
          {payload !== null && <SendReportButton reportId={report.id} title={title} onSent={setSentTo} />}
          <DeleteReportButton
            reportId={report.id}
            title={title}
            // back to the list; `replace`: "back" in the browser doesn't return to the deleted report
            onDeleted={() => {
              onDeleted();
              void navigate(back, { replace: true });
            }}
            onError={setDeleteError}
          />
        </div>
      </header>
      {sharedBy !== null && (
        <div className={styles.shared}>
          <p className={styles.sharedFrom}>
            <Icon name="send" /> {t.reports.sharedBy(sharedBy.name)}
          </p>
          <p>{t.reports.sharedView(sharedBy.viewOfName)}</p>
        </div>
      )}
      {sentTo !== null && (
        <p className={styles.sentTo} role="status">
          {t.reports.sentTo(sentTo.join(', '))}
        </p>
      )}
      {deleteError !== null && <ErrorMessage>{errorMessage(t, deleteError)}</ErrorMessage>}
      {payload === null ? (
        <p>{t.reports.unreadableText}</p>
      ) : payload.type === 'BATTLE' ? (
        <>
          {farmListOf !== undefined && farmVillage !== null && (
            // at the top: on a phone the end of a battle report is several screens down
            <AddToFarmLink
              village={farmVillage}
              villageId={farmListOf}
              // the reader's own attack: what it sent is a good first template (not a copy's, M40)
              sent={kind === 'ATTACK' && sharedBy === null ? payload.attackerUnits : undefined}
            />
          )}
          <BattleDetails kind={kind} battle={payload} villageId={villageId} />
          {villageId !== undefined && (
            // the numbers of this battle in the attack simulator (M23)
            <Link className={styles.reportAction} to={simulatorPath(villageId, report.id)}>
              {t.simulator.simulateReport}
            </Link>
          )}
        </>
      ) : payload.type === 'CONQUEST' ? (
        <ConquestDetails kind={kind} report={payload} villageId={villageId} />
      ) : payload.type === 'TRADE' ? (
        <TradeDetails report={payload} villageId={villageId} />
      ) : (
        <SupportDetails report={payload} villageId={villageId} />
      )}
    </>
  );
}

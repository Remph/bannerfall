import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useDeleteAllReports, useReadAllReports, useReportPage, type ReportItem } from '../../api/queries/reports';
import { Button } from '../../components/Button';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Pagination } from '../../components/Pagination';
import { formatDateTime } from '../../game/format';
import { pageCount, resultFor, REPORTS_PAGE_SIZE, viewerOf } from '../../game/reports';
import { useI18n, useT } from '../../i18n';
import { DeleteReportButton } from './DeleteReportButton';
import { reportIcon } from './reportIcon';
import { pageFromSearch, reportPath, reportsPath } from './reportPaths';
import styles from './ReportsPage.module.css';
import { reportTitle } from './reportText';

/**
 * /reports?page= – the player's reports, newest first (05-frontend §3): unread ones bold, an icon
 * per kind, coloured by the result; mark all as read, delete one or all (M38), pages of 25.
 */
export function ReportsPage() {
  const t = useT();
  const [params] = useSearchParams();
  const page = pageFromSearch(params);
  const reports = useReportPage(page);
  const readAll = useReadAllReports();
  const deleteAll = useDeleteAllReports();
  const navigate = useNavigate();
  // what "Delete all" will delete, fixed when the sheet opens: the counts it names and the newest
  // report the player saw (one that arrives while the sheet is open stays)
  const [deleteAllOf, setDeleteAllOf] = useState<{ upTo: number; total: number; unread: number } | null>(null);
  // a failed delete of one line, shown below the list of its page (the error itself: translated
  // while rendering); the next delete clears it
  const [deleteError, setDeleteError] = useState<{ page: number; error: Error } | null>(null);

  if (reports.isError) {
    return (
      <div className={styles.status}>
        <ErrorMessage>{errorMessage(t, reports.error)}</ErrorMessage>
        <Button type="button" onClick={() => void reports.refetch()}>
          {t.common.retry}
        </Button>
      </div>
    );
  }
  if (reports.isPending) {
    return <p>{t.common.loading}</p>;
  }

  const { items, total, unread, newestId } = reports.data;
  const pages = pageCount(total, REPORTS_PAGE_SIZE);
  // e.g. the last report of the last page was deleted: go to the page that is the last one now
  if (items.length === 0 && page > 0 && total > 0 && !reports.isFetching) {
    return <Navigate to={reportsPath(pages - 1)} replace />;
  }

  return (
    <div className={styles.page}>
      <header className={styles.listHead}>
        <div className={styles.headText}>
          <h1>{t.reports.title}</h1>
          {total > 0 && (
            <p className={styles.meta}>
              {unread > 0 && `${t.reports.unreadCount(unread)} · `}
              {t.reports.total(total)}
            </p>
          )}
        </div>
        {total > 0 && (
          <div className={styles.headActions}>
            <Button
              type="button"
              variant="secondary"
              size="small"
              disabled={unread === 0 || readAll.isPending}
              onClick={() => readAll.mutate()}
            >
              {t.reports.markAllRead}
            </Button>
            <Button
              type="button"
              variant="danger"
              size="small"
              className={styles.deleteAll}
              disabled={deleteAll.isPending}
              onClick={() => {
                deleteAll.reset();
                setDeleteAllOf({ upTo: newestId, total, unread });
              }}
            >
              <Icon name="trash" />
              {t.reports.deleteAll}
            </Button>
          </div>
        )}
      </header>
      {readAll.isError && <ErrorMessage>{errorMessage(t, readAll.error)}</ErrorMessage>}
      <ConfirmSheet
        open={deleteAllOf !== null}
        title={t.reports.deleteAllTitle}
        text={deleteAllOf === null ? '' : t.reports.deleteAllText(deleteAllOf.total, deleteAllOf.unread)}
        confirmLabel={t.reports.deleteAll}
        danger
        pending={deleteAll.isPending}
        error={deleteAll.error}
        onConfirm={() => {
          if (deleteAllOf === null) {
            return;
          }
          deleteAll.mutate(deleteAllOf.upTo, {
            onSuccess: () => {
              setDeleteAllOf(null);
              // reports that arrived meanwhile are the newest: they are on the first page
              if (page > 0) {
                void navigate(reportsPath(0), { replace: true });
              }
            },
          });
        }}
        onClose={() => setDeleteAllOf(null)}
      />

      {items.length === 0 ? (
        <div className={styles.panel}>
          <p className={styles.empty}>{page === 0 ? t.reports.empty : t.reports.emptyPage}</p>
          {page > 0 && (
            <p className={styles.empty}>
              <Link to={reportsPath(0)}>{t.reports.toFirstPage}</Link>
            </p>
          )}
        </div>
      ) : (
        <ul className={`${styles.panel} ${styles.list}`}>
          {items.map((item) => (
            <ReportLine
              key={item.id}
              item={item}
              page={page}
              onDeleteStart={() => setDeleteError(null)}
              onDeleteError={(error) => setDeleteError({ page, error })}
            />
          ))}
        </ul>
      )}
      {deleteError !== null && deleteError.page === page && (
        <ErrorMessage>{errorMessage(t, deleteError.error)}</ErrorMessage>
      )}

      {pages > 1 && (
        <Pagination
          page={page}
          pages={pages}
          pathFor={reportsPath}
          label={t.reports.pagesLabel}
          previous={t.reports.newer}
          next={t.reports.older}
          info={t.reports.page(page + 1, pages)}
        />
      )}
    </div>
  );
}

interface ReportLineProps {
  item: ReportItem;
  /** The list page the line is on: the detail's "back" link returns there. */
  page: number;
  onDeleteStart: () => void;
  onDeleteError: (error: Error) => void;
}

/**
 * One report: kind icon (coloured by the result), title, time and result, who sent it (a copy,
 * M40); the delete button.
 */
function ReportLine({ item, page, onDeleteStart, onDeleteError }: ReportLineProps) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  // a copy someone sent (M40) reads as its first owner saw it
  const title = reportTitle(t, item.kind, item.summary, viewerOf(item.sharedBy, me.playerId));
  const result = resultFor(item.kind, item.summary?.outcome ?? null);
  return (
    <li className={item.read ? styles.line : `${styles.line} ${styles.unread}`}>
      <Link className={styles.lineLink} to={reportPath(item.id)} state={{ from: reportsPath(page) }}>
        <span className={[styles.kindIcon, styles[result]].filter(Boolean).join(' ')}>
          <Icon name={reportIcon(item.kind, item.summary?.outcome ?? null)} />
        </span>
        <span className={styles.lineText}>
          <span className={styles.lineTitle}>
            {!item.read && <span className="sr-only">{t.reports.unread}: </span>}
            {title}
          </span>
          <span className={styles.lineMeta}>
            <span className="sr-only">{t.reports.kinds[item.kind]} · </span>
            {formatDateTime(Date.parse(item.happenedAt), language)}
            {result !== 'none' && (
              <span className={[styles.resultPill, styles[result]].filter(Boolean).join(' ')}>
                {t.reports.results[result]}
              </span>
            )}
            {item.sharedBy !== null && (
              <span className={styles.sharedPill}>
                <Icon name="send" /> {t.reports.sharedFrom(item.sharedBy.name)}
              </span>
            )}
          </span>
        </span>
      </Link>
      <span className={styles.lineAction}>
        <DeleteReportButton reportId={item.id} title={title} compact onStart={onDeleteStart} onError={onDeleteError} />
      </span>
    </li>
  );
}

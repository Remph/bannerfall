import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useMatch, useNavigate, useOutlet, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import {
  forgetReport,
  useDeleteAllReports,
  useDeleteBarbarianReports,
  useDeleteReports,
  useReadAllReports,
  useReportPage,
  type ReportItem,
  type ReportSummary,
} from '../../api/queries/reports';
import { Banner } from '../../components/Banner';
import { Button } from '../../components/Button';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { ListDetail } from '../../components/ListDetail';
import { Pagination } from '../../components/Pagination';
import { UnitSummary } from '../../components/UnitSummary';
import { calendarDay, formatDateTime, formatDay, formatNumber, formatTime } from '../../game/format';
import {
  groupByDay,
  pageCount,
  reportFilters,
  resultFor,
  REPORTS_PAGE_SIZE,
  viewerOf,
  type ReportFilter,
} from '../../game/reports';
import { resourceTypes } from '../../game/resources';
import { useServerNow } from '../../game/serverTime';
import { useI18n, useT } from '../../i18n';
import { DESKTOP_QUERY, useMediaQuery } from '../../layout/useMediaQuery';
import { reportIcon } from './reportIcon';
import { filterFromSearch, pageFromSearch, parseReportId, reportPath, reportsPath } from './reportPaths';
import styles from './ReportsPage.module.css';
import { reportTitle } from './reportText';

/** How long the select bar's Delete waits for the second tap (like a report's own Delete). */
const ARMED_MS = 4000;

/**
 * /reports and /reports/:reportId (the child route, `ReportPage`). Phones: the list, or the report
 * as a page of its own. Desktop (1.3): the list with the open report next to it, or a hint to pick
 * one; the list stays where it is while reports are opened.
 */
export function ReportsPage() {
  const t = useT();
  const split = useMediaQuery(DESKTOP_QUERY);
  const detail = useOutlet();
  const openId = parseReportId(useMatch('/reports/:reportId')?.params.reportId);
  if (!split) {
    return detail ?? <ReportList openId={undefined} reportPages />;
  }
  return (
    <ListDetail
      list={<ReportList openId={openId} />}
      detail={detail}
      empty={t.reports.pickReport}
      label={t.reports.openReport}
      openKey={openId}
    />
  );
}

/**
 * The player's reports, newest first (05-frontend §3): filter chips with counts and one heading per
 * day (1.3); unread ones bold, an icon per kind, coloured by the result, the loot and losses of a
 * battle; mark all as read, delete all (M38) or the reports on barbarian villages (1.2.3), pages of
 * 25. "Select" turns the lines into checkboxes with a bar to delete the chosen ones (1.3); the lines
 * themselves have no buttons. `openId`: the report open next to it (desktop), marked in the list;
 * paging and filtering keep it open. `reportPages`: a report opens as a page of its own (phones), with the
 * cross-fade of a page change (1.3); next to the list it fades in by itself (ListDetail).
 */
function ReportList({ openId, reportPages = false }: { openId: number | undefined; reportPages?: boolean }) {
  const t = useT();
  const [params] = useSearchParams();
  const page = pageFromSearch(params);
  const filter = filterFromSearch(params);
  const reports = useReportPage(page, filter);
  const readAll = useReadAllReports();
  const deleteAll = useDeleteAllReports();
  const deleteBarbarian = useDeleteBarbarianReports();
  const deleteChosen = useDeleteReports();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selecting, setSelecting] = useState(false);
  // the open report when a bulk delete took it: dropped from the cache once it is closed (while it
  // is on screen its query would only fetch it again)
  const closedDeleted = useRef<number | null>(null);
  useEffect(() => {
    if (closedDeleted.current !== null && closedDeleted.current !== openId) {
      forgetReport(queryClient, closedDeleted.current);
      closedDeleted.current = null;
    }
  }, [openId, queryClient]);
  // the lines chosen in select mode, and on which list (filter and page): another list starts empty
  const [chosen, setChosen] = useState<{ list: string; ids: number[] }>({ list: '', ids: [] });
  // what "Delete all" will delete, fixed when the sheet opens: the counts it names and the newest
  // report the player saw (one that arrives while the sheet is open stays)
  const [deleteAllOf, setDeleteAllOf] = useState<{ upTo: number; total: number; unread: number } | null>(null);
  // the same for "Delete barbarian reports": the newest report seen and how many will go
  const [deleteBarbarianOf, setDeleteBarbarianOf] = useState<{ upTo: number; count: number } | null>(null);

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

  const { items, total: shownTotal, unread, newestId, barbarian, counts } = reports.data;
  // all the player's reports; `shownTotal` are those of the filter
  const total = reportFilters.reduce((sum, each) => sum + (counts[each] ?? 0), 0);
  const pages = pageCount(shownTotal, REPORTS_PAGE_SIZE);
  // the list's own links keep the open report open (desktop)
  const listPath = (to: number, toFilter: ReportFilter | null) =>
    openId === undefined ? reportsPath(to, toFilter) : reportPath(openId, to, toFilter);
  const pathFor = (to: number) => listPath(to, filter);
  const list = reportsPath(page, filter);
  // a bulk delete that took the open report closes it (`replace`: "back" doesn't return to it)
  const closeIfDeleted = (deleted: boolean, to: string) => {
    if (openId !== undefined && deleted) {
      closedDeleted.current = openId;
      void navigate(to, { replace: true });
    }
  };
  // a chosen line that is gone (deleted, or pushed to the next page) is no longer chosen
  const chosenIds = chosen.list === list ? chosen.ids.filter((id) => items.some((item) => item.id === id)) : [];
  const allChosen = items.length > 0 && chosenIds.length === items.length;
  const choose = (ids: number[]) => setChosen({ list, ids });
  // e.g. the last report of the last page was deleted: go to the page that is the last one now
  if (items.length === 0 && page > 0 && shownTotal > 0 && !reports.isFetching) {
    return <Navigate to={pathFor(pages - 1)} replace />;
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
              variant={selecting ? 'primary' : 'secondary'}
              size="small"
              onClick={() => {
                setSelecting(!selecting);
                choose([]);
                deleteChosen.reset();
              }}
            >
              {selecting ? t.reports.selectDone : t.reports.select}
            </Button>
            {barbarian > 0 && (
              <Button
                type="button"
                variant="secondary"
                size="small"
                className={styles.deleteAll}
                disabled={deleteBarbarian.isPending}
                onClick={() => {
                  deleteBarbarian.reset();
                  setDeleteBarbarianOf({ upTo: newestId, count: barbarian });
                }}
              >
                <Icon name="trash" />
                {t.reports.deleteBarbarian}
              </Button>
            )}
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
              if (openId !== undefined) {
                closeIfDeleted(true, reportsPath(0, filter));
              } else if (page > 0) {
                void navigate(pathFor(0), { replace: true });
              }
            },
          });
        }}
        onClose={() => setDeleteAllOf(null)}
      />
      <ConfirmSheet
        open={deleteBarbarianOf !== null}
        title={t.reports.deleteBarbarianTitle}
        text={deleteBarbarianOf === null ? '' : t.reports.deleteBarbarianText(deleteBarbarianOf.count)}
        confirmLabel={t.reports.deleteBarbarian}
        danger
        pending={deleteBarbarian.isPending}
        error={deleteBarbarian.error}
        onConfirm={() => {
          if (deleteBarbarianOf === null) {
            return;
          }
          deleteBarbarian.mutate(deleteBarbarianOf.upTo, {
            onSuccess: () => {
              setDeleteBarbarianOf(null);
              // the list can't tell whether the open report was one of them: it closes either way
              closeIfDeleted(true, list);
            },
          });
        }}
        onClose={() => setDeleteBarbarianOf(null)}
      />

      {total > 0 && <FilterChips value={filter} counts={counts} total={total} pathFor={(chip) => listPath(0, chip)} />}
      {items.length === 0 ? (
        <div className={styles.panel}>
          <p className={styles.empty}>
            {page > 0 ? t.reports.emptyPage : filter !== null ? t.reports.emptyFilter : t.reports.empty}
          </p>
          {page > 0 && (
            <p className={styles.empty}>
              <Link to={pathFor(0)}>{t.reports.toFirstPage}</Link>
            </p>
          )}
        </div>
      ) : (
        <div className={styles.days}>
          {groupByDay(items).map(({ day, items: dayItems }) => (
            <section key={day} className={styles.day} aria-labelledby={`reports-${day}`}>
              <DayHeading id={`reports-${day}`} ms={Date.parse(dayItems[0]!.listedAt)} />
              <ul className={`${styles.panel} ${styles.list}`}>
                {dayItems.map((item) => (
                  <ReportLine
                    key={item.id}
                    item={item}
                    day={day}
                    to={reportPath(item.id, page, filter)}
                    open={item.id === openId}
                    pageTransition={reportPages}
                    selecting={selecting}
                    chosen={chosenIds.includes(item.id)}
                    onToggle={() =>
                      choose(
                        chosenIds.includes(item.id)
                          ? chosenIds.filter((id) => id !== item.id)
                          : [...chosenIds, item.id],
                      )
                    }
                  />
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
      {deleteChosen.isError && <ErrorMessage>{errorMessage(t, deleteChosen.error)}</ErrorMessage>}

      {pages > 1 && (
        <Pagination
          page={page}
          pages={pages}
          pathFor={pathFor}
          label={t.reports.pagesLabel}
          previous={t.reports.newer}
          next={t.reports.older}
          info={t.reports.page(page + 1, pages)}
        />
      )}
      {selecting && items.length > 0 && (
        <SelectBar
          count={chosenIds.length}
          allChosen={allChosen}
          pending={deleteChosen.isPending}
          onChooseAll={() => choose(allChosen ? [] : items.map((item) => item.id))}
          onDelete={() =>
            deleteChosen.mutate(chosenIds, {
              onSuccess: () => {
                closeIfDeleted(openId !== undefined && chosenIds.includes(openId), list);
                choose([]);
              },
            })
          }
        />
      )}
    </div>
  );
}

interface SelectBarProps {
  count: number;
  allChosen: boolean;
  pending: boolean;
  onChooseAll: () => void;
  onDelete: () => void;
}

/**
 * Select mode's bar (1.3), above the tab bar on phones: how many lines are chosen, all or none of
 * the page, and Delete with two taps like a report's own Delete (the first asks, a second deletes).
 */
function SelectBar({ count, allChosen, pending, onChooseAll, onDelete }: SelectBarProps) {
  const t = useT();
  const [armed, setArmed] = useState(false);

  useEffect(() => {
    if (!armed) {
      return;
    }
    const timer = setTimeout(() => setArmed(false), ARMED_MS);
    return () => clearTimeout(timer);
  }, [armed]);

  return (
    <div className={styles.selectBar}>
      <span className={styles.selectCount} role="status">
        {t.reports.selectedCount(count)}
      </span>
      <Button type="button" variant="secondary" size="small" onClick={onChooseAll}>
        {allChosen ? t.reports.selectNone : t.reports.selectAll}
      </Button>
      <button
        type="button"
        className={armed ? `${styles.delete} ${styles.deleteArmed}` : styles.delete}
        disabled={count === 0 || pending}
        onClick={() => {
          if (!armed) {
            setArmed(true);
            return;
          }
          setArmed(false);
          onDelete();
        }}
        // a mouse or keyboard user who moves on doesn't leave it armed
        onBlur={() => setArmed(false)}
      >
        {armed ? (
          t.reports.reallyDelete
        ) : (
          <>
            <Icon name="trash" />
            {t.reports.delete}
          </>
        )}
      </button>
    </div>
  );
}

/** The filter chips (1.3): all, attacks, defence … each with its count; links, so the back button and the detail's back link keep the filter. */
function FilterChips({
  value,
  counts,
  total,
  pathFor,
}: {
  value: ReportFilter | null;
  counts: Partial<Record<ReportFilter, number>>;
  total: number;
  pathFor: (filter: ReportFilter | null) => string;
}) {
  const t = useT();
  const chips: { filter: ReportFilter | null; label: string; count: number }[] = [
    { filter: null, label: t.reports.filters.all, count: total },
    ...reportFilters.map((filter) => ({ filter, label: t.reports.filters[filter], count: counts[filter] ?? 0 })),
  ];
  return (
    <nav className={styles.chips} aria-label={t.reports.filtersLabel}>
      {chips.map(({ filter, label, count }) => (
        <Link
          key={filter ?? 'all'}
          className={styles.chip}
          to={pathFor(filter)}
          aria-current={filter === value ? 'page' : undefined}
          replace
        >
          {label} <span className={styles.chipCount}>{count}</span>
        </Link>
      ))}
    </nav>
  );
}

/** "Today", "Yesterday" or the date: the day's heading, which turns over at midnight. */
function DayHeading({ id, ms }: { id: string; ms: number }) {
  const { t, language } = useI18n();
  const now = useServerNow();
  const day = formatDay(ms, now, language);
  return (
    <h2 id={id} className={styles.dayTitle}>
      {day === 'today' ? t.reports.today : day === 'yesterday' ? t.reports.yesterday : day.date}
    </h2>
  );
}

interface ReportLineProps {
  item: ReportItem;
  /** The day heading the line is under ("2026-10-16"): the line shows only the time if it happened that day. */
  day: string;
  /** The report, with the list (filter and page) the line is on: the detail's "back" link returns there. */
  to: string;
  /** The report is open next to the list (desktop, 1.3). */
  open: boolean;
  /** Opening it is a page change that cross-fades (phones, 1.3). */
  pageTransition: boolean;
  /** Select mode (1.3): the line is a checkbox instead of a link to the report. */
  selecting: boolean;
  chosen: boolean;
  onToggle: () => void;
}

/**
 * One report: kind icon (coloured by the result), title, time and result, the banners the reader
 * gained there, who sent it (a copy, M40); a battle's loot and losses (1.3). It opens the report,
 * or in select mode chooses it.
 */
function ReportLine({ item, day, to, open, pageTransition, selecting, chosen, onToggle }: ReportLineProps) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  // a copy someone sent (M40) reads as its first owner saw it
  const title = reportTitle(t, item.kind, item.summary, viewerOf(item.sharedBy, me.playerId));
  const result = resultFor(item.kind, item.summary?.outcome ?? null);
  const bannersGained = item.summary?.bannersGained ?? [];
  const happenedAt = Date.parse(item.happenedAt);
  const content = (
    <>
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
          {calendarDay(happenedAt) === day ? formatTime(happenedAt, language) : formatDateTime(happenedAt, language)}
          {result !== 'none' && (
            <span className={[styles.resultPill, styles[result]].filter(Boolean).join(' ')}>
              {t.reports.results[result]}
            </span>
          )}
          {bannersGained.length > 0 && (
            <span className={styles.bannerPill}>
              {bannersGained.map((banner, index) => (
                <Banner key={index} type={banner.type} tier={banner.tier} size={10} />
              ))}
              {t.reports.bannerGained(bannersGained.length)}
            </span>
          )}
          {item.sharedBy !== null && (
            <span className={styles.sharedPill}>
              <Icon name="send" /> {t.reports.sharedFrom(item.sharedBy.name)}
            </span>
          )}
        </span>
        {item.summary !== null && <BattleFacts kind={item.kind} summary={item.summary} />}
      </span>
    </>
  );
  return (
    <li
      className={[styles.line, !item.read && styles.unread, chosen && styles.chosen, open && styles.open]
        .filter(Boolean)
        .join(' ')}
    >
      {selecting ? (
        <label className={styles.lineLink}>
          <input type="checkbox" className={styles.check} checked={chosen} onChange={onToggle} aria-label={title} />
          {content}
        </label>
      ) : (
        <Link
          viewTransition={pageTransition}
          className={styles.lineLink}
          to={to}
          aria-current={open ? 'page' : undefined}
        >
          {content}
        </Link>
      )}
    </li>
  );
}

/**
 * A battle's third line (1.3): the resources taken (the attacker's with "full haul" when his
 * survivors carried all they could), then the reader's own losses or "no losses". Nothing for
 * other reports and for scouting without losses.
 */
function BattleFacts({ kind, summary }: { kind: ReportItem['kind']; summary: ReportSummary }) {
  const { t, language } = useI18n();
  const { loot, losses, fullHaul } = summary;
  const lost = Object.values(losses).some((count) => count > 0);
  const taken = loot !== null && resourceTypes.some((type) => loot[type] > 0);
  if (!taken && !lost && loot === null) {
    return null;
  }
  return (
    <span className={styles.facts}>
      {taken && (
        <span className={styles.fact}>
          {kind === 'DEFENSE' && <span>{t.reports.lineTaken}</span>}
          {resourceTypes.map((type) => (
            <span key={type} className={styles.resource}>
              <Icon name={type} />
              <span className="sr-only">{t.resources[type]}: </span>
              {formatNumber(loot[type], language)}
            </span>
          ))}
          {fullHaul && <span className={styles.fullHaul}>{t.reports.fullHaul}</span>}
        </span>
      )}
      {lost ? (
        <span className={styles.fact}>
          {t.reports.lineLosses} <UnitSummary units={losses} className={styles.factUnits} />
        </span>
      ) : (
        <span className={styles.fact}>{t.reports.lineNoLosses}</span>
      )}
    </span>
  );
}

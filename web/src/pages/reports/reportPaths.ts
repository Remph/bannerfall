import { reportFilters, type ReportFilter } from '../../game/reports';

/** The highest page the server accepts (0-based, ReportController.MAX_PAGE). */
const MAX_PAGE = 10_000;

/**
 * `/reports`, `/reports?page=2`, `/reports?filter=attacks&page=2`: the list; pages are 1-based in
 * the URL, 0-based for the server; the filter (1.3) is the server's in lower case.
 */
export function reportsPath(page: number, filter: ReportFilter | null = null): string {
  return `/reports${listSearch(page, filter)}`;
}

/** `?filter=attacks&page=2`, or nothing for the first page of all reports. */
function listSearch(page: number, filter: ReportFilter | null): string {
  const query = new URLSearchParams();
  if (filter !== null) {
    query.set('filter', filter.toLowerCase());
  }
  if (page > 0) {
    query.set('page', String(page + 1));
  }
  const search = query.toString();
  return search === '' ? '' : `?${search}`;
}

/** The filter from `?filter=`; missing or unknown → null (all reports). */
export function filterFromSearch(params: URLSearchParams): ReportFilter | null {
  const value = (params.get('filter') ?? '').toUpperCase();
  return reportFilters.find((filter) => filter === value) ?? null;
}

/**
 * `/reports/12`, `/reports/12?filter=attacks&page=2`: one report, with the list it was opened from
 * (1.3). The detail's "back" link returns to that list; on desktop it is shown next to the report.
 */
export function reportPath(reportId: number, page = 0, filter: ReportFilter | null = null): string {
  return `/reports/${reportId}${listSearch(page, filter)}`;
}

/**
 * The 0-based page from `?page=`: missing or not a plain number above 0 → the first page; above
 * the server's limit → that limit (the list then finds it empty and goes to the last page).
 */
export function pageFromSearch(params: URLSearchParams): number {
  const value = params.get('page') ?? '';
  return /^\d{1,5}$/.test(value) && Number(value) > 0 ? Math.min(Number(value) - 1, MAX_PAGE) : 0;
}

/** `/reports/12` → 12; anything else → undefined. */
export function parseReportId(param: string | undefined): number | undefined {
  return param !== undefined && /^\d{1,15}$/.test(param) && Number(param) > 0 ? Number(param) : undefined;
}

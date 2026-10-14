/** The highest page the server accepts (0-based, ReportController.MAX_PAGE). */
const MAX_PAGE = 10_000;

/** `/reports`, `/reports?page=2`: the list; pages are 1-based in the URL, 0-based for the server. */
export function reportsPath(page: number): string {
  return page <= 0 ? '/reports' : `/reports?page=${page + 1}`;
}

export function reportPath(reportId: number): string {
  return `/reports/${reportId}`;
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

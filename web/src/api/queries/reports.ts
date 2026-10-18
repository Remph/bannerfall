import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { REPORTS_PAGE_SIZE, type ReportFilter } from '../../game/reports';
import { api, expectData } from '../client';
import type { components } from '../schema';

/** GET /api/reports: one page of the list, with the counts of all pages. */
export type ReportPage = components['schemas']['ReportPageDto'];
export type ReportItem = components['schemas']['ReportItemDto'];
export type ReportSummary = components['schemas']['ReportSummary'];
/** GET /api/reports/{id}: `payload.type` tells a battle from a support or a conquest. */
export type Report = components['schemas']['ReportDto'];
export type BattleReport = components['schemas']['BattleReport'];
export type SupportReport = components['schemas']['SupportReport'];
export type ConquestReport = components['schemas']['ConquestReport'];
export type TradeReport = components['schemas']['TradeReport'];
export type LoyaltyReport = components['schemas']['LoyaltyReport'];
export type DefenderSide = components['schemas']['DefenderSide'];
export type DefenderStack = components['schemas']['DefenderStack'];
export type ScoutingReport = components['schemas']['ScoutingReport'];
export type BannerReport = components['schemas']['BannerReport'];
export type ConqueredBanner = components['schemas']['ConqueredBanner'];
/** Who sent a copy and whose report it was first (M40); null on a report of the game. */
export type ReportSharedBy = components['schemas']['ReportSharedBy'];

/**
 * Everything of the list: the pages (`['reports', page]`) and the unread counter
 * (`['reports', 'unread']`). Invalidating this prefix refreshes both – after reading, deleting, or
 * when new reports may have arrived.
 */
export const reportsQueryKey = ['reports'] as const;
const unreadQueryKey = [...reportsQueryKey, 'unread'] as const;

export function reportPageQueryKey(page: number, filter: ReportFilter | null = null) {
  return [...reportsQueryKey, page, filter ?? 'all'] as const;
}

/** One report never changes (only its read flag, which the detail doesn't show). */
export function reportQueryKey(reportId: number) {
  return ['report', reportId] as const;
}

/**
 * Drops a deleted report from the cache. useDeleteReport does it for a report nobody shows; the
 * detail page calls this when it leaves after deleting its report (the entry would otherwise be
 * served for good, `staleTime: Infinity`, if the same URL is opened again).
 */
export function forgetReport(queryClient: QueryClient, reportId: number): void {
  queryClient.removeQueries({ queryKey: reportQueryKey(reportId), exact: true });
}

/** New reports may be there: after arrivals of movements (they create them on both sides). */
export function refreshReports(queryClient: QueryClient): Promise<unknown> {
  return queryClient.invalidateQueries({ queryKey: reportsQueryKey });
}

/**
 * GET /api/reports?page=&filter=: a page of the player's reports, newest first (page is 0-based),
 * only the filter's kinds unless it is null (1.3). The counts per filter come with every page.
 */
export function useReportPage(page: number, filter: ReportFilter | null = null) {
  return useQuery({
    queryKey: reportPageQueryKey(page, filter),
    queryFn: () =>
      expectData(
        api.GET('/api/reports', {
          params: { query: { page, size: REPORTS_PAGE_SIZE, ...(filter === null ? {} : { filter }) } },
        }),
      ),
  });
}

/**
 * GET /api/reports: the newest `size` reports in one go (at most the API's page size, 100), for a
 * picker that filters them itself (the simulator's report list).
 */
export function useRecentReports(size: number) {
  return useQuery({
    queryKey: [...reportsQueryKey, 'recent', size],
    queryFn: () => expectData(api.GET('/api/reports', { params: { query: { page: 0, size } } })),
  });
}

function loadReport(queryClient: QueryClient, reportId: number) {
  return expectData(api.GET('/api/reports/{reportId}', { params: { path: { reportId } } })).then((report) => {
    void refreshReports(queryClient);
    return report;
  });
}

/** The report once, from the cache if it is there (a report never changes): for code outside a component's render. */
export function fetchReport(queryClient: QueryClient, reportId: number): Promise<Report> {
  return queryClient.fetchQuery({
    queryKey: reportQueryKey(reportId),
    queryFn: () => loadReport(queryClient, reportId),
    staleTime: Infinity,
  });
}

/**
 * GET /api/reports/{id}: the report with its payload. Opening it marks it read on the server, so
 * the list and the counter are refreshed once it is there. It never changes, so it is not asked for
 * again while it is cached (`staleTime: Infinity`).
 */
export function useReport(reportId: number) {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey: reportQueryKey(reportId),
    queryFn: () => loadReport(queryClient, reportId),
    staleTime: Infinity,
  });
}

/**
 * GET /api/reports/unread: the number on the navigation's Reports entry. The REPORT_CREATED
 * notification invalidates `['reports']` (M9b), so it changes when a report arrives; after a
 * reconnect everything is fetched again.
 */
export function useUnreadReports() {
  return useQuery({
    queryKey: unreadQueryKey,
    queryFn: () => expectData(api.GET('/api/reports/unread')),
    select: (data) => data.unread,
  });
}

/** DELETE /api/reports/{id}. */
export function useDeleteReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (reportId: number) => {
      await api.DELETE('/api/reports/{reportId}', { params: { path: { reportId } } });
    },
    onSuccess: (_, reportId) => {
      // not while the detail shows it (it would ask the server again and get a 404); the detail
      // page leaves after deleting and drops it then (forgetReport)
      queryClient.removeQueries({ queryKey: reportQueryKey(reportId), type: 'inactive' });
    },
    // also after an error: it may have been deleted already (REPORT_NOT_FOUND)
    onSettled: () => refreshReports(queryClient),
  });
}

/**
 * POST /api/reports/delete: deletes the reports chosen in the list's select mode (1.3); the server
 * skips those that are gone already.
 */
export function useDeleteReports() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (ids: number[]) => {
      await api.POST('/api/reports/delete', { body: { ids } });
    },
    onSuccess: (_, ids) => {
      for (const id of ids) {
        queryClient.removeQueries({ queryKey: reportQueryKey(id), type: 'inactive' });
      }
    },
    onSettled: () => refreshReports(queryClient),
  });
}

/**
 * POST /api/reports/delete-all?upTo=: deletes every report up to `upTo`, the list's `newestId` the
 * player confirmed (a report that arrived since stays, M38).
 */
export function useDeleteAllReports() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (upTo: number) => {
      await api.POST('/api/reports/delete-all', { params: { query: { upTo } } });
    },
    onSuccess: () => {
      // the details of the deleted reports would otherwise be served from the cache for good
      queryClient.removeQueries({ queryKey: ['report'], type: 'inactive' });
    },
    onSettled: () => refreshReports(queryClient),
  });
}

/**
 * POST /api/reports/{id}/share: sends a copy of the report to the named players (M40). All or
 * nothing: an error names the player the send stopped at (`ApiError.playerName`).
 */
export function useShareReport(reportId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (names: string[]) =>
      expectData(api.POST('/api/reports/{reportId}/share', { params: { path: { reportId } }, body: { names } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: recentRecipientsQueryKey }),
  });
}

export const recentRecipientsQueryKey = ['reportRecipients'] as const;

/**
 * GET /api/reports/recent-recipients: the players the player last sent a report to by name, newest
 * first, at most 3 (1.3.1). "Send report" offers them first.
 */
export function useRecentReportRecipients() {
  return useQuery({
    queryKey: recentRecipientsQueryKey,
    queryFn: () => expectData(api.GET('/api/reports/recent-recipients')),
  });
}

/**
 * POST /api/reports/{id}/share-guild: sends a copy of the report to every other member of the
 * player's guild. Not all or nothing: members a named send would refuse are left out, so `sentTo`
 * can be shorter than the guild, or empty.
 */
export function useShareReportWithGuild(reportId: number) {
  return useMutation({
    mutationFn: () => expectData(api.POST('/api/reports/{reportId}/share-guild', { params: { path: { reportId } } })),
  });
}

/** POST /api/reports/read-all: every report counts as read. */
export function useReadAllReports() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api.POST('/api/reports/read-all');
    },
    onSettled: () => refreshReports(queryClient),
  });
}

/**
 * POST /api/reports/delete-barbarian?upTo=: deletes the player's own attack and scout reports on
 * barbarian villages up to `upTo`, except those with a new banner (1.2.3); the list's `barbarian`
 * says how many.
 */
export function useDeleteBarbarianReports() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (upTo: number) => {
      await api.POST('/api/reports/delete-barbarian', { params: { query: { upTo } } });
    },
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['report'], type: 'inactive' });
    },
    onSettled: () => refreshReports(queryClient),
  });
}

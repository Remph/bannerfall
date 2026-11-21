/** The search parameter that opens a help request's sheet on any page (2.1 S4). */
export const HELP_PARAM = 'help';

/**
 * `/diplomacy?help=12`: the Diplomacy page with the request's sheet open (a push or a toast). The
 * strip under the sky opens the sheet wherever `?help=` is.
 */
export function helpRequestPath(requestId: number): string {
  return `/diplomacy?${HELP_PARAM}=${requestId}`;
}

/** {@link helpRequestPath} as a router link. */
export function helpRequestLink(requestId: number): { pathname: string; search: string } {
  return { pathname: '/diplomacy', search: `?${HELP_PARAM}=${requestId}` };
}

/** The request `?help=` names, or undefined. */
export function helpFromSearch(params: URLSearchParams): number | undefined {
  const value = Number(params.get(HELP_PARAM) ?? '');
  return Number.isInteger(value) && value > 0 ? value : undefined;
}

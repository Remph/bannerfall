/**
 * The state of a link that opens its page at the top (1.6.2): the bars' entries. A page change
 * leaves the window where it was, so tapping Village after scrolling down in the smithy would show
 * the village scrolled down too.
 */
export const OPENS_AT_TOP = { opensAtTop: true } as const;

/** Whether a location's state came from a link with {@link OPENS_AT_TOP}. */
export function opensAtTop(state: unknown): boolean {
  return (state as { opensAtTop?: unknown } | null)?.opensAtTop === true;
}

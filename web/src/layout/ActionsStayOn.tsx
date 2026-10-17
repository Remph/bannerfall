import { useContext, useMemo, type ReactNode } from 'react';
import { WorldEndContext } from './worldEnd';

/**
 * A part that still works in an ended world (v1.3): the chat, the pages in `openWhenEnded`. Its
 * buttons stay on while `open` (always the same element, so switching `open` keeps the tree).
 */
export function ActionsStayOn({ open = true, children }: { open?: boolean; children: ReactNode }) {
  const outer = useContext(WorldEndContext);
  const frozen = outer.frozen && !open;
  const value = useMemo(() => ({ ended: outer.ended, frozen }), [outer.ended, frozen]);
  return <WorldEndContext value={value}>{children}</WorldEndContext>;
}

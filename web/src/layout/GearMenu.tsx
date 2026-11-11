import type { CSSProperties, RefObject } from 'react';
import { useT } from '../i18n';
import { FloatingMenu } from './FloatingMenu';
import { Settings } from './MenuSheet';

interface GearMenuProps {
  /** Where the menu hangs (menuPlaceUnder of the button, right-aligned); null: closed. */
  place: CSSProperties | null;
  /** A function that stays the same between renders (useCallback), or the menu's listeners restart with every render. */
  onClose: () => void;
  /** The gear button: the menu hangs under it, and a tap on it is the button's own (it toggles). */
  anchor: RefObject<HTMLElement | null>;
  onOpenWorldInfo?: () => void;
}

/**
 * The PC's gear menu (2.0): the settings in a small menu under the gear instead of a sheet in the
 * middle of the window. Not modal: the page behind stays in sight, which is the point of choosing a
 * colour theme. A tap elsewhere, Escape, a resize or the focus moving on closes it; after Escape the
 * focus is back on the gear. A page of the menu (profile, password …) closes it too.
 */
export function GearMenu({ place, onClose, anchor, onOpenWorldInfo }: GearMenuProps) {
  const t = useT();
  return (
    <FloatingMenu place={place} onClose={onClose} anchor={anchor} label={t.nav.settings}>
      <Settings onNavigate={onClose} onOpenWorldInfo={onOpenWorldInfo} profile />
    </FloatingMenu>
  );
}

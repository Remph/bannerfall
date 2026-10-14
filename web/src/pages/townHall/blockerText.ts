import { formatUpcoming } from '../../game/format';
import type { UpgradeBlocker } from '../../game/upgradePreview';
import type { Dictionary } from '../../i18n/en';
import type { Language } from '../../i18n/language';

/** The sentence for a blocker: the hints under the buttons and the status line of the preview. */
export function blockerText(t: Dictionary, language: Language, nowMs: number, blocker: UpgradeBlocker): string {
  switch (blocker.kind) {
    case 'bannersDisabled':
      // the server's refusal says the same
      return t.errors.BANNERS_DISABLED;
    case 'requirements':
      return t.townHall.needs(
        blocker.missing.map((need) => t.townHall.requirement(t.buildings[need.building], need.level)).join(', '),
      );
    case 'queueFull':
      return t.townHall.queueFull;
    case 'warehouse':
      return t.townHall.warehouseTooSmall;
    case 'never':
      return t.townHall.notProduced;
    case 'population':
      return t.townHall.farmTooSmall;
    case 'later':
      return t.townHall.enoughAt(formatUpcoming(blocker.atMs, nowMs, language));
  }
}

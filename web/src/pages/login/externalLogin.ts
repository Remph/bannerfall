import { isPermanentBan } from '../../api/errors';
import type { ExternalLogin } from '../../api/queries/auth';
import { formatDateTime } from '../../game/format';
import type { Dictionary } from '../../i18n/en';
import type { Language } from '../../i18n/language';

/**
 * What went wrong with the last login through the account server (1.5), as the login, join and
 * password pages show it; null if nothing did.
 */
export function externalProblemText(
  t: Dictionary,
  language: Language,
  state: ExternalLogin | undefined,
  server: string,
): string | null {
  const problem = state?.problem ?? null;
  if (problem === null) {
    return null;
  }
  if (problem === 'BANNED' && state?.bannedUntil) {
    const reason = state.banReason === null || state.banReason === '' ? null : state.banReason;
    return isPermanentBan(state.bannedUntil)
      ? t.auth.bannedForGood(reason)
      : t.auth.bannedUntil(formatDateTime(Date.parse(state.bannedUntil), language), reason);
  }
  return t.accountServer.problems[problem](server);
}

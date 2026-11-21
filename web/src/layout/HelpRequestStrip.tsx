import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useHelpRequests, type HelpRequest } from '../api/queries/diplomacy';
import { useWorld } from '../api/queries/world';
import { Countdown } from '../components/Countdown';
import { formatDuration } from '../game/format';
import { byDeadline, helpNeed } from '../game/helpRequests';
import { secondsUntil, useGameNow } from '../game/serverTime';
import { useI18n } from '../i18n';
import { HelpRequestSheet } from '../pages/diplomacy/HelpRequestSheet';
import { HELP_PARAM, helpFromSearch } from '../pages/diplomacy/helpPaths';
import styles from './HelpRequestStrip.module.css';

/**
 * While an ally asks for help (2.1 S4, 12-solo-and-allies §9), every page says so under the sky: the
 * lord and what it needs, the time left in the war colour as the biggest thing, a dot in the action
 * colour. With two open, the nearer deadline and "+1". A tap opens the request sheet; so does
 * `?help=` (a push or a toast) on any page. On a phone it keeps one line, the lord and the time; the
 * place is in the sheet (UI review 2.1, finding 07).
 */
export function HelpRequestStrip() {
  const world = useWorld().data;
  const enabled = world?.phase === 'OPEN' && world.soloMode;
  const now = useGameNow();
  const requests = byDeadline(useHelpRequests(enabled).data ?? [], now);
  const [params, setParams] = useSearchParams();
  const [openId, setOpenId] = useState<number | null>(null);
  const askedId = helpFromSearch(params);
  const shownId = openId ?? askedId ?? null;
  const shown = requests.find((request) => request.id === shownId) ?? null;

  function close() {
    setOpenId(null);
    if (askedId !== undefined) {
      const next = new URLSearchParams(params);
      next.delete(HELP_PARAM);
      setParams(next, { replace: true });
    }
  }

  const first = requests[0];
  if (!enabled || first === undefined) {
    return <HelpRequestSheet request={shown} onClose={close} />;
  }
  return (
    <>
      <Strip request={first} more={requests.length - 1} onOpen={() => setOpenId(first.id)} />
      <HelpRequestSheet request={shown} onClose={close} />
    </>
  );
}

function Strip({ request, more, onOpen }: { request: HelpRequest; more: number; onOpen: () => void }) {
  const { t, language } = useI18n();
  const texts = t.diplomacy.help;
  const now = useGameNow();
  const need = helpNeed(t, request, language);
  const left = formatDuration(secondsUntil(Date.parse(request.deadline), now));
  const label = [texts.stripLabel(request.lordName, need, left), more > 0 ? texts.moreLabel(more) : '']
    .filter(Boolean)
    .join(', ');
  return (
    // an <aside> like the other notices under the sky, so it makes room for the Now column (UI review
    // 2.1, finding 02: AppLayout moves `.column > aside`)
    <aside className={styles.wrap} aria-label={texts.stripRegion}>
      <button type="button" className={styles.strip} onClick={onOpen} aria-label={label}>
        <span className={styles.dot} aria-hidden="true" />
        <span className={styles.text} aria-hidden="true">
          <strong className={styles.lord}>{request.lordName}</strong>
          <span className={styles.need}>{need}</span>
        </span>
        <Countdown until={request.deadline} className={styles.time} />
        {more > 0 && (
          <span className={styles.more} aria-hidden="true">
            {texts.more(more)}
          </span>
        )}
      </button>
    </aside>
  );
}

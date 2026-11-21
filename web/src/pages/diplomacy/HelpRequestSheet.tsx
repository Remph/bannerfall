import { useNavigate } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useDeclineRequest, type HelpRequest } from '../../api/queries/diplomacy';
import { Button } from '../../components/Button';
import { Countdown } from '../../components/Countdown';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import { formatDateTime, formatDuration, formatNumber } from '../../game/format';
import { helpNeedLine, helpVillageLabel } from '../../game/helpRequests';
import { letterText } from '../../game/letters';
import { useGameNow } from '../../game/serverTime';
import type { UnitMap } from '../../game/troops';
import { useI18n } from '../../i18n';
import { useFrozenReason } from '../../layout/frozenReason';
import { useWorldEnded } from '../../layout/worldEnd';
import { marketSendPath } from '../market/marketPaths';
import { rallySupportPath } from '../rally/rallyPaths';
import styles from './HelpRequestSheet.module.css';

/**
 * An ally's help request (2.1 S4, 12-solo-and-allies §9): the deadline first, the biggest thing on
 * the sheet, then the lord's letter, where the player's help would come from and what an answer is
 * worth, then "Not this time" (quiet) and "Send support" / "Send resources" (the main action, which
 * opens the rally point or the market with everything filled in).
 */
export function HelpRequestSheet({ request, onClose }: { request: HelpRequest | null; onClose: () => void }) {
  const { t } = useI18n();
  return (
    <Sheet
      open={request !== null}
      onClose={onClose}
      title={request === null ? '' : t.diplomacy.help.title(request.lordName)}
    >
      {request !== null && <HelpRequestBody request={request} onDone={onClose} />}
    </Sheet>
  );
}

function HelpRequestBody({ request, onDone }: { request: HelpRequest; onDone: () => void }) {
  const { t, language } = useI18n();
  const texts = t.diplomacy.help;
  const goods = (['wood', 'clay', 'iron'] as const)
    .filter((type) => request.resources[type] > 0)
    .map((type) => `${t.resources[type]} ${formatNumber(request.resources[type], language)}`)
    .join(', ');
  return (
    <div className={styles.body}>
      <div className={styles.deadline}>
        <span className={styles.deadlineLabel}>{request.kind === 'SUPPORT' ? texts.landsIn : texts.endsIn}</span>
        <Countdown until={request.deadline} className={styles.countdown} />
        <span className={styles.deadlineAt}>{texts.at(formatDateTime(Date.parse(request.deadline), language))}</span>
      </div>
      <p className={styles.need}>
        {helpNeedLine(t, request, language)}
        {request.kind === 'RESOURCES' && goods !== '' && (
          <span className={styles.still}> {texts.stillNeeded(goods)}</span>
        )}
      </p>
      {request.letter !== null && (
        <section className={styles.letter} aria-label={texts.letterHeading}>
          <p>{letterText(t, request.letter, language, { times: false })}</p>
          <span className={styles.signed}>{request.lordName}</span>
        </section>
      )}
      <HelpRequestActions request={request} onDone={onDone} />
    </div>
  );
}

/**
 * Where the player's help would come from, what an answer is worth, and the two buttons; also under
 * the lord's letter in Messages while its request is open. A march that would land too late keeps
 * "Send support" as the quiet button; with no village that can send there is one line and no send
 * button; in a still world the buttons are off with the reason, after the end only the reason shows.
 */
export function HelpRequestActions({ request, onDone }: { request: HelpRequest; onDone?: () => void }) {
  const { t } = useI18n();
  const texts = t.diplomacy.help;
  const navigate = useNavigate();
  const decline = useDeclineRequest();
  const now = useGameNow();
  // Messages stay open after the end and while paused, but the world stands still: no answer then
  // (UI review 2.1, finding 04)
  const frozen = useFrozenReason();
  const ended = useWorldEnded();
  const from = request.from;
  const fill: UnitMap = request.fill;
  // the server picks the nearest village that can help (defenders at home, a free merchant), or none
  const late = request.seconds !== null && now + request.seconds * 1000 > Date.parse(request.deadline);

  if (ended) {
    return <p className={styles.where}>{frozen}</p>;
  }

  let where: string;
  if (from === null) {
    where = request.kind === 'SUPPORT' ? texts.noDefenders : texts.noMerchants;
  } else if (request.seconds === null) {
    where = '';
  } else {
    const duration = formatDuration(request.seconds);
    where = (request.kind === 'SUPPORT' ? texts.arrivesSupport : texts.arrivesResources)(
      helpVillageLabel(from),
      duration,
    );
  }

  function send() {
    if (from === null) {
      return;
    }
    const target = { x: request.village.x, y: request.village.y };
    onDone?.();
    void navigate(
      request.kind === 'SUPPORT'
        ? rallySupportPath(from.id, target, fill)
        : marketSendPath(from.id, target, request.resources),
    );
  }

  return (
    <div className={styles.actionsPart}>
      {where !== '' && (
        <p className={styles.where}>
          {where}
          {late && from !== null && <strong className={styles.late}> {texts.tooLate}</strong>}
        </p>
      )}
      <p className={styles.worth}>{texts.worth(request.answered, request.declined, request.ignored)}</p>
      {frozen !== null && <p className={styles.frozen}>{frozen}</p>}
      {decline.isError && <ErrorMessage>{errorMessage(t, decline.error)}</ErrorMessage>}
      <div className={styles.actions}>
        <Button
          variant="secondary"
          onClick={() => decline.mutate(request.id, { onSuccess: () => onDone?.() })}
          disabled={decline.isPending || frozen !== null}
        >
          {texts.decline}
        </Button>
        {from !== null && (
          // too late: sending is still allowed, but it is no longer the main action
          <Button variant={late ? 'secondary' : 'primary'} onClick={send} disabled={frozen !== null}>
            {request.kind === 'SUPPORT' ? texts.sendSupport : texts.sendResources}
          </Button>
        )}
      </div>
    </div>
  );
}
